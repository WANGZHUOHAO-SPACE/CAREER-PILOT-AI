package com.careerpilot.security;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Single-instance, bounded token buckets. Chat and streaming share one budget. */
@Component
public class PerUserRateLimiter {
    private static final long MINUTE = 60_000_000_000L;
    private static final long IDLE = 10 * MINUTE;
    private static final int MAX_USERS = 10_000;
    private final int chatLimit;
    private final int uploadLimit;
    private final LongSupplier clock;
    private final Map<Long, Buckets> users = new HashMap<>();
    private long lastCleanup;

    @org.springframework.beans.factory.annotation.Autowired
    public PerUserRateLimiter(@Value("${app.rate-limit.chat:20}") int chatLimit,
            @Value("${app.rate-limit.upload:5}") int uploadLimit) {
        this(chatLimit, uploadLimit, System::nanoTime);
    }

    PerUserRateLimiter(int chatLimit, int uploadLimit, LongSupplier clock) {
        if (chatLimit < 1 || chatLimit > 1000 || uploadLimit < 1 || uploadLimit > 100) {
            throw new IllegalArgumentException("Rate limits must be chat=1..1000 and upload=1..100");
        }
        this.chatLimit = chatLimit;
        this.uploadLimit = uploadLimit;
        this.clock = clock;
    }

    public synchronized Decision acquire(long userId, boolean upload) {
        long now = clock.getAsLong();
        if (now - lastCleanup >= MINUTE) {
            users.values().removeIf(b -> now - b.lastAccess >= IDLE);
            lastCleanup = now;
        }
        Buckets buckets = users.get(userId);
        if (buckets == null) {
            // Do not evict active users: that would allow an exhausted budget to reset.
            if (users.size() >= MAX_USERS) return new Decision(false, 60);
            buckets = new Buckets(new Bucket(chatLimit, now), new Bucket(uploadLimit, now), now);
            users.put(userId, buckets);
        }
        buckets.lastAccess = now;
        Bucket bucket = upload ? buckets.upload : buckets.chat;
        int capacity = upload ? uploadLimit : chatLimit;
        bucket.tokens = Math.min(capacity, bucket.tokens + Math.max(0, now - bucket.updated) * (double) capacity / MINUTE);
        bucket.updated = now;
        if (bucket.tokens >= 1) {
            bucket.tokens -= 1;
            return new Decision(true, 0);
        }
        return new Decision(false, Math.max(1, (int) Math.ceil((1 - bucket.tokens) * 60 / capacity)));
    }

    public record Decision(boolean allowed, int retryAfterSeconds) {}
    private static final class Bucket {
        double tokens;
        long updated;
        Bucket(int capacity, long now) { tokens = capacity; updated = now; }
    }
    private static final class Buckets {
        final Bucket chat;
        final Bucket upload;
        long lastAccess;
        Buckets(Bucket chat, Bucket upload, long now) { this.chat = chat; this.upload = upload; lastAccess = now; }
    }
}
