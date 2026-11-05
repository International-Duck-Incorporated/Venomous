package net.venomous.meteoraddon.utils.combat;

import java.util.Random;

/**
 * CPS delay timing utilities shared across modules.
 */
public final class AttackTiming {

    private AttackTiming() {}

    /**
     * Returns the next CPS delay in ticks based on the technique and CPS settings.
     * Matches the original KillAura timing logic exactly.
     */
    public static int nextCpsDelay(Random random, Mode technique, int minCps, int maxCps,
            boolean isVulcan, int vulcanRandomizationMs, boolean vulcanSpeedRandomization) {
        int avg = (minCps + maxCps) / 2;
        double base = 20.0 / Math.max(1, avg);
        if (isVulcan) {
            base = 20.0 / Math.max(9, Math.min(12, avg));
        }

        double var = (random.nextDouble() - 0.5) * 2.0 * (maxCps - minCps) / Math.max(1.0, avg);

        double interval = switch (technique) {
            case STABILIZED -> base + var;
            case BUTTERFLY -> random.nextBoolean() ? 20.0 / Math.max(1, avg + 2) : 20.0 / Math.max(1, avg - 2);
            case NORMAL_DISTRIBUTION -> base + random.nextGaussian() * base * 0.25;
            case EFFICIENT -> base;
            case SWITCH -> base + (random.nextDouble() - 0.5) * 2.0;
        };

        if (isVulcan && vulcanSpeedRandomization && vulcanRandomizationMs > 0) {
            double msVariation = (random.nextDouble() - 0.5) * 2.0 * vulcanRandomizationMs;
            interval += msVariation / 50.0;
        }

        return Math.max(1, (int) Math.round(interval));
    }

    /** CPS mode enumeration matching the former module enums. */
    public enum Mode {
        STABILIZED,
        BUTTERFLY,
        NORMAL_DISTRIBUTION,
        EFFICIENT,
        SWITCH
    }
}