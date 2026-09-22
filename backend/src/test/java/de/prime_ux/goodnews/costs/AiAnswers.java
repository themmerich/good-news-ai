package de.prime_ux.goodnews.costs;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** Answers shaped the way a provider's would be, for the tests that only care what is on them. */
public final class AiAnswers {

	private AiAnswers() {
	}

	/** An answer that reports its model and what it used, as a real one does. */
	public static ChatResponse withUsage(String model, int inputTokens, int outputTokens) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))),
				ChatResponseMetadata.builder()
						.model(model)
						.usage(new DefaultUsage(inputTokens, outputTokens))
						.build());
	}

	/** An answer that reports nothing — what a stood-in model hands back. */
	public static ChatResponse withoutAccounting() {
		return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
	}
}
