package net.venomous.meteoraddon.utils;

import java.util.Random;

/**
 * Humanlike click timing for middle-click triggered actions.
 *
 * <p>Deliberately free of any Minecraft types so the logic can be unit tested on a plain JVM.</p>
 */
public final class ClickTiming {
    /** Minecraft runs at 20 ticks per second, so a single click cycle is 20 ticks long. */
    public static final int TICKS_PER_SECOND = 20;

    private ClickTiming() {
    }

    /** Click cadence profiles offered by the middle-click action setting. */
    public enum Technique {
        /** Even and metronomic, with essentially no variance. */
        Stabilized,
        /** Tightest cadence of the three, alternating by a tick to imitate a two-handed wingbeat. */
        Butterfly,
        /** Gaussian about the midpoint of the CPS window. */
        NormalDistribution
    }

    /**
     * Ticks to wait between the middle-click press and acting on it.
     *
     * <p>A real click cycle lasts {@link #TICKS_PER_SECOND} ticks, so a 7 CPS profile fires
     * roughly three ticks after the press. The window is clamped defensively: {@code minCps} is
     * floored at 1 and {@code maxCps} is floored at {@code minCps}, so an inverted or zero
     * setting cannot divide by zero or produce a nonsensical negative delay.
     *
     * @param technique cadence profile to draw the delay from
     * @param minCps    lower bound of the clicks-per-second window
     * @param maxCps    upper bound of the clicks-per-second window
     * @param random    source of Gaussian noise; must not be {@code null}
     * @param parity    flips between calls to give {@link Technique#Butterfly} its alternation
     * @return delay in ticks, always at least 1
     */
    public static int reactionDelayTicks(Technique technique, int minCps, int maxCps, Random random, boolean parity) {
        int min = Math.max(1, minCps);
        int max = Math.max(min, maxCps);
        double midpoint = (min + max) / 2.0;

        double cps = switch (technique) {
            case Stabilized -> midpoint;
            case Butterfly -> max;
            case NormalDistribution -> {
                // Spread scales with how wide the window is, so a narrow window stays
                // consistent and a wide one varies as much as the user asked it to.
                double deviation = (max - min) / 4.0;
                yield clamp(midpoint + random.nextGaussian() * deviation, min, max);
            }
        };

        int ticks = (int) Math.round(TICKS_PER_SECOND / cps);
        if (technique == Technique.Butterfly && !parity) ticks++;

        return Math.max(1, ticks);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
