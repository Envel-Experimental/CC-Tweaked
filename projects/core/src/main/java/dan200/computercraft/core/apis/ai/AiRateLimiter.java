// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.ai;

import dan200.computercraft.core.AiConfig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-player sliding-window rate limiter for the AI API.
 *
 * <p>Three independent windows are enforced simultaneously:
 * <ul>
 *   <li>Per-minute (burst protection)</li>
 *   <li>Per-hour (session protection)</li>
 *   <li>Per-day (quota protection)</li>
 * </ul>
 *
 * <p>A global {@link #globalConcurrent} counter caps total in-flight requests
 * across all players, preventing executor thread exhaustion on large servers.
 *
 * <p>Thread-safety: all state uses compare-and-swap on {@link AtomicInteger}.
 * Window eviction runs every 5 minutes on a daemon thread. The map uses
 * {@link ConcurrentHashMap} — individual player state is only accessed under
 * its own lock-free CAS loop.
 *
 * <p>Usage:
 * <pre>{@code
 * var result = AiRateLimiter.INSTANCE.check(playerId);
 * if (result != LimitResult.ALLOWED) {
 *     throw new LuaException(result.errorMessage());
 * }
 * // ... dispatch request ...
 * AiRateLimiter.INSTANCE.release(); // always call on completion
 * }</pre>
 */
public final class AiRateLimiter {

    public static final AiRateLimiter INSTANCE = new AiRateLimiter();

    /** Result of a rate-limit check. */
    public enum LimitResult {
        ALLOWED,
        RATE_LIMITED_MINUTE,
        RATE_LIMITED_HOUR,
        RATE_LIMITED_DAY,
        GLOBAL_CONCURRENT_LIMIT;

        public String errorMessage() {
            return switch (this) {
                case ALLOWED               -> "";
                case RATE_LIMITED_MINUTE   -> "AI rate limit reached: too many requests per minute.";
                case RATE_LIMITED_HOUR     -> "AI rate limit reached: too many requests this hour.";
                case RATE_LIMITED_DAY      -> "AI rate limit reached: daily quota exceeded.";
                case GLOBAL_CONCURRENT_LIMIT -> "AI is currently busy (server limit reached). Try again shortly.";
            };
        }
    }

    /** Per-player sliding window state. Lock-free via AtomicInteger CAS. */
    private static final class PlayerState {
        // --- Minute window ---
        volatile long minuteWindowStart = System.currentTimeMillis();
        final AtomicInteger minuteCount = new AtomicInteger(0);

        // --- Hour window ---
        volatile long hourWindowStart = System.currentTimeMillis();
        final AtomicInteger hourCount = new AtomicInteger(0);

        // --- Day window ---
        volatile long dayWindowStart = System.currentTimeMillis();
        final AtomicInteger dayCount = new AtomicInteger(0);

        /** Timestamp of the last successful request (for eviction). */
        volatile long lastAccess = System.currentTimeMillis();
    }

    private final ConcurrentHashMap<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final AtomicInteger globalConcurrent = new AtomicInteger(0);

    /** Background eviction: removes stale entries every 5 minutes. */
    private final ScheduledExecutorService evictionScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "cc-ai-ratelimit-eviction");
            t.setDaemon(true);
            return t;
        });

    private AiRateLimiter() {
        evictionScheduler.scheduleAtFixedRate(this::evict, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Check if a player is allowed to make an AI request.
     * Atomically increments all window counters if allowed.
     *
     * @param playerId The player's UUID.
     * @return {@link LimitResult#ALLOWED} if the request may proceed,
     *         or the specific limit that was exceeded.
     */
    public LimitResult check(UUID playerId) {
        // Global concurrent check first (cheapest check, no per-player state needed).
        var maxConcurrent = AiConfig.maxGlobalConcurrent;
        if (maxConcurrent > 0) {
            int current;
            do {
                current = globalConcurrent.get();
                if (current >= maxConcurrent) return LimitResult.GLOBAL_CONCURRENT_LIMIT;
            } while (!globalConcurrent.compareAndSet(current, current + 1));
        }

        var state = playerStates.computeIfAbsent(playerId, id -> new PlayerState());
        state.lastAccess = System.currentTimeMillis();

        // Check per-player windows in order: minute → hour → day.
        var result = checkWindows(state);
        if (result != LimitResult.ALLOWED && maxConcurrent > 0) {
            globalConcurrent.decrementAndGet(); // undo the global increment
        }
        return result;
    }

    /**
     * Must be called when a request completes (success or error) to release
     * the global concurrent slot. Pair exactly once with each successful {@link #check}.
     */
    public void release() {
        globalConcurrent.decrementAndGet();
    }

    private LimitResult checkWindows(PlayerState state) {
        var now = System.currentTimeMillis();

        // Minute window
        if (AiConfig.requestsPerMinute > 0) {
            if (now - state.minuteWindowStart >= 60_000L) {
                state.minuteWindowStart = now;
                state.minuteCount.set(0);
            }
            if (state.minuteCount.incrementAndGet() > AiConfig.requestsPerMinute) {
                state.minuteCount.decrementAndGet();
                return LimitResult.RATE_LIMITED_MINUTE;
            }
        }

        // Hour window
        if (AiConfig.requestsPerHour > 0) {
            if (now - state.hourWindowStart >= 3_600_000L) {
                state.hourWindowStart = now;
                state.hourCount.set(0);
            }
            if (state.hourCount.incrementAndGet() > AiConfig.requestsPerHour) {
                state.hourCount.decrementAndGet();
                state.minuteCount.decrementAndGet(); // undo minute
                return LimitResult.RATE_LIMITED_HOUR;
            }
        }

        // Day window
        if (AiConfig.requestsPerDay > 0) {
            if (now - state.dayWindowStart >= 86_400_000L) {
                state.dayWindowStart = now;
                state.dayCount.set(0);
            }
            if (state.dayCount.incrementAndGet() > AiConfig.requestsPerDay) {
                state.dayCount.decrementAndGet();
                state.hourCount.decrementAndGet();
                state.minuteCount.decrementAndGet();
                return LimitResult.RATE_LIMITED_DAY;
            }
        }

        return LimitResult.ALLOWED;
    }

    /**
     * Remove player state entries that have not made a request in over 25 hours.
     * Runs every 5 minutes on the eviction daemon thread.
     */
    private void evict() {
        var cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25);
        playerStates.entrySet().removeIf(e -> e.getValue().lastAccess < cutoff);
    }

    /** Visible for testing only. */
    ConcurrentHashMap<UUID, PlayerState> playerStatesForTest() {
        return playerStates;
    }

    /** Visible for testing only. */
    AtomicInteger globalConcurrentForTest() {
        return globalConcurrent;
    }
}
