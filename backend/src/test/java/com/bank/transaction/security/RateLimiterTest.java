package com.bank.transaction.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);
    private final Duration minute = Duration.ofMinutes(1);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        for (int i = 0; i < 3; i++) {
            assertEquals(0, limiter.tryAcquire("login|1.2.3.4", 3, minute), "request " + (i + 1) + " should pass");
        }
        long retryAfter = limiter.tryAcquire("login|1.2.3.4", 3, minute);
        assertTrue(retryAfter > 0 && retryAfter <= 60, "blocked with a wait of up to one minute, got " + retryAfter);
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("k", 3, minute);
        }
        assertTrue(limiter.tryAcquire("k", 3, minute) > 0);

        clock.advance(Duration.ofSeconds(61));

        assertEquals(0, limiter.tryAcquire("k", 3, minute));
    }

    @Test
    void blockedAttemptsDoNotExtendTheWait() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("k", 3, minute);
        }
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("k", 3, minute);
        }
        clock.advance(Duration.ofSeconds(61));
        assertEquals(0, limiter.tryAcquire("k", 3, minute));
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("login|1.1.1.1", 3, minute);
        }
        assertTrue(limiter.tryAcquire("login|1.1.1.1", 3, minute) > 0);
        assertEquals(0, limiter.tryAcquire("login|2.2.2.2", 3, minute));
    }
}
