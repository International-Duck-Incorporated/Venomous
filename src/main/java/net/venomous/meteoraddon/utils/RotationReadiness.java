package net.venomous.meteoraddon.utils;

/**
 * Whether the rotation the server has already received is close enough to fire a deferred action.
 *
 * <p>This is the decision {@link PreRotate} makes in the movement packet's PRE phase for every
 * queued action, split out of it so it can be unit tested without a game. PreRotate reads
 * {@code Rotations.serverYaw}/{@code serverPitch} - the rotation the server actually holds - and
 * asks whether the action's aim has landed.</p>
 *
 * <p>The yaw and the pitch are not the same question. An action whose yaw is the player's own
 * camera yaw moves with the mouse: waiting for <em>that</em> yaw to land measures the mouse rather
 * than the rotation, and since a queued action is discarded after five ticks of not landing, the
 * caller would act only while the player happened to be holding the mouse still. That is the
 * contract {@code yawMatters} expresses - {@code false} means the yaw never holds the action. The
 * yaw of every other caller, such as a module aiming at a block it is about to mine, is a real
 * target: the action must not fire until the server holds it.</p>
 *
 * <p>The yaw comparison wraps around the full circle - 359 degrees to 1 degree is two degrees of
 * difference, not three hundred and fifty-eight. The pitch has no wrap: it is clamped by the game
 * to -90..90, so a plain difference is already the shortest one.</p>
 */
public final class RotationReadiness {
    private RotationReadiness() {
    }

    /**
     * Whether the rotation the server holds is close enough to {@code targetYaw}/{@code targetPitch}
     * for an action waiting on it to fire.
     *
     * @param serverYaw        the yaw the server last received, degrees
     * @param serverPitch      the pitch the server last received, degrees
     * @param targetYaw        the yaw the action's aim wants, degrees
     * @param targetPitch      the pitch the action's aim wants, degrees
     * @param yawMatters       whether the yaw has to have landed as well as the pitch; pass
     *                         {@code false} for a caller whose yaw is the player's own camera
     * @param toleranceDegrees how close counts as landed, degrees, on both axes
     */
    public static boolean isReady(double serverYaw, double serverPitch,
                                  float targetYaw, float targetPitch,
                                  boolean yawMatters, float toleranceDegrees) {
        if (yawMatters && Math.abs(wrapDegrees((float) (serverYaw - targetYaw))) > toleranceDegrees) {
            return false;
        }

        return Math.abs((float) serverPitch - targetPitch) <= toleranceDegrees;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
