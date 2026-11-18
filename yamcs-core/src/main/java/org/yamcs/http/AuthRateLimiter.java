package org.yamcs.http;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Limits authentication attempts per client IP address, to protect against brute-force login attempts.
 * <p>
 * Every attempt is counted before it is verified, so that concurrent attempts cannot exceed the limit. Successful
 * attempts are given back with {@link #release(String)}, so that in effect only failed attempts count against the
 * limit.
 */
public class AuthRateLimiter {

    // Expires after 1 min of inactivity
    private final Cache<String, Window> windows = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    private final int maxAttemptsPerSecond;

    public AuthRateLimiter(int maxAttemptsPerSecond) {
        this.maxAttemptsPerSecond = maxAttemptsPerSecond;
    }

    /**
     * Registers an authentication attempt from the given IP address.
     *
     * @throws TooManyRequestsException
     *             if the limit for this IP address is exceeded
     */
    public void acquire(String ip) throws TooManyRequestsException {
        var window = windows.asMap().computeIfAbsent(ip, k -> new Window());
        if (!window.tryAcquire(maxAttemptsPerSecond)) {
            throw new TooManyRequestsException("Too many login attempts");
        }
    }

    /**
     * Gives back an attempt that was registered with {@link #acquire(String)}, because it was successful.
     */
    public void release(String ip) {
        var window = windows.getIfPresent(ip);
        if (window != null) {
            window.release();
        }
    }

    private static class Window {
        private long start = System.currentTimeMillis();
        private int count;

        synchronized boolean tryAcquire(int max) {
            var now = System.currentTimeMillis();
            if (now - start > 1000) { // Reset every second
                start = now;
                count = 0;
            }
            if (count >= max) {
                return false;
            }
            count++;
            return true;
        }

        synchronized void release() {
            if (count > 0) {
                count--;
            }
        }
    }
}
