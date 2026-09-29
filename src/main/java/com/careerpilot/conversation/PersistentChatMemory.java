package com.careerpilot.conversation;

import com.careerpilot.security.CurrentUser;
import com.careerpilot.observability.ObservationScope;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;

/** Uses the same append-only records as the history API; only reads are windowed. */
public final class PersistentChatMemory implements ChatMemory {
    private final ConversationRepository repository;
    private final int maxMessages;
    public PersistentChatMemory(ConversationRepository repository, int maxMessages) {
        if (maxMessages < 2 || maxMessages > 100) throw new IllegalArgumentException("CHAT_MEMORY_MAX_MESSAGES须介于2和100");
        this.repository = repository;
        this.maxMessages = maxMessages;
    }

    @Override public void add(String key, List<Message> messages) {
        Owner owner = owner(key);
        var stream = com.careerpilot.stream.StreamEvents.current();
        if (stream != null) {
            stream.deferAssistant(messages);
            // Streaming lifecycle already committed USER. Defer ASSISTANT until atomic successful finalization.
            return;
        }
        List<ConversationRepository.NewMessage> visible = messages.stream()
                .filter(message -> message instanceof UserMessage || (message instanceof AssistantMessage assistant && !assistant.hasToolCalls()))
                .filter(message -> message.getText() != null && !message.getText().isBlank())
                .map(message -> new ConversationRepository.NewMessage(message instanceof UserMessage ? "USER" : "ASSISTANT", message.getText()))
                .toList();
        repository.append(owner.userId(), owner.id(), visible, MDC.get("requestId"));
    }

    @Override public List<Message> get(String key) {
        Owner owner = owner(key);
        long started = System.nanoTime();
        var history = com.careerpilot.stream.StreamEvents.current() == null
                ? repository.window(owner.userId(), owner.id(), maxMessages)
                : repository.windowBeforeRequest(owner.userId(), owner.id(), maxMessages, MDC.get("requestId"));
        List<Message> messages = history.stream()
                .<Message>map(row -> "USER".equals(row.role()) ? new UserMessage(row.content()) : new AssistantMessage(row.content())).toList();
        var trace = ObservationScope.current();
        if (trace != null && trace.events().stream().noneMatch(event -> "MEMORY".equals(event.getEventType())))
            trace.event("MEMORY", "MEMORY_CONTEXT", "SUCCESS", ObservationScope.elapsed(started), null, null, messages.size());
        com.careerpilot.stream.StreamEvents.emit("memory.loaded", java.util.Map.of("loadedMessageCount", messages.size(), "durationMs", ObservationScope.elapsed(started)));
        return messages;
    }

    @Override public void clear(String key) {
        Owner owner = owner(key);
        repository.delete(owner.userId(), owner.id());
    }

    private static Owner owner(String key) {
        long userId = CurrentUser.id();
        String prefix = "u:" + userId + ":c:";
        if (key == null || !key.startsWith(prefix) || key.length() <= prefix.length())
            throw new IllegalArgumentException("Invalid authenticated memory key");
        return new Owner(userId, key.substring(prefix.length()));
    }
    private record Owner(long userId, String id) { }
}
