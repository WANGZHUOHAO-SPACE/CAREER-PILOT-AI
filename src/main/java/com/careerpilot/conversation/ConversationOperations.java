package com.careerpilot.conversation;

import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Single-backend overlap protection; database row locks separately protect message sequences. */
@Component
public class ConversationOperations {
    private final Set<String> active = new HashSet<>();

    public synchronized Lease acquire(long userId, String id) {
        String key = userId + ":" + id.toLowerCase(java.util.Locale.ROOT);
        if (!active.add(key)) throw new ResponseStatusException(HttpStatus.CONFLICT, "该会话正在处理请求，请稍后重试");
        return new Lease(key);
    }

    public final class Lease implements AutoCloseable {
        private final String key;
        private Lease(String key) { this.key = key; }
        @Override public void close() {
            synchronized (ConversationOperations.this) { active.remove(key); }
        }
    }
}
