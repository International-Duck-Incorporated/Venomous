package net.venomous.meteoraddon.utils;

/**
 * How long the smoothed pitch stays put after a placement instead of chasing the next face.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as
 * {@link AimEase}, {@link SneakTiming} and {@link ScaffoldCadence}.</p>
 *
 * <h2>Why a hold is needed at all</h2>
 *
 * <p>Between placements the pitch target is recomputed every tick from the face currently underfoot,
 * and smoothed at a high gain so the server's ray reaches it. That gain is correct for a placement
 * that is about to leave and wrong for the ticks in between: the target drifts a few degrees every
 * tick while the player walks, so the smoothed pitch is <em>always</em> moving, tick after tick,
 * even though nothing is going out. A rotation that moves continuously at a steady clip is not a
 * rotation a hand produces - a hand aims, holds, and corrects.</p>
 *
 * <p>The ported shape comes from Raven B4's scaffold, which freezes its aim rotation on a duration
 * that depends on what the player is doing: a long hold while sprinting (a sprinting bridge is a
 * head-down, committed glide), a short one while walking (the head comes back up few ticks after
 * each place), a medium one when standing still (the player has stopped and is looking at the
 * edge, not the next block). Those three durations travel with this: 710ms sprinting, 110ms
 * moving, 460ms idle.</p>
 *
 * <p>When a placement <em>is</em> waiting to leave, the hold must give way: the pitch has to be
 * working to bring the server's ray onto the face, and the ray gate in the module is what judges
 * that. So a pending placement turns the hold off, and the high-gain tracking resumes - which is
 * the shape a real hand makes anyway: steadier between actions, purposeful during them.</p>
 *
 * <p>The rotation never goes fully still while held, because the module's pitch wobble keeps the
 * rotation from repeating exactly for Grim's {@code AimDuplicateLook}. A hold is not a freeze of
 * the packet, only of the chase.</p>
 *
 * @see #shouldHold
 */
public final class AimHold {
    /** Raven B4's sprinting hold, in milliseconds. */
    public static final int SPRINTING_HOLD_MS = 710;

    /** Raven B4's moving hold, in milliseconds. */
    public static final int MOVING_HOLD_MS = 110;

    /** Raven B4's idle hold, in milliseconds. */
    public static final int IDLE_HOLD_MS = 460;

    private AimHold() {
    }

    /**
     * How long to hold the aim after a placement, in milliseconds.
     *
     * @param sprinting the player is currently sprinting
     * @param moving    the player has any movement input down
     * @return the hold duration in milliseconds
     */
    public static long duration(boolean sprinting, boolean moving) {
        if (sprinting) return SPRINTING_HOLD_MS;
        if (moving) return MOVING_HOLD_MS;
        return IDLE_HOLD_MS;
    }

    /**
     * Whether the smoothed pitch should keep its last value this tick.
     *
     * <p>Called once per tick. The module owns the clock; this only decides, so it stays a pure
     * function of its arguments.</p>
     *
     * @param placementPending     a placement is staged on {@code PreRotate} and waiting to leave;
     *                             always false while the aim must work
     * @param msSinceLastPlacement milliseconds since the last placement left the client, large
     *                             when nothing has ever been placed
     * @param sprinting            the player is currently sprinting
     * @param moving               the player has any movement input down
     * @return true to keep the smoothed pitch where it is this tick
     */
    public static boolean shouldHold(boolean placementPending, long msSinceLastPlacement,
                                     boolean sprinting, boolean moving) {
        if (placementPending) return false;
        return msSinceLastPlacement < duration(sprinting, moving);
    }
}
