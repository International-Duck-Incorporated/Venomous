package net.venomous.meteoraddon.utils;

/**
 * Placement cadence arithmetic for {@code v-scaffold}.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as
 * {@link AimEase} and {@link ClickTiming}.</p>
 *
 * <h2>Why the cadence has a floor</h2>
 *
 * <p>AntiCheat-Addition's {@code Scaffold.parts} family includes {@code ScaffoldAverageBatchProcessor},
 * which buffers a run of placements on one straight line, averages the real gaps between them, and
 * compares that against a minimum it derives per placement:</p>
 *
 * <pre>{@code
 *   if (actualAverage >= minExpectedAverage) return;   // no flag
 *   vl = 1.1 * (minExpectedAverage - actualAverage) + 5;
 * }</pre>
 *
 * <p>For an ordinary straight-line bridge that minimum is {@code placement_delay}, which the
 * anticheat defaults to <b>238ms</b>. Diagonal building is allowed to be faster (138ms) and sneaking
 * modifies the number further, but a straight bridge is held to the full delay. So the default
 * cadence has to average <em>at least</em> 238ms, which is 4.76 ticks at 20 TPS.</p>
 *
 * <p>The old default of {@code place-delay = 3} produced {@code 3 + random(0..2)} ticks - a mean of
 * 4 ticks, or 200ms. That is under the floor by construction, which is exactly what the anticheat
 * reports. Raising the setting alone would fix it only if the user found it; keeping the invariant
 * here means the default cannot drift back under it unnoticed.</p>
 *
 * @see #ACADDITION_MIN_STRAIGHT_DELAY_MS
 */
public final class ScaffoldCadence {
    /**
     * AntiCheat-Addition's minimum average gap between placements on a straight line, in
     * milliseconds. Taken from its {@code Scaffold.placement_delay} default of {@code 238}.
     */
    public static final double ACADDITION_MIN_STRAIGHT_DELAY_MS = 238.0;

    /**
     * Vanilla and most modded servers run the client at 20 TPS, so one tick is 50ms. The server
     * measures wall-clock gaps, which are never shorter than the tick count implies, so this is the
     * value to reason with when sizing the cadence.
     */
    public static final double DEFAULT_TICKS_PER_SECOND = 20.0;

    /** {@code place-delay} shipped by default. See the class note for why it is not 3. */
    public static final int DEFAULT_PLACE_DELAY = 5;

    /**
     * Largest extra number of ticks added on top of {@code place-delay}. Jitter only ever lengthens
     * the gap, so it can pull the mean up but never down - the floor that matters is the minimum.
     */
    public static final int CADENCE_JITTER_TICKS = 2;

    private ScaffoldCadence() {
    }

    /**
     * Ticks to wait after a placement.
     *
     * @param placeDelay the {@code place-delay} setting, clamped to at least 1
     * @param jitterRoll a roll in {@code [0, jitterTicks]}, clamped into that range
     * @param jitterTicks the largest value {@code jitterRoll} may take
     * @return the cooldown in ticks
     */
    public static int cooldownTicks(int placeDelay, int jitterRoll, int jitterTicks) {
        int base = Math.max(1, placeDelay);
        int ceiling = Math.max(0, jitterTicks);
        int jitter = Math.min(Math.max(0, jitterRoll), ceiling);
        return base + jitter;
    }

    /** Converts ticks to milliseconds at the given tick rate. */
    public static double ticksToMillis(double ticks, double ticksPerSecond) {
        if (ticksPerSecond <= 0) return Double.NaN;
        return ticks * 1000.0 / ticksPerSecond;
    }

    /**
     * The mean interval a configuration actually produces.
     *
     * <p>Jitter is a uniform roll over {@code [0, jitterTicks]}, so the mean cooldown is the
     * midpoint of the range it spans. This is the number the anticheat's batch average is compared
     * against, so this is what has to clear {@link #ACADDITION_MIN_STRAIGHT_DELAY_MS}.</p>
     */
    public static double meanIntervalMillis(int placeDelay, int jitterTicks, double ticksPerSecond) {
        int lowest = cooldownTicks(placeDelay, 0, jitterTicks);
        int highest = cooldownTicks(placeDelay, jitterTicks, jitterTicks);
        return ticksToMillis((lowest + highest) / 2.0, ticksPerSecond);
    }
}
