package de.prime_ux.goodnews.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.aisettings.ChatClients;
import de.prime_ux.goodnews.aisettings.StubChatClients;
import de.prime_ux.goodnews.catalog.Category;
import de.prime_ux.goodnews.catalog.CategoryRepository;
import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.catalog.FeedRepository;
import de.prime_ux.goodnews.catalog.SourceType;
import de.prime_ux.goodnews.costs.Purpose;
import de.prime_ux.goodnews.costs.StubAiCalls;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

/**
 * What the AI makes of a bundle of stories, and what happens when it does not oblige. The model
 * is stood in for: a test that called Anthropic would be a test of Anthropic, and would cost
 * money on every run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ArticleProcessorTest {

	/** What the stood-in model answered last, for the tests that read the prompt it was handed. */
	private StubChatClients chat;

	/** The bookings the processor made, for the tests that ask whether the call was paid for. */
	private StubAiCalls aiCalls;

	@Autowired
	private ArticleRepository articleRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private FeedRepository feedRepository;

	@Autowired
	private TenantRepository tenantRepository;

	private Tenant tenant;
	private Feed kicker;

	@BeforeEach
	void cleanDatabaseAndCreateACatalog() {
		this.articleRepository.deleteAll();
		this.feedRepository.deleteAll();
		this.categoryRepository.deleteAll();
		this.tenantRepository.deleteAll();
		this.tenant = this.tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		this.kicker = this.feedRepository
				.save(new Feed(this.tenant, "kicker", "https://kicker.example/rss", SourceType.FEED));
		this.categoryRepository.save(new Category(this.tenant, "Sport", 0));
		this.categoryRepository.save(new Category(this.tenant, "NFL", 1));
		this.categoryRepository.save(new Category(this.tenant, "Miami Dolphins", 2));
	}

	@Test
	void writesBackTheGistTheCategoryAndTheRanking() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Der Haushalt steht.", "category": "Sport", "ranking": 7}]}""");
		Article article = article("Haushalt beschlossen");

		int rated = processor.process(this.tenant, List.of(article)).rated();

		assertThat(rated).isOne();
		Article stored = reloaded(article);
		assertThat(stored.getPositiveSummary()).isEqualTo("Der Haushalt steht.");
		assertThat(stored.getCategory().getName()).isEqualTo("Sport");
		assertThat(stored.getRanking()).isEqualTo(7);
		assertThat(stored.isUnprocessed()).isFalse();
	}

	@Test
	void matchesTheCategoryWhateverCaseTheModelWroteItIn() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Kurz.", "category": "  mIaMi dOlPhInS  ", "ranking": 5}]}""");
		Article article = article("Dolphins gewinnen");

		processor.process(this.tenant, List.of(article));

		assertThat(reloaded(article).getCategory().getName()).isEqualTo("Miami Dolphins");
	}

	/**
	 * The narrowest category that fits is the rule the instructions state, and it is what makes
	 * "Miami Dolphins, otherwise NFL, otherwise Sport" work without a hierarchy in the schema.
	 * What is checked here is that all three names reach the model; which one it picks is its
	 * business, and stubbing an answer could not show otherwise.
	 */
	@Test
	void showsTheModelEveryCategoryItMayChooseFrom() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Kurz.", "category": "NFL", "ranking": 5}]}""");

		processor.process(this.tenant, List.of(article("Patriots gewinnen")));

		String prompt = this.chat.lastPrompt().getContents();
		assertThat(prompt).contains("- Sport", "- NFL", "- Miami Dolphins");
		assertThat(prompt).contains("engste");
	}

	@Test
	void leavesTheStoryUnplacedWhenTheModelNamesACategoryTheTenantDoesNotHave() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Kurz.", "category": "Handball", "ranking": 4}]}""");
		Article article = article("Irgendetwas");

		processor.process(this.tenant, List.of(article));

		Article stored = this.articleRepository.findById(article.getId()).orElseThrow();
		assertThat(stored.getCategory()).isNull();
		// Rated all the same, so the next run does not pay for it again.
		assertThat(stored.isUnprocessed()).isFalse();
		assertThat(stored.getRanking()).isEqualTo(4);
	}

	@Test
	void takesAnEmptyCategoryAsARealAnswer() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Kurz.", "category": "", "ranking": 2}]}""");
		Article article = article("Passt nirgends");

		processor.process(this.tenant, List.of(article));

		Article stored = this.articleRepository.findById(article.getId()).orElseThrow();
		assertThat(stored.getCategory()).isNull();
		assertThat(stored.isUnprocessed()).isFalse();
	}

	/**
	 * Matched by number rather than by position. A model that skips one would otherwise shift
	 * every rating after the gap onto the wrong story, which is worse than leaving them unrated.
	 */
	@Test
	void leavesTheStoriesTheModelSkippedUnrated() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 3, "summary": "Zur dritten.", "category": "Sport", "ranking": 6}]}""");
		Article first = article("Erste");
		Article second = article("Zweite");
		Article third = article("Dritte");

		int rated = processor.process(this.tenant, List.of(first, second, third)).rated();

		assertThat(rated).isOne();
		assertThat(this.articleRepository.findById(third.getId()).orElseThrow().getPositiveSummary())
				.isEqualTo("Zur dritten.");
		assertThat(this.articleRepository.findById(first.getId()).orElseThrow().isUnprocessed()).isTrue();
		assertThat(this.articleRepository.findById(second.getId()).orElseThrow().isUnprocessed()).isTrue();
	}

	@Test
	void ignoresAnAnswerForAStoryThatWasNotInTheBundle() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 9, "summary": "Zu niemandem.", "category": "Sport", "ranking": 6}]}""");
		Article article = article("Einzige");

		int rated = processor.process(this.tenant, List.of(article)).rated();

		assertThat(rated).isZero();
		assertThat(this.articleRepository.findById(article.getId()).orElseThrow().isUnprocessed()).isTrue();
	}

	@Test
	void pullsARankingOutsideTheScaleBackOntoIt() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Kurz.", "category": "Sport", "ranking": 42}]}""");
		Article article = article("Übertrieben");

		processor.process(this.tenant, List.of(article));

		assertThat(this.articleRepository.findById(article.getId()).orElseThrow().getRanking()).isEqualTo(10);
	}

	/**
	 * What went wrong travels back with the outcome, so the run can say it rather than guess at a
	 * cause. A message naming the key when the key is fine sends somebody looking in the wrong
	 * place — which is exactly what happened the first time this was written.
	 */
	@Test
	void leavesTheBundleUnratedAndSaysWhatWentWrongWhenTheCallFails() {
		ArticleProcessor processor = processorUsing(StubChatClients.failing("invalid x-api-key"));
		Article article = article("Ohne Zugang");

		ArticleProcessor.Outcome outcome = processor.process(this.tenant, List.of(article));

		assertThat(outcome.failure()).contains("invalid x-api-key");
		int rated = outcome.rated();
		assertThat(rated).isZero();
		assertThat(this.articleRepository.findById(article.getId()).orElseThrow().isUnprocessed()).isTrue();
	}

	@Test
	void tellsTheModelThereIsNothingToChooseFromWhenTheTenantHasNoCategories() {
		this.categoryRepository.deleteAll();
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Kurz.", "category": "", "ranking": 5}]}""");

		processor.process(this.tenant, List.of(article("Ohne Kategorien")));

		assertThat(this.chat.lastPrompt().getContents()).contains("keine");
	}

	@Test
	void handsTheModelTheSourceTheTitleAndTheTeaser() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Kurz.", "category": "Sport", "ranking": 5}]}""");

		processor.process(this.tenant, List.of(article("Ein Titel")));

		String prompt = this.chat.lastPrompt().getContents();
		assertThat(prompt).contains("kicker", "Ein Titel", "Ein Teaser.");
	}

	/**
	 * One booking per bundle, not per story: ten stories go to the model in one call and are
	 * billed as one. A cost per story would be a division nobody was ever charged for.
	 */
	@Test
	void booksOneCostForTheWholeBundle() {
		ArticleProcessor processor = answering("""
				{"ratings": [{"number": 1, "summary": "Eins.", "category": "Sport", "ranking": 5},
				{"number": 2, "summary": "Zwei.", "category": "Sport", "ranking": 5},
				{"number": 3, "summary": "Drei.", "category": "Sport", "ranking": 5}]}""");

		processor.process(this.tenant, List.of(article("Erste"), article("Zweite"), article("Dritte")));

		assertThat(this.aiCalls.bookings()).singleElement()
				.satisfies(booking -> {
					assertThat(booking.purpose()).isEqualTo(Purpose.RATING);
					assertThat(booking.tenant().getId()).isEqualTo(this.tenant.getId());
				});
	}

	/**
	 * An answer that could not be read was paid for like any other. Booking only the successful
	 * calls would leave the page below the invoice with nothing to show for the difference.
	 */
	@Test
	void booksTheCostOfAnAnswerItCouldNotRead() {
		ArticleProcessor processor = answering("kein JSON, nur Text");

		processor.process(this.tenant, List.of(article("Unlesbar")));

		assertThat(this.aiCalls.bookings()).hasSize(1);
	}

	/** A call that never reached the provider was never billed either. */
	@Test
	void booksNothingWhenTheCallFails() {
		ArticleProcessor processor = processorUsing(StubChatClients.failing("invalid x-api-key"));

		processor.process(this.tenant, List.of(article("Ohne Zugang")));

		assertThat(this.aiCalls.bookings()).isEmpty();
	}

	/**
	 * The effort is a string from the properties, and the SDK would take any string here: a typo
	 * would reach the provider and fail once per call rather than once at startup.
	 */
	@Test
	void refusesAnEffortLevelThatDoesNotExist() {
		assertThatThrownBy(() -> processorWithEffort("gründlich"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("goodnews.ai.effort");
	}

	@Test
	void takesEveryLevelTheProviderKnows() {
		for (String level : List.of("low", "medium", "high", "xhigh", "max", "  HIGH  ")) {
			assertThatCode(() -> processorWithEffort(level)).doesNotThrowAnyException();
		}
	}

	/** Empty leaves the provider its own default, which is how one asks for no opinion. */
	@Test
	void leavesTheEffortAloneWhenTheSettingIsEmpty() {
		assertThatCode(() -> processorWithEffort("")).doesNotThrowAnyException();
	}

	private ArticleProcessor processorWithEffort(String effort) {
		return new ArticleProcessor(StubChatClients.answering("{}"), this.categoryRepository,
				this.articleRepository, new StubAiCalls(), effort,
				new ClassPathResource("prompts/rate-articles.md"));
	}

	/**
	 * A processor whose model answers this. Built by hand rather than taken from the context: the
	 * answer differs per test, and a bean is made once when the context starts.
	 */
	private ArticleProcessor answering(String json) {
		return processorUsing(StubChatClients.answering(json));
	}

	private ArticleProcessor processorUsing(StubChatClients chatClients) {
		this.chat = chatClients;
		this.aiCalls = new StubAiCalls();
		return new ArticleProcessor(chatClients, this.categoryRepository, this.articleRepository, this.aiCalls, "low",
				new ClassPathResource("prompts/rate-articles.md"));
	}

	/**
	 * Read back the way the board reads it, with the category fetched along. A plain findById
	 * hands out a proxy that has no session left to initialize itself from.
	 */
	private Article reloaded(Article article) {
		return this.articleRepository.findAllOfTenant(this.tenant.getId()).stream()
				.filter(candidate -> candidate.getId().equals(article.getId()))
				.findFirst()
				.orElseThrow();
	}

	private Article article(String title) {
		return this.articleRepository.save(new Article(this.kicker, title, title,
				"https://kicker.example/" + title, Instant.parse("2026-09-20T10:00:00Z"), "Ein Teaser."));
	}
}
