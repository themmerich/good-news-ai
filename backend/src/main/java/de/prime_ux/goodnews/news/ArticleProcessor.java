package de.prime_ux.goodnews.news;

import com.anthropic.models.messages.OutputConfig;
import de.prime_ux.goodnews.aisettings.ChatClients;
import de.prime_ux.goodnews.catalog.Category;
import de.prime_ux.goodnews.catalog.CategoryRepository;
import de.prime_ux.goodnews.costs.AiCalls;
import de.prime_ux.goodnews.costs.Purpose;
import de.prime_ux.goodnews.tenants.Tenant;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Hands a bundle of stories to the AI and writes back what it made of them: a positive gist, a
 * category and a ranking.
 *
 * <p>Ten at a time. One call per story would multiply the overhead of the instructions by the
 * number of stories; the whole run in one call would make a single bad answer cost everything.
 *
 * <p>The instructions live in a file under {@code resources} rather than in a Java string, so
 * they can be changed without reading any code — and they are the part most likely to be changed.
 */
@Component
public class ArticleProcessor {

	private static final Logger log = LoggerFactory.getLogger(ArticleProcessor.class);

	/** How many stories go into one call. */
	public static final int BATCH_SIZE = 10;

	/**
	 * Room for the answer, set for this call rather than globally: the application default of
	 * 1024 serves the key test, which answers with one word, and is barely enough for ten gists
	 * with their JSON around them. An answer cut off mid-word cannot be read at all, and the
	 * whole bundle is lost — while a ceiling that is never reached costs nothing, because output
	 * tokens are billed as produced.
	 */
	private static final int MAX_TOKENS = 16000;

	/**
	 * Turns the answer into a record, and tells the model what shape to answer in. Held here
	 * rather than left to {@code .entity(...)} so the raw answer can be looked at before it is
	 * parsed: an answer with no text in it is a different problem from an answer that is malformed,
	 * and a parser error names neither.
	 */
	private static final BeanOutputConverter<Ratings> CONVERTER = new BeanOutputConverter<>(Ratings.class);

	private final ChatClients chatClients;
	private final CategoryRepository categoryRepository;
	private final ArticleRepository articleRepository;
	private final AiCalls aiCalls;
	private final OutputConfig.Effort effort;
	private final String instructions;

	ArticleProcessor(ChatClients chatClients, CategoryRepository categoryRepository,
			ArticleRepository articleRepository, AiCalls aiCalls,
			@Value("${goodnews.ai.effort:low}") String effort,
			@Value("classpath:prompts/rate-articles.md") Resource instructions) {
		this.chatClients = chatClients;
		this.categoryRepository = categoryRepository;
		this.articleRepository = articleRepository;
		this.aiCalls = aiCalls;
		this.effort = effortOf(effort);
		this.instructions = read(instructions);
	}

	/**
	 * Rates one bundle and stores the result.
	 *
	 * @return how many stories came back rated, and what went wrong where none did. The stories
	 *         of a bundle that failed stay unprocessed and go along next run.
	 */
	public Outcome process(Tenant tenant, List<Article> batch) {
		if (batch.isEmpty()) {
			return new Outcome(0, null);
		}
		Map<String, Category> byName = categoriesByName(tenant);
		ChatResponse response;
		try {
			response = this.chatClients.forTenant(tenant)
					.prompt()
					.options(callOptions())
					.user(prompt(batch, byName.values()))
					.call()
					.chatResponse();
		} catch (RuntimeException e) {
			// One bundle that did not come back is no reason to abandon the rest: the stories stay
			// unrated, show as such, and go along next time.
			log.warn("a bundle of {} stories could not be rated", batch.size(), e);
			return new Outcome(0, describe(e));
		}
		// Booked before the answer is read: a bundle that came back unusable was paid for all the
		// same, and a page that left those out would not agree with the invoice.
		this.aiCalls.record(tenant, Purpose.RATING, response);
		String text = textOf(response);
		if (text.isBlank()) {
			String why = "the model answered with no text at all (" + accountOf(response) + ")";
			log.warn("{} for a bundle of {} stories", why, batch.size());
			return new Outcome(0, why);
		}
		Ratings answer;
		try {
			answer = CONVERTER.convert(text);
		} catch (RuntimeException e) {
			log.warn("the answer for {} stories could not be read: {}", batch.size(), abbreviate(text), e);
			return new Outcome(0, "the answer could not be read: " + describe(e));
		}
		return new Outcome(store(batch, byName, answer), null);
	}

