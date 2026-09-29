package com.careerpilot.localtest;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.ArrayList;

/** Deterministic context fixture for local HTTP acceptance, never real LLM reasoning. */
@Component
@Profile("local-test-embedding")
public final class LocalTestChatModel implements ChatModel {
    @Value("${app.local-test.stream-delay-ms:100}") private long streamDelayMs = 100;
    public LocalTestChatModel(LocalTestEmbeddingModel embeddingModel) {
        LoggerFactory.getLogger(getClass()).warn("LOCAL TEST ONLY: local keyword embedding and retrieval echo responder enabled; no OpenAI requests");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = prompt.getUserMessage().getText();
        String startMarker = "以下是个人知识库检索结果（可能为空）：";
        String endMarker = "如问题涉及用户本人经历";
        int start = text.indexOf(startMarker);
        int end = start < 0 ? -1 : text.indexOf(endMarker, start + startMarker.length());
        String context = start >= 0 && end > start ? text.substring(start + startMarker.length(), end).trim() : "";
        String memoryFixture = "";
        if (text.contains("streaming memory")) {
            Pattern value = Pattern.compile("My(?: final)? streaming memory value is\\s+([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
            for (var message : prompt.getUserMessages()) {
                var matcher = value.matcher(message.getText());
                if (matcher.find()) memoryFixture = "Memory fixture value: " + matcher.group(1) + "\n";
            }
        }
        // Only explicit fixture facts in USER history, never assistant text or system instructions.
        Pattern city = Pattern.compile("(?:My(?: persistent memory| test)? city is|MY_CITY_[AB]\\s*=)\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
        if (text.toLowerCase(java.util.Locale.ROOT).contains("city")) {
            for (var message : prompt.getInstructions()) {
                if (!(message instanceof UserMessage)) continue;
                var matcher = city.matcher(message.getText());
                if (matcher.find()) memoryFixture = "Memory fixture city: " + matcher.group(1) + "\n";
            }
        }
        else if (prompt.getUserMessages().stream().anyMatch(message -> message.getText()
                .contains("Remember that I am discussing my Java backend direction."))) {
            memoryFixture = "Memory fixture direction: Java backend\n";
        }
        String answer = "[LOCAL TEST — retrieval echo, not an AI answer]\n"
                + memoryFixture
                + (context.isEmpty() ? "当前知识库没有找到相关资料。" : context);
        return response(new AssistantMessage(answer));
    }

    @Override public ToolCallingChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }

    @Override public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            String question = prompt.getUserMessage().getText();
            if (question.contains("LOCAL_TEST_TOOL") && prompt.getInstructions().stream().noneMatch(ToolResponseMessage.class::isInstance)) {
                return Flux.just(response(AssistantMessage.builder().toolCalls(List.of(
                        new AssistantMessage.ToolCall("local-resume-call", "function", "getResume", "{}"))).build()));
            }
            String answer = question.contains("LOCAL_TEST_TOOL") ? "[LOCAL TEST] getResume executed for the authenticated user."
                    : call(prompt).getResult().getOutput().getText();
            List<ChatResponse> chunks = new ArrayList<>();
            for (int i = 0; i < answer.length();) {
                int end = answer.offsetByCodePoints(i, Math.min(8, answer.codePointCount(i, answer.length())));
                chunks.add(response(new AssistantMessage(answer.substring(i, end)))); i = end;
            }
            Flux<ChatResponse> output = Flux.fromIterable(chunks).delayElements(Duration.ofMillis(Math.max(1, Math.min(streamDelayMs, 1000))));
            if (question.contains("LOCAL_TEST_FAIL_STREAM"))
                return output.take(2).concatWith(Flux.error(new IllegalStateException("LOCAL_TEST stream failure fixture")));
            return output;
        });
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)),
                ChatResponseMetadata.builder().model("local-test-retrieval-echo")
                        // Spring AI streaming aggregation accepts absent Usage, not null fields inside Usage.
                        .keyValue("provider", "LOCAL_TEST").usage(null).build());
    }
}
