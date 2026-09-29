package com.careerpilot.conversation;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ConversationDtos {
    private ConversationDtos() { }

    public record Conversation(String conversationId, String title, LocalDateTime createdAt,
            LocalDateTime updatedAt, LocalDateTime lastMessageAt, long messageCount) { }
    public record HistoryMessage(long id, String role, String content, long sequenceNo,
            LocalDateTime createdAt, String requestId, String status) { }
    public record Page<T>(int page, int size, boolean hasMore, List<T> items) { }
    public record RenameRequest(@NotBlank @Size(max = 100) String title) { }
}
