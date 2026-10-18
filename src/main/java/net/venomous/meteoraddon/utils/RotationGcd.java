package net.venomous.meteoraddon.utils;

/**
 * Rotation deltas a real mouse cannot produce.
 *
 * <p>A physical mouse reports integer counts, and the client turns those counts into degrees
 * through a sensitivity curve. The result is that every genuine yaw or pitch delta is an exact
 * multiple of a fixed grid step, the sensitivity GCD. Writing rotation directly with an
 * arbitrary float - {@code setYRot(current + 7.0F)} - therefore produces a delta no human
 * could ever generate, which is the signature rotation checks look for.</p>
 *
 * <p>Formula: {@code f = sensitivity * 0.6 + 0.2}, {@code gcd = f^3 * 1.2}.</p>
 */
public final class RotationGcd {
    private RotationGcd() {
    }

    /**
     * The grid step for a given mouse sensitivity, in degrees.
     *
     * <p>Zero is a legitimate sensitivity - Minecraft's slider goes to 0% - and still yields a
     * real grid of 0.0096 degrees, so only non-finite or negative values are rejected.</p>
     *
     * @return the GCD, or 0 when the sensitivity is unusable and the delta should be left alone
     */
    public static double factor(double sensitivity) {
        if (!Double.isFinite(sensitivity) || sensitivity < 0.0) {
            return 0.0;
        }

        double f = sensitivity * 0.6 + 0.2;
        return f * f * f * 1.2;
    }

    /**
     * Snaps a rotation delta onto the sensitivity grid.
     *
     * <p>Rounds to the nearest multiple rather than truncating: a real mouse count maps to the
     * nearest representable angle, so rounding matches the client and truncation would bias
     * every step downwards.</p>
     *
     * @param sensitivity the configured mouse sensitivity
     * @param delta       the intended rotation delta in degrees
     * @return the quantised delta, or the original when the GCD is not usable
     */
    public static float quantize(double sensitivity, float delta) {
        double gcd = factor(sensitivity);

        if (gcd <= 0.0001) {
            return delta;
        }

        return (float)(Math.round(delta / gcd) * gcd);
    }
}
