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

    /**
     * Snaps a target rotation onto the grid, measured from the rotation the server already holds.
     *
     * <p>Rotation checks do not validate absolute angles; they validate the <em>step</em> between
     * two consecutive rotations in the movement stream. So the question is never "is the target
     * on the grid" but "is the step from the last sent rotation a whole number of grid steps" -
     * and the last sent rotation is whatever the server currently holds, which is rarely a grid
     * multiple itself. Anchoring on it and quantising the difference is what makes the step
     * legal.</p>
     *
     * <p>A requested movement is never quantised down to nothing. A sub-grid nudge - the
     * scaffold's pitch wobble is smaller than one grid step at most sensitivities - would round
     * away to an unchanged rotation, and a rotation that repeats exactly tick after tick is its
     * own fingerprint (Grim's {@code AimDuplicateLook}). When the requested step is real but
     * rounds to zero, it becomes one whole grid step in the requested direction instead: the
     * smallest movement a real mouse can make. A genuinely zero step stays zero, because a hand
     * that asks for no movement must not be given some.</p>
     *
     * @param sensitivity the configured mouse sensitivity
     * @param anchor      the rotation the server currently holds, in degrees
     * @param target      the rotation the caller wants the server to hold, in degrees
     * @return the rotation to send, or the target unchanged when the GCD is not usable
     */
    public static float quantizeFrom(double sensitivity, float anchor, float target) {
        double gcd = factor(sensitivity);

        if (gcd <= 0.0001) {
            return target;
        }

        float delta = target - anchor;
        float snapped = (float)(Math.round(delta / gcd) * gcd);

        if (snapped == 0.0F && delta != 0.0F) {
            snapped = delta > 0.0F ? (float) gcd : (float) -gcd;
        }

        return anchor + snapped;
    }
}
