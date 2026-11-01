package com.bank.transaction.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks a username after repeated wrong passwords, whatever IP the attempts come from.
 *
 * Counted per username text, so a username that does not exist is locked exactly like a real one
 * and the lock reply reveals nothing. Trade-off: anyone who knows a username can keep it locked by
 * failing on purpose; admins recover by waiting out the lock. In memory, like RateLimiter.
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final class Attempts {
        int failures;
        Instant lastFailure;
        Instant lockedUntil;
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Seconds until this username may try again, or 0 if it is not locked. */
    public long secondsLocked(String username) {
        Attempts a = attempts.get(key(username));
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            Instant now = clock.instant();
            if (a.lockedUntil != null && now.isBefore(a.lockedUntil)) {
                return Math.max(1, Duration.between(now, a.lockedUntil).toSeconds());
            }
            return 0;
        }
    }

    public void recordFailure(String username) {
        Attempts a = attempts.computeIfAbsent(key(username), k -> new Attempts());
        synchronized (a) {
            Instant now = clock.instant();
            // Old failures and an expired lock no longer count
            if (a.lastFailure != null && now.isAfter(a.lastFailure.plus(LOCK_DURATION))) {
                a.failures = 0;
            }
            if (a.lockedUntil != null && !now.isBefore(a.lockedUntil)) {
                a.lockedUntil = null;
                a.failures = 0;
            }
            a.failures++;
            a.lastFailure = now;
            if (a.failures >= MAX_FAILURES) {
                a.lockedUntil = now.plus(LOCK_DURATION);
            }
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
