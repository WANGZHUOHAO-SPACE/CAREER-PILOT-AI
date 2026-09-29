package com.careerpilot.conversation;

import java.util.UUID;
import com.careerpilot.security.CurrentUser;
import com.careerpilot.conversation.ConversationDtos.*;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    private final ConversationRepository repository;
    private final ConversationOperations operations;
    public ConversationService(ConversationRepository repository, ConversationOperations operations) {
        this.repository = repository;
        this.operations = operations;
    }
    public Conversation create() { return repository.create(CurrentUser.id(), UUID.randomUUID().toString()); }
    public Page<Conversation> list(int page, int size) {
        if (size > 50) throw new IllegalArgumentException("会话列表size不能超过50");
        return repository.list(CurrentUser.id(), page, size);
    }
    public Page<HistoryMessage> history(String id, int page, int size) {
        return repository.history(CurrentUser.id(), id, page, size);
    }
    public ConversationOperations.Lease beginChat(String id) {
        repository.requireOwned(CurrentUser.id(), id);
        return operations.acquire(CurrentUser.id(), id);
    }
    public Conversation rename(String id, String title) {
        repository.requireOwned(CurrentUser.id(), id);
        try (var lease = operations.acquire(CurrentUser.id(), id)) {
            return repository.rename(CurrentUser.id(), id, title.trim());
        }
    }
    public void delete(String id) {
        repository.requireOwned(CurrentUser.id(), id);
        try (var lease = operations.acquire(CurrentUser.id(), id)) { repository.delete(CurrentUser.id(), id); }
    }
    public static String memoryKey(long userId, String id) { return "u:" + userId + ":c:" + id; }
}
