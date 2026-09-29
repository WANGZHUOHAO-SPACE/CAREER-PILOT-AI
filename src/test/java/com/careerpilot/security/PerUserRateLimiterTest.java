package com.careerpilot.security;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerUserRateLimiterTest {
    @Test void budgetsAreUserScopedAndUploadIsSeparate() {
        AtomicLong clock = new AtomicLong();
        var limiter = new PerUserRateLimiter(2, 1, clock::get);
        assertTrue(limiter.acquire(1, false).allowed());
        assertTrue(limiter.acquire(1, false).allowed());
        var denied = limiter.acquire(1, false);
        assertFalse(denied.allowed());
        assertEquals(30, denied.retryAfterSeconds());
        assertTrue(limiter.acquire(1, true).allowed());
        assertFalse(limiter.acquire(1, true).allowed());
        assertTrue(limiter.acquire(2, false).allowed());
        clock.addAndGet(30_000_000_000L);
        assertTrue(limiter.acquire(1, false).allowed());
        assertFalse(limiter.acquire(1, false).allowed());
    }
    @Test void idleEntriesExpireAndInvalidConfigurationFails() {
        AtomicLong clock = new AtomicLong();
        var limiter = new PerUserRateLimiter(1, 1, clock::get);
        for (int i = 1; i <= 10_000; i++) assertTrue(limiter.acquire(i, false).allowed());
        assertFalse(limiter.acquire(10_001, false).allowed());
        clock.addAndGet(600_000_000_000L);
        assertTrue(limiter.acquire(10_001, false).allowed());
        assertThrows(IllegalArgumentException.class, () -> new PerUserRateLimiter(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PerUserRateLimiter(1, 101));
    }
}
