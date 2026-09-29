package com.careerpilot.stream;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

public final class LlmStreamAdvisor implements StreamAdvisor {
    @Override public String getName() { return "LlmStreamLifecycle"; }
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE - 10; }
    @Override public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        StreamEvents.emit("llm.started", Map.of());
        return chain.nextStream(request);
    }
}