	/**
	 * How hard the model should think about a bundle, and how much room its answer gets.
	 *
	 * <p>Effort is the lever that decides what a run costs. Reasoning is billed as output, output
	 * costs five times what input costs, and a measured run spent roughly two thirds of its output
	 * tokens on reasoning that never reached the page. Reading a teaser, writing one sentence about
	 * it and picking from a list of names does not need much of it — hence the default of
	 * {@code low}, and hence the knob, so the trade can be measured rather than argued.
	 */
	private AnthropicChatOptions.Builder callOptions() {
		AnthropicChatOptions.Builder options = AnthropicChatOptions.builder().maxTokens(MAX_TOKENS);
		if (this.effort != null) {
			options.effort(this.effort);
		}
		return options;
	}

	/**
	 * The configured effort, or null to leave the provider its own default.
	 *
	 * <p>Matched against the levels by hand rather than passed on as written: the SDK takes any
	 * string here and a typo would travel all the way to the provider, where it fails per call
	 * rather than at startup.
	 */
	private static OutputConfig.Effort effortOf(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		return switch (name.strip().toLowerCase(Locale.ROOT)) {
			case "low" -> OutputConfig.Effort.LOW;
			case "medium" -> OutputConfig.Effort.MEDIUM;
			case "high" -> OutputConfig.Effort.HIGH;
			case "xhigh" -> OutputConfig.Effort.XHIGH;
			case "max" -> OutputConfig.Effort.MAX;
			default -> throw new IllegalArgumentException(
					"goodnews.ai.effort is " + name + "; it must be low, medium, high, xhigh, max, or empty");
		};
	}

	/**
	 * Writes the answer onto the stories it belongs to.
	 *
	 * <p>Matched by the number the prompt gave each story, not by position: a model that returns
	 * nine answers for ten stories would otherwise shift every rating after the gap onto the wrong
	 * story, which is worse than leaving them unrated.
	 */
	private int store(List<Article> batch, Map<String, Category> byName, Ratings answer) {
		if (answer == null || answer.ratings() == null) {
			log.warn("the model answered with nothing usable for {} stories", batch.size());
			return 0;
		}
		int rated = 0;
		for (Rating rating : answer.ratings()) {
			int index = rating.number() - 1;
			if (index < 0 || index >= batch.size()) {
				log.warn("the model answered for story {}, which was not in the bundle", rating.number());
				continue;
			}
			Article article = batch.get(index);
			article.rate(categoryOf(rating, byName), rating.summary(), clamp(rating.ranking()));
			this.articleRepository.save(article);
			rated++;
		}
		return rated;
	}

	/**
	 * The category the model named, or none. Matched on the name without regard to case, because
	 * a model writes "sport" as readily as "Sport"; a name that matches nothing leaves the story
	 * unplaced, which the board shows under "Sonstiges".
	 */
	private static Category categoryOf(Rating rating, Map<String, Category> byName) {
		String name = rating.category();
		if (name == null || name.isBlank()) {
			return null;
		}
		Category category = byName.get(name.strip().toLowerCase(Locale.ROOT));
		if (category == null) {
			log.warn("the model named the category {}, which this tenant does not have", name);
		}
		return category;
	}

	/**
	 * A ranking outside the scale is the model miscounting rather than an opinion worth keeping
	 * as given, and the column will not take it either.
	 */
	private static Integer clamp(Integer ranking) {
		if (ranking == null) {
			return null;
		}
		return Math.max(0, Math.min(10, ranking));
	}

