package de.prime_ux.goodnews.aisettings;

import de.prime_ux.goodnews.tenants.Tenant;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Chat clients that never reach anybody: every client answers what the test prepared, or fails
 * the way a rejected key would. The last prompt is kept so a test can read what the model was
 * handed.
 */
public final class StubChatClients implements ChatClients {

	private final Supplier<ChatResponse> answer;
	private Prompt lastPrompt;

	private StubChatClients(Supplier<ChatResponse> answer) {
		this.answer = answer;
	}

	/** Answers this text. */
	public static StubChatClients answering(String text) {
		return answering(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
	}

	public static StubChatClients answering(ChatResponse response) {
		return new StubChatClients(() -> response);
	}

	/** Every call fails with this message, the way the provider's client fails on a bad key. */
	public static StubChatClients failing(String message) {
		return new StubChatClients(() -> {
			throw new IllegalStateException(message);
		});
	}

	public Prompt lastPrompt() {
		return lastPrompt;
	}

	@Override
	public ChatClient forTenant(Tenant tenant) {
		return ChatClient.create(new StubChatModel());
	}

	@Override
	public ChatClient withApiKey(String apiKey) {
		return ChatClient.create(new StubChatModel());
	}

	private final class StubChatModel implements ChatModel {

		@Override
		public ChatResponse call(Prompt prompt) {
			lastPrompt = prompt;
			return answer.get();
		}

		@Override
		public ChatOptions getOptions() {
			return ChatOptions.builder().build();
		}
	}
}
