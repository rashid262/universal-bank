package com.bank.transaction.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window request counter: at most {@code limit} events per key within {@code window}.
 *
 * Kept in memory, which suits this single-instance app. Counts reset when the backend restarts
 * and are not shared between several backend instances; a multi-instance deployment would need
 * a shared store such as Redis.
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records one event for {@code key} if it is within the limit.
     *
     * @return 0 when the event is allowed, otherwise the number of seconds until the next one is allowed
     */
    public long tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowStart = now - window.toMillis();
        if (events.size() > CLEANUP_THRESHOLD) {
            removeStale(windowStart);
        }
        Deque<Long> timestamps = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                long oldest = timestamps.peekFirst();
                long waitMs = oldest + window.toMillis() - now;
                return Math.max(1, (waitMs + 999) / 1000);
            }
            timestamps.addLast(now);
            return 0;
        }
    }

    private void removeStale(long windowStart) {
        events.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || last <= windowStart;
            }
        });
    }
}