	private Map<String, Category> categoriesByName(Tenant tenant) {
		Map<String, Category> byName = new HashMap<>();
		for (Category category : this.categoryRepository
				.findAllByTenantIdOrderBySortOrderAscNameAsc(tenant.getId())) {
			byName.put(category.getName().toLowerCase(Locale.ROOT), category);
		}
		return byName;
	}

	/**
	 * Filled in by hand rather than through a template engine. The titles and teasers come from
	 * the open internet and carry whatever punctuation they like; a renderer that treats braces or
	 * angle brackets as syntax would fail on the first story about a piece of code.
	 */
	private String prompt(List<Article> batch, Iterable<Category> categories) {
		StringBuilder names = new StringBuilder();
		for (Category category : categories) {
			names.append("- ").append(category.getName()).append('\n');
		}
		if (names.isEmpty()) {
			names.append("(keine — lass die Kategorie bei jeder Meldung leer)\n");
		}
		StringBuilder stories = new StringBuilder();
		for (int index = 0; index < batch.size(); index++) {
			Article article = batch.get(index);
			stories.append("### Meldung ").append(index + 1).append('\n')
					.append("Quelle: ").append(article.getFeed().getName()).append('\n')
					.append("Titel: ").append(article.getTitle()).append('\n')
					.append("Teaser: ").append(article.getTeaser().isBlank() ? "(keiner)" : article.getTeaser())
					.append("\n\n");
		}
		// The shape of the answer goes on the end, where the model reads it last.
		return this.instructions
				.replace("{{categories}}", names.toString())
				.replace("{{articles}}", stories.toString())
				+ System.lineSeparator() + System.lineSeparator() + CONVERTER.getFormat();
	}

	private static String read(Resource resource) {
		try (var in = resource.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("the rating instructions could not be read", e);
		}
	}

	/**
	 * What comes back per story.
	 *
	 * @param number the number the prompt gave the story, counted from one
	 * @param category a name from the list, or empty where nothing fitted
	 */
	record Rating(int number, String summary, String category, Integer ranking) {
	}

	record Ratings(List<Rating> ratings) {
	}

	/**
	 * @param failure what went wrong, in words the run can show; null where nothing did
	 */
	public record Outcome(int rated, String failure) {
	}

	/**
	 * The text of every part of the answer, not just the first.
	 *
	 * <p>An answer arrives in blocks, and the first is not necessarily the one with the words in
	 * it: models that reason before they speak put their reasoning first, and that block carries
	 * no text. Reading only the first block therefore found an empty answer while the model had
	 * plainly said something — 1415 tokens of it, by its own accounting.
	 */
	private static String textOf(ChatResponse response) {
		if (response == null || response.getResults() == null) {
			return "";
		}
		return response.getResults().stream()
				.map(generation -> generation.getOutput() == null ? null : generation.getOutput().getText())
				.filter(text -> text != null && !text.isBlank())
				.collect(Collectors.joining(System.lineSeparator()))
				.strip();
	}

	/**
	 * Why an empty answer was empty, in the terms the provider reports it: how it stopped and what
	 * it spent. A model that stopped because it ran out of room says so here, which is a different
	 * fix from a model that simply had nothing to say.
	 */
	private static String accountOf(ChatResponse response) {
		if (response == null || response.getResult() == null) {
			return "no result at all";
		}
		String finishReason = response.getResult().getMetadata() == null ? "unknown"
				: String.valueOf(response.getResult().getMetadata().getFinishReason());
		String usage = response.getMetadata() == null || response.getMetadata().getUsage() == null ? "unknown"
				: response.getMetadata().getUsage().toString();
		int parts = response.getResults() == null ? 0 : response.getResults().size();
		return "stopped: " + finishReason + ", parts: " + parts + ", usage: " + usage;
	}

	private static String abbreviate(String text) {
		return text.length() <= 400 ? text : text.substring(0, 400) + " …";
	}

	/** The shortest honest account of a failure, for a message somebody has to act on. */
	private static String describe(RuntimeException e) {
		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}
}
