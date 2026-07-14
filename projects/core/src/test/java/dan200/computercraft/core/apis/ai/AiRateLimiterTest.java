// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.ai;

import dan200.computercraft.core.AiConfig;
import dan200.computercraft.test.core.ReplaceUnderscoresDisplayNameGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayNameGeneration(ReplaceUnderscoresDisplayNameGenerator.class)
class AiRateLimiterTest {

    private final UUID playerId = UUID.randomUUID();
    private final AiRateLimiter limiter = AiRateLimiter.INSTANCE;

    @BeforeEach
    void setUp() {
        // Reset limits to standard test values
        AiConfig.requestsPerMinute = 10;
        AiConfig.requestsPerHour = 100;
        AiConfig.requestsPerDay = 1000;
        AiConfig.maxGlobalConcurrent = 5;

        // Clear state
        limiter.playerStatesForTest().clear();
        limiter.globalConcurrentForTest().set(0);
    }

    @AfterEach
    void tearDown() {
        limiter.playerStatesForTest().clear();
        limiter.globalConcurrentForTest().set(0);
    }

    @Test
    void allows_requests_under_limit() {
        for (int i = 0; i < 5; i++) {
            assertEquals(AiRateLimiter.LimitResult.ALLOWED, limiter.check(playerId));
            limiter.release(); // Free concurrent slot
        }
    }

    @Test
    void blocks_when_minute_limit_exceeded() {
        // Use 10 requests (limit)
        for (int i = 0; i < 10; i++) {
            assertEquals(AiRateLimiter.LimitResult.ALLOWED, limiter.check(playerId));
            limiter.release(); // release concurrent, keep window count
        }

        // 11th request should fail
        assertEquals(AiRateLimiter.LimitResult.RATE_LIMITED_MINUTE, limiter.check(playerId));
    }

    @Test
    void blocks_when_global_concurrent_limit_exceeded() {
        AiConfig.maxGlobalConcurrent = 2;

        assertEquals(AiRateLimiter.LimitResult.ALLOWED, limiter.check(playerId));
        assertEquals(AiRateLimiter.LimitResult.ALLOWED, limiter.check(UUID.randomUUID()));

        // 3rd concurrent request should fail
        assertEquals(AiRateLimiter.LimitResult.GLOBAL_CONCURRENT_LIMIT, limiter.check(UUID.randomUUID()));
    }

    @Test
    void concurrent_requests_from_multiple_threads() throws InterruptedException {
        int threads = 50;
        AiConfig.requestsPerMinute = 1000;
        AiConfig.maxGlobalConcurrent = threads * 2; // don't hit global limit

        var executor = Executors.newFixedThreadPool(10);
        var latch = new CountDownLatch(threads);
        var successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                if (limiter.check(playerId) == AiRateLimiter.LimitResult.ALLOWED) {
                    successCount.incrementAndGet();
                    limiter.release();
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(threads, successCount.get(), "All concurrent requests should succeed under limit");
        
        executor.shutdown();
    }
}
