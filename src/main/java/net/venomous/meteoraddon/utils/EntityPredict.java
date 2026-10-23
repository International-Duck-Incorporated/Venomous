package net.venomous.meteoraddon.utils;

/**
 * Where a moving entity will be in a few ticks, if it keeps doing what it is doing now.
 *
 * <p>Rod play is aiming at where someone is about to be: the hook travels, the target travels,
 * and the reel-in only lands a knockback if the two meet. The vanilla per-tick motion constants
 * are applied here in their familiar order - velocity carries with drag, gravity bends it, and
 * ground friction (slipperiness 0.6 through the 0.91 multiplier, the same 0.546 that decayed the
 * Simulation offsets in the anticheat logs) flattens anything standing on a floor.</p>
 *
 * <p>This is a projection of the velocity the entity was last seen with, not a simulation of its
 * brain: a target that stops, turns or jumps mid-window lands somewhere else. That is the same
 * bet the original makes - the predicted-ticks slider exists so the user can shorten the bet
 * when it stops paying.</p>
 *
 * <p>Free of Minecraft types so it unit tests on a plain JVM.</p>
 */
public final class EntityPredict {
    /** Vanilla gravity per tick, before drag. */
    public static final double GRAVITY = 0.08;
    /** Vanilla vertical drag. */
    public static final double VERTICAL_DRAG = 0.98;
    /** Vanilla horizontal drag in air. */
    public static final double AIR_DRAG = 0.91;
    /** Vanilla horizontal friction on a default floor: slipperiness 0.6 through the 0.91. */
    public static final double GROUND_FRICTION = 0.546;

    private EntityPredict() {
    }

    /**
     * Projects an entity's motion {@code ticks} ticks ahead.
     *
     * @param x, y, z    the entity's current position
     * @param vx, vy, vz the entity's current velocity
     * @param onGround   whether the entity is standing on a floor this tick
     * @param ticks      how many ticks to project; zero returns the position unchanged
     * @return {@code {x, y, z}} at the end of the projection
     */
    public static double[] predict(double x, double y, double z,
                                   double vx, double vy, double vz,
                                   boolean onGround, int ticks) {
        for (int i = 0; i < ticks; i++) {
            x += vx;
            z += vz;

            if (onGround) {
                vy = 0.0;
                vx *= GROUND_FRICTION;
                vz *= GROUND_FRICTION;
            } else {
                y += vy;
                vy = (vy - GRAVITY) * VERTICAL_DRAG;
                vx *= AIR_DRAG;
                vz *= AIR_DRAG;
            }
        }

        return new double[] {x, y, z};
    }
}
