package net.venomous.meteoraddon.utils;

/**
 * Pure movement math shared by the movement modules.
 *
 * <p>Deliberately free of any Minecraft types so the logic can be unit tested on a plain JVM.
 * All angles are in Minecraft yaw degrees (0 = +Z, 90 = -X).</p>
 */
public final class MovementMath {
    /** Vanilla walking speed of a sprinting player on flat ground, in blocks/tick. */
    public static final double SPRINT_SPEED = 0.2873;
    /** Vanilla walking speed of a non-sprinting player on flat ground, in blocks/tick. */
    public static final double WALK_SPEED = 0.2158;

    private MovementMath() {
    }

    /**
    * Vanilla-accurate horizontal velocity for the given input.
    *
    * <p>Unlike the old ad-hoc angle juggling, this uses the same normalised
    * forward/strafe rotation the vanilla client uses, so diagonal movement is not
    * faster than straight movement — a very common reason anticheats flag
    * "Speed (Prediction)".</p>
    *
    * @param yaw    player yaw in degrees
    * @param fwd    forward input, -1..1
    * @param str    strafe input, -1..1 (positive = left, matching vanilla key presses)
    * @param speed  desired horizontal speed in blocks/tick
    * @return {velocityX, velocityZ}; {0,0} when there is no input
    */
    public static double[] velocity(double yaw, double fwd, double str, double speed) {
        double length = Math.sqrt(fwd * fwd + str * str);
        if (length < 1.0E-6 || speed <= 0.0) {
            return new double[] {0.0, 0.0};
        }

        // Normalise so diagonals are not sqrt(2) faster than cardinals.
        fwd /= length;
        str /= length;

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        double x = (str * cos - fwd * sin) * speed;
        double z = (fwd * cos + str * sin) * speed;
        return new double[] {x, z};
    }

    /**
    * Base horizontal move speed including potion effects.
    *
    * @param sprinting       whether the player is sprinting
    * @param speedAmplifier  Speed effect amplifier, or -1 when the effect is absent
    * @param slowAmplifier   Slowness effect amplifier, or -1 when the effect is absent
    */
    public static double baseSpeed(boolean sprinting, int speedAmplifier, int slowAmplifier) {
        double base = sprinting ? SPRINT_SPEED : WALK_SPEED;
        if (speedAmplifier >= 0) {
            base *= 1.0 + 0.2 * (speedAmplifier + 1);
        }

        if (slowAmplifier >= 0) {
            base *= Math.max(0.0, 1.0 - 0.15 * (slowAmplifier + 1));
        }

        return base;
    }

    /**
    * Vertical motion for a "hover" style flight that mimics falling/landing so that
    * prediction based checks (Vulcan's {@code Flight (Hover)} / {@code Flight (Prediction)})
    * keep seeing gravity being applied.
    *
    * @param tick         monotonically increasing tick counter
    * @param period       how often a downward pulse is emitted, in ticks
    * @param pulseMotion  downward motion applied during a pulse (negative)
    * @param up           whether the player requests going up
    * @param down         whether the player requests going down
    * @param vertical     requested vertical speed
    */
    public static double verticalMotion(int tick, int period, double pulseMotion, boolean up, boolean down, double vertical) {
        if (up) {
            return vertical;
        }

        if (down) {
            return -vertical;
        }

        if (period <= 0) {
            return 0.0;
        }

        return tick % period == 0 ? pulseMotion : 0.0;
    }

    /**
     * Rotates the movement input so the walk matches the rotation the server is being told.
     *
     * <p>The client computes its movement from the camera's yaw. The server predicts that movement
     * from the yaw in the movement packet, which a scaffold spoofs to point at the block being
     * placed. When the two differ the client walks a different line from the one the server
     * predicted, and the difference is Grim's {@code Simulation} - which flags at one millimetre.</p>
     *
     * <p>So the input is pre-rotated by the angle between them. If the server is told a yaw of
     * {@code S} and the camera is at {@code C}, the input is rotated by {@code S - C}: the client
     * then produces exactly the world-space movement the server expects, while the player is still
     * looking wherever they like.</p>
     *
     * <p>The convention is the game's own. Minecraft turns a yaw of {@code 0} to face {@code +Z},
     * and the forward vector is {@code (-sin, cos)} while the left vector - the one a strafe of
     * {@code +1} moves towards - is {@code (cos, sin)}. A strafe is {@code +1} for the left key:
     * {@code KeyboardInput} builds the vector as {@code new Vec2(strafe, forward)} from
     * {@code calculateImpulse(left, right)} and {@code calculateImpulse(forward, backward)}.</p>
     *
     * @param strafe   sideways input, {@code +1} for the left key
     * @param forward  forwards input, {@code +1} for the up key
     * @param deltaYaw the server's yaw minus the camera's yaw, in degrees
     * @return the compensated input as {@code {strafe, forward}}
     */
    public static float[] compensateInput(float strafe, float forward, float deltaYaw) {
        double radians = Math.toRadians(deltaYaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new float[] {
            (float) (strafe * cos - forward * sin),
            (float) (strafe * sin + forward * cos)
        };
    }

    /** Clamps a value into the inclusive range. */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
