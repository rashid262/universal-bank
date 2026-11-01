package com.bank.transaction.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService attempts = new LoginAttemptService(clock);

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(username);
        }
    }

    @Test
    void locksAfterFiveFailures() {
        fail("admin", 4);
        assertEquals(0, attempts.secondsLocked("admin"));

        fail("admin", 1);

        long locked = attempts.secondsLocked("admin");
        assertTrue(locked > 14 * 60 && locked <= 15 * 60, "about 15 minutes, got " + locked);
    }

    @Test
    void lockExpiresAfterFifteenMinutes() {
        fail("admin", 5);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertEquals(0, attempts.secondsLocked("admin"));
    }

    @Test
    void successClearsTheCount() {
        fail("wazeh", 4);
        attempts.recordSuccess("wazeh");
        fail("wazeh", 4);
        assertEquals(0, attempts.secondsLocked("wazeh"));
    }

    @Test
    void oldFailuresStopCounting() {
        fail("wazeh", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("wazeh", 1);
        assertEquals(0, attempts.secondsLocked("wazeh"));
    }

    @Test
    void unknownUsernamesAreLockedTheSameWay() {
        fail("no-such-user", 5);
        assertTrue(attempts.secondsLocked("no-such-user") > 0);
    }

    @Test
    void usernameCaseAndSpacesDoNotBypassTheLock() {
        fail("Admin", 2);
        fail(" admin ", 2);
        fail("ADMIN", 1);
        assertTrue(attempts.secondsLocked("admin") > 0);
    }
}
