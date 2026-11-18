package org.yamcs.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class AuthRateLimiterTest {

    @Test
    public void testLimitPerIp() throws Exception {
        var limiter = new AuthRateLimiter(2);
        limiter.acquire("10.0.0.1");
        limiter.acquire("10.0.0.1");
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("10.0.0.1"));
        assertDoesNotThrow(() -> limiter.acquire("10.0.0.2"));
    }

    @Test
    public void testReleasedAttemptsDoNotCount() throws Exception {
        var limiter = new AuthRateLimiter(2);
        for (int i = 0; i < 10; i++) {
            limiter.acquire("10.0.0.1");
            limiter.release("10.0.0.1");
        }
        limiter.acquire("10.0.0.1");
        limiter.acquire("10.0.0.1");
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("10.0.0.1"));
    }
}
