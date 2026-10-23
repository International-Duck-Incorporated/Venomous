package net.venomous.meteoraddon.utils;

/**
 * Rotation math for aiming an action at a point in the world.
 *
 * <p>The conventions are Minecraft's own, matching {@link MovementMath}'s movement angles and
 * {@link ScaffoldAim}'s pitch: yaw zero faces {@code +Z} and grows towards {@code -X}, and a
 * downward look is positive pitch. {@link ScaffoldAim#pitchToPoint} is reused directly for the
 * pitch so every module that aims agrees on what an angle means.</p>
 *
 * <p>Free of Minecraft types so it unit tests on a plain JVM.</p>
 */
public final class RodAimMath {
    private RodAimMath() {
    }

    /**
     * The yaw that looks from an eye at a point.
     *
     * <p>The components are the point's offset <em>from the eye</em>, not absolute positions.</p>
     */
    public static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** The pitch that looks from an eye at a point; delegates to {@link ScaffoldAim}. */
    public static float pitchTo(double eyeX, double eyeY, double eyeZ,
                                double pointX, double pointY, double pointZ) {
        return ScaffoldAim.pitchToPoint(eyeX, eyeY, eyeZ, pointX, pointY, pointZ);
    }

    /**
     * Whether a target yaw sits inside a field of view around the camera's yaw.
     *
     * <p>The FOV describes the whole cone, so half of it is the tolerance either side. The
     * comparison wraps at 180 degrees: a target one degree past due north counts as one degree
     * away from a camera facing south, not 359.</p>
     *
     * @param cameraYaw  the look the player actually holds
     * @param targetYaw  the yaw of what might be aimed at
     * @param fovDegrees the full cone width in degrees; 360 means everything
     * @return true when the target is inside the cone
     */
    public static boolean withinFov(float cameraYaw, float targetYaw, double fovDegrees) {
        if (fovDegrees >= 360.0D) {
            return true;
        }

        double distance = Math.abs(wrapDegrees(targetYaw - cameraYaw));
        return distance <= fovDegrees / 2.0D;
    }

    /** Wraps an angle difference into {@code [-180, 180)}. */
    public static double wrapDegrees(double angle) {
        angle %= 360.0D;
        if (angle >= 180.0D) angle -= 360.0D;
        if (angle < -180.0D) angle += 360.0D;
        return angle;
    }
}
