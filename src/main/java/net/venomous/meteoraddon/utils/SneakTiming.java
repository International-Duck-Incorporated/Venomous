package net.venomous.meteoraddon.utils;

/**
 * How long {@code v-scaffold} holds a crouch at a bridge edge.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as {@link AimEase},
 * {@link ClickTiming} and {@link ScaffoldCadence}.</p>
 *
 * <h2>Why a hold is needed at all</h2>
 *
 * <p>The obvious implementation is a boolean: crouch while over an edge, stand the moment it is not.
 * That is a bad tell, because it snaps. AntiCheat-Addition watches for exactly this shape in
 * {@code Scaffold.parts.Safewalk.Timing} (20 VL), which fires when a player has moved within 355ms,
 * has <em>not</em> moved within 175ms, and either has not sneaked recently or has not held the sneak
 * for more than 148ms:</p>
 *
 * <pre>{@code
 *   hasMovedRecently(XZ_MOVEMENT, 355) && !hasMovedRecently(XZ_MOVEMENT, 175)
 *       && !(hasSneakedRecently(175) && lastSneakDuration > 148)
 * }</pre>
 *
 * <p>In other words: movement that stops abruptly without a sustained crouch. Holding the crouch for
 * a real duration rather than one tick is what stops the transition reading as a snap, and the
 * human reason is the same - nobody lands on a new block and stands up instantly, because they are
 * still crouched and thinking about the next one.</p>
 *
 * <p>Vape models this as a "Sneak delay" setting ("Delay until standing after sneaking") with
 * 100/200/500ms presets and a 30ms floor. This is the same idea, expressed as a pure decision so it
 * can be tested without a running game.</p>
 *
 * @see #shouldHold
 */
public final class SneakTiming {
    /** Vape's presets for this, in milliseconds. The middle one is a sane default. */
    public static final int DEFAULT_SNEAK_DELAY_MS = 200;

    /**
     * Floor on a single crouch, in milliseconds.
     *
     * <p>Without it a sub-tick edge blip - the player clips a boundary for one tick - could press
     * and release the key inside a single tick, which the server sees as a zero-length sneak and
     * which no human hand produces.</p>
     */
    public static final int MINIMUM_SNEAK_MS = 30;

    private SneakTiming() {
    }

    /**
     * Whether the crouch should still be held this tick.
     *
     * <p>Called once per tick with the current edge state. The module owns the timestamp; this only
     * decides, so it stays a pure function of its arguments.</p>
     *
     * @param atEdge        the player is over open air and needs the crouch
     * @param onGround      the player is standing on something; a mid-air crouch is meaningless
     * @param currentlyHeld the crouch is down right now
     * @param heldForMs     milliseconds the current crouch has been down, 0 if it is not down
     * @param delayMs       how long to keep holding after the edge clears
     * @return true to keep (or start) sneaking
     */
    public static boolean shouldHold(boolean atEdge, boolean onGround, boolean currentlyHeld,
                                     long heldForMs, int delayMs) {
        if (!onGround) return false;

        // At the edge: always crouch, and start the clock if this is the first tick of it.
        if (atEdge) return true;

        // Clear of the edge, but the crouch has not been held long enough yet.
        return currentlyHeld && heldForMs < Math.max(MINIMUM_SNEAK_MS, delayMs);
    }
}