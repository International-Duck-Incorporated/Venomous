package net.venomous.meteoraddon.utils;

/**
 * The jump-reset decision: whether to press the jump key on the tick a hit lands.
 *
 * <p>Jump resetting is jumping while the knockback is being applied, so the same upward velocity
 * the game grants a normal jump multiplies into the launch and the hit pushes the player a
 * fraction of its range. This is the decision {@code JumpReset} makes each tick, split out of the
 * module so it can be unit tested without a game. The gate list is the one Raven B4 evaluates in
 * its own {@code JumpReset}, ported one boolean at a time - fresh hurt, grounded, not burning, no
 * jump boost, poison or wither, knockback direction inside an arc, the three optional gates, and
 * the percentage chance.</p>
 *
 * <p>The optional gates are passed as a raw condition plus its requirement switch rather than
 * pre-waived, because the waiver rule ("eligible when held, irrelevant when not required") is part
 * of the ported behaviour and belongs where it can be tested: {@code require-mouse-down} held or
 * false. A gate whose requirement is off waives its condition; one whose requirement is on needs
 * its condition satisfied.</p>
 */
public final class JumpResetRules {
    private JumpResetRules() {
    }

    /**
     * The horizontal direction a velocity delta points in, expressed as a Minecraft yaw.
     *
     * <p>Minecraft's yaw zero faces +Z and turns clockwise towards -X, so the velocity
     * {@code (x, z)} is produced by facing {@code atan2(-x, z)}: moving +Z is yaw 0, +X is yaw
     * -90, -X is +90. The result is in -180..180 rather than the game's 0..360; callers route it
     * through {@link #withinArc}, which is where the wrapping lives.</p>
     *
     * @param deltaX the velocity's X component
     * @param deltaZ the velocity's Z component
     */
    public static float motionYawOf(double deltaX, double deltaZ) {
        return (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
    }

    /**
     * Whether a movement direction sits inside a symmetric arc around the facing, the arc given in
     * full degrees and counted as a half on each side the way Raven's field of view check halves
     * its own argument.
     *
     * <p>The comparison wraps around the 0/360 seam - a facing of 350 and a movement of 10 are
     * twenty degrees apart. Raven passes 330, which halves to 165 and therefore passes everything
     * except a movement within fifteen degrees of exactly backwards; shipping 330 keeps the port's
     * parity while letting the caller lower it to actually reject sideways and rear hits.</p>
     *
     * @param facingYaw  the player's yaw, degrees
     * @param motionYaw  the direction the movement points, from {@link #motionYawOf}, degrees
     * @param arcDegrees the full accepting arc around the facing, degrees
     */
    public static boolean withinArc(float facingYaw, float motionYaw, float arcDegrees) {
        return Math.abs(wrapDegrees(facingYaw - motionYaw)) <= arcDegrees / 2.0F;
    }

    /**
     * Whether this tick's hurt time is a hit worth jump resetting.
     *
     * <p>Every gate is a knockback-reading hazard the port inherited with its arithmetic:</p>
     *
     * <ul>
     *   <li>{@code freshHit} - only a hurt time that <em>grew</em> this tick is a hit; a constant
     *       hurt window means the same hit still being drained, not a new one.</li>
     *   <li>{@code ignoreNext} - the caller's fall latch: landing after falling further than the
     *       module's threshold arms it, because the hurt time a landing deals is the fall's, and
     *       jumping into a fall's knockback multiplies a descent the player did not choose.</li>
     *   <li>{@code onGround} - a jump can only start from the ground; mid-air hits cannot be
     *       reset and pressing the key does nothing but spam.</li>
     *   <li>{@code burning} - fire covers hurt time constantly, so the growth check cannot tell
     *       a hit from the burn.</li>
     *   <li>{@code hasBadEffect} - jump boost changes the arc the reset relies on, and poison and
     *       wither drip hurt time on their own schedule, so neither can be distinguished from a
     *       hit either.</li>
     *   <li>{@code inResetArc} - the movement direction gate from {@link #withinArc}.</li>
     *   <li>{@code aimingAtPlayer}, {@code requireAim} - the crosshair gate: only required when
     *       the caller asks for it.</li>
     *   <li>{@code attackKeyDown}, {@code requireMouseDown} - the attack-key gate; being mid
     *       combo is when the reset pays off.</li>
     *   <li>{@code forwardKeyDown}, {@code requireForward} - the forward-key gate; walking into
     *       the fight is when the knockback lands facing melee.</li>
     *   <li>{@code rollPassed} - the caller's chance roll, already settled as {@code true} at
     *       100% parity.</li>
     * </ul>
     */
    public static boolean shouldJump(boolean freshHit, boolean ignoreNext,
                                     boolean onGround,
                                     boolean burning, boolean hasBadEffect,
                                     boolean inResetArc,
                                     boolean aimingAtPlayer, boolean requireAim,
                                     boolean attackKeyDown, boolean requireMouseDown,
                                     boolean forwardKeyDown, boolean requireForward,
                                     boolean rollPassed) {
        if (!freshHit) {
            return false;
        }

        if (ignoreNext || !onGround || burning || hasBadEffect || !inResetArc) {
            return false;
        }

        if (!aimingAtPlayer && requireAim) {
            return false;
        }

        if (!attackKeyDown && requireMouseDown) {
            return false;
        }

        if (!forwardKeyDown && requireForward) {
            return false;
        }

        return rollPassed;
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
