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
     * Largest number of milliseconds of jitter applied to the base gap.
     *
     * <p>Milliseconds, not ticks, and that is the whole point. The jitter used to be counted in
     * ticks, so it moved in 50ms steps - which is nothing at 20 TPS and half a second at 4 FPS,
     * where a tick is 250ms long. A 100ms jitter on a 250ms base lands the deadline on the far
     * side of the next tick boundary and the placement waits for the tick after that, doubling
     * the gap. Millisecond-scale jitter stays inside the tick it belongs to.</p>
     */
    public static final int CADENCE_JITTER_MS = 12;

    /**
     * Margin kept above the anticheat's floor.
     *
     * <p>The anticheat averages a batch of up to six real gaps, so the mean is what has to clear
     * 238ms rather than every individual gap. A little headroom above the floor covers the
     * scheduling slop of a low frame rate without eating into the gap the player needs.</p>
     */
    public static final double MIN_GAP_MARGIN_MS = 12.0;

    private ScaffoldCadence() {
    }

    /**
     * The gap the configuration asks for, before jitter.
     *
     * <p>Wall-clock, deliberately. A tick-counted gap assumes a frame rate: five ticks is 250ms at
     * 20 TPS and 1250ms at 4 FPS, and the client's frame rate is not something the module gets to
     * choose. The live log caught what that costs - the player jumped while bridging, the module
     * was still inside the cooldown from the previous placement, and they fell off the bridge,
     * because a jump is over well before 1250ms.</p>
     *
     * <p>The setting keeps its "ticks at 20 TPS" meaning, so the same number still means the same
     * thing to the player, but what is enforced is a duration. The anticheat's floor clamps it: no
     * configuration may ask for a gap the anticheat would flag.</p>
     */
    public static double nominalGapMillis(int placeDelay) {
        int base = Math.max(1, placeDelay);
        double nominal = base * (1000.0 / DEFAULT_TICKS_PER_SECOND);

        return Math.max(nominal, ACADDITION_MIN_STRAIGHT_DELAY_MS + MIN_GAP_MARGIN_MS);
    }

    /**
     * The gap to wait after a placement, in milliseconds.
     *
     * <p>The jitter pulls the gap <em>down</em> from the nominal, never up, and never below the
     * anticheat's floor. Pulling it up is the natural thing to do, but the gap is quantised to the
     * client's own tick: at 4 FPS a tick is 250ms, so a 275ms request lands on the far side of the
     * next tick boundary and the real gap becomes 500ms - long enough to miss a jump.</p>
     *
     * <p>What the anticheat measures is the real gap, which is the tick interval rounded up to fit,
     * so a request anywhere in [238, 250] produces the same 250ms of real time at 4 FPS and at 20
     * TPS. Sitting at the bottom of that window rather than the top is what keeps the placement on
     * the next tick instead of the one after it.</p>
     *
     * @param placeDelay   the {@code place-delay} setting
     * @param jitterRollMs a roll in {@code [0, CADENCE_JITTER_MS]}
     * @return the gap in milliseconds
     */
    public static double delayMillis(int placeDelay, int jitterRollMs) {
        double nominal = nominalGapMillis(placeDelay);
        int jitter = Math.max(0, Math.min(jitterRollMs, CADENCE_JITTER_MS));

        return Math.max(nominal - (CADENCE_JITTER_MS - jitter),
            ACADDITION_MIN_STRAIGHT_DELAY_MS);
    }

    /**
     * The mean interval a configuration actually produces.
     *
     * <p>Jitter is a uniform roll, so the mean cooldown is the midpoint of the range it spans.
     * This is the number the anticheat's batch average is compared against, so this is what has to
     * clear {@link #ACADDITION_MIN_STRAIGHT_DELAY_MS}.</p>
     */
    public static double meanIntervalMillis(int placeDelay, int jitterMs) {
        double nominal = nominalGapMillis(placeDelay);
        int jitter = Math.max(0, Math.min(jitterMs, CADENCE_JITTER_MS));

        return nominal - jitter / 2.0;
    }
}
