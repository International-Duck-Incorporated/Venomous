package net.venomous.meteoraddon.utils;

/**
 * The hit-select decision: whether a click on an opponent should be sent at all.
 *
 * <p>Every server ignores a hit that lands inside the target's invulnerability window - ten ticks
 * against the same damage source - but the click is not free: it spends the attacker's weapon
 * cooldown and the momentum of the exchange, exactly when nothing is gained for it. This is the
 * decision {@code HitSelect} makes about each attack packet, split out of the module so it can be
 * unit tested without a game. The gate list is Raven B4's {@code HitSelect} with the same shape:
 * a per-opponent state, a mask of reasons the click would be wasted, a sticky pause that holds any
 * reason until its duration has run, and a burst window predicted from the attacker's own hit.</p>
 *
 * <p>The port drops Raven's {@code maxHurtTime} window fallback and the unwired hurt-window sync -
 * the live path of the original never calls the sync, and only ever starts windows as ten ticks
 * from its own passed hit, which is what the port keeps.</p>
 *
 * <p>On 26.2 the filter runs at packet level: the attack arrives as a standalone
 * {@code ServerboundAttackPacket}, so a click the rules reject is cancelled there and vanilla's
 * separate swing packet still goes out - the visible behaviour of Raven's "fake swing" option
 * without the option.</p>
 */
public final class HitSelectRules {
    private HitSelectRules() {
    }

    /** The invulnerability window a landed hit grants its target, in ticks. */
    public static final int HURT_WINDOW_TICKS = 10;

    /**
     * How long a sent hit may wait for the server to confirm the target's hurt window grew, in
     * ticks, before the pending confirmation is abandoned.
     */
    public static final int SERVER_CONFIRM_TIMEOUT_TICKS = 30;

    /** No click-wasting reason applies; the click goes. */
    public static final int NO_BLOCK = 0;

    /** The attacker is matchmaking: the first hit of the exchange is still being waited for. */
    public static final int BLOCK_WAIT_FIRST = 1 << 0;

    /** The last sent hit's server-side cooldown is believed to still be running. */
    public static final int BLOCK_SERVER_COOLDOWN = 1 << 3;

    /** The burst window predicted from the attacker's own hit is running its pause. */
    public static final int BLOCK_PREDICTED_BURST = 1 << 4;

    /** The attacker cannot turn this ground hit into a critical and a crit was expected. */
    public static final int BLOCK_CRITICALS = 1 << 5;

    /**
     * Per-opponent state, the port of Raven's {@code TargetState}: plain numbers, ticked by the
     * module, read by the decision.
     */
    public static final class TargetState {
        /** Self damage was taken while this opponent was tracked; the exchange has begun. */
        public boolean firstSelfHitSeen;

        /** Tick of the hurt-time growth that confirmed the last sent hit, or -1. */
        public int lastConfirmedTargetDamageTick = -1;

        /** Tick of the last sent hit that still awaits server confirmation, or -1. */
        public int pendingServerConfirmationTick = -1;

        /** Tick the burst window predicted from our own hit starts, or -1. */
        public int predictedBurstWindowStartTick = -1;

        /** Tick the burst window ends, exclusive, or -1. */
        public int predictedBurstWindowEndTick = -1;

        /** The target's hurt time as last observed, for server-confirmation timing. */
        public int lastObservedTargetHurtTime;

        /** Tick the currently sticky block mask started, or -1. */
        public int rawBlockStartTick = -1;

        /** The currently sticky block mask. */
        public int rawBlockMask;
    }

    /** Milliseconds rounded up to whole ticks; zero and below stay zero. */
    public static int msToTicks(double ms) {
        if (ms <= 0.0) {
            return 0;
        }
        return (int) Math.ceil(ms / 50.0);
    }

    /** Squared distance from an eye position to the closest point of an axis-aligned box. */
    public static double eyeToBoxSqr(double eyeX, double eyeY, double eyeZ,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.max(minX - eyeX, 0.0), eyeX - maxX);
        double dy = Math.max(Math.max(minY - eyeY, 0.0), eyeY - maxY);
        double dz = Math.max(Math.max(minZ - eyeZ, 0.0), eyeZ - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Whether the burst window predicted from our own hit is still running. */
    public static boolean isPredictedBurstWindowActive(TargetState state, int currentTick) {
        return state.predictedBurstWindowEndTick >= 0 && currentTick < state.predictedBurstWindowEndTick;
    }

    /** Starts the ten-tick burst window at {@code startTick}, as a passed hit does. */
    public static void startPredictedBurstWindow(TargetState state, int startTick) {
        state.predictedBurstWindowStartTick = startTick;
        state.predictedBurstWindowEndTick = startTick + HURT_WINDOW_TICKS;
    }

    /** Clears the predicted burst window. */
    public static void clearPredictedBurstWindow(TargetState state) {
        state.predictedBurstWindowStartTick = -1;
        state.predictedBurstWindowEndTick = -1;
    }

    /** A passed hit ticked down past the window's end drops it. */
    public static void tickWindow(TargetState state, int currentTick) {
        if (state.predictedBurstWindowEndTick >= 0 && currentTick >= state.predictedBurstWindowEndTick) {
            clearPredictedBurstWindow(state);
        }
    }

    /**
     * The square-root-free check that the box lies inside {@code maxDistanceSq} of the eye, used to
     * accept attack targets at Raven's three-block reach.
     */
    public static boolean withinHitRange(double eyeX, double eyeY, double eyeZ,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ,
                                         double maxDistanceSq) {
        return eyeToBoxSqr(eyeX, eyeY, eyeZ, minX, minY, minZ, maxX, maxY, maxZ) <= maxDistanceSq;
    }

    /**
     * The reasons the click would be wasted inside the predicted burst window, in the server-timing
     * mode instead the cooldown the server confirmed, so the caller can hold the pause.
     *
     * <p>With server timing, a confirmed damage at {@code lastConfirmed} blocks for the hurt
     * window's length. Without it, the predicted burst window blocks for the caller's pause length
     * measured from the window's own start.</p>
     */
    public static int burstBlockMask(TargetState state, int currentTick,
                                     boolean useServerAttackTime, double pauseMs) {
        if (useServerAttackTime) {
            if (state.lastConfirmedTargetDamageTick >= 0
                && currentTick - state.lastConfirmedTargetDamageTick < HURT_WINDOW_TICKS) {
                return BLOCK_SERVER_COOLDOWN;
            }
            return NO_BLOCK;
        }

        if (!isPredictedBurstWindowActive(state, currentTick)) {
            return NO_BLOCK;
        }

        int pauseTicks = msToTicks(pauseMs);
        return pauseTicks > 0 && currentTick - state.predictedBurstWindowStartTick < pauseTicks
            ? BLOCK_PREDICTED_BURST
            : NO_BLOCK;
    }

    /**
     * The sticky pause: a non-zero block mask is held for the pause duration from the tick the mask
     * itself started, and a mask that changes restarts the clock. A clear mask resets the stickiness
     * and passes unconditionally, so the pause cannot latch shutdowns.
     *
     * <p>This is Raven's {@code applyPauseDuration} verbatim, down to the ordering quirk that the
     * caller subtracts the predicted-burst bit before passing the mask in - the burst blocks on its
     * own window, the sticky pause reaches the other reasons.</p>
     *
     * @return whether the click is still held
     */
    public static boolean applyPauseDuration(TargetState state, int blockMask, int currentTick, double pauseMs) {
        if (blockMask == NO_BLOCK) {
            state.rawBlockMask = NO_BLOCK;
            state.rawBlockStartTick = -1;
            return false;
        }

        if (pauseMs <= 0.0) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartTick = currentTick;
            return false;
        }

        if (blockMask != state.rawBlockMask) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartTick = currentTick;
        } else if (state.rawBlockStartTick < 0) {
            state.rawBlockStartTick = currentTick;
        }

        int requiredTicks = msToTicks(pauseMs);
        return requiredTicks > 0 && currentTick - state.rawBlockStartTick < requiredTicks;
    }

    /**
     * Whether the first hit of the exchange is still being waited for: only while tracking a fresh
     * opponent whose tracking has never been unlocked, within the wait duration.
     */
    public static boolean waitingForFirstHit(double waitFirstMs, boolean tracking, boolean unlocked,
                                             int startTick, int currentTick) {
        if (waitFirstMs <= 0.0 || !tracking || unlocked || startTick < 0) {
            return false;
        }

        int requiredTicks = msToTicks(waitFirstMs);
        return requiredTicks > 0 && currentTick - startTick < requiredTicks;
    }

    /**
     * Whether the click cannot become a critical: airborne with a positive fall distance, off
     * climbables, water, blindness and rides alike - a landed-click crit test, since a
     * critical-expecting click on the ground is just a normal hit and passes.
     */
    public static boolean canCriticalHit(double fallDistance, boolean onGround, boolean onClimbable,
                                         boolean inWater, boolean blind, boolean riding) {
        return fallDistance > 0.0
            && !onGround
            && !onClimbable
            && !inWater
            && !blind
            && !riding;
    }

    /**
     * The criticals gate, in the Criticals mode only: while airborne, a click that cannot become a
     * critical (and in the damaged-only option, from an exchange that has never damaged the caller)
     * is held, unless knockback is exempting the click.
     */
    public static boolean criticalsBlocked(boolean criticalsMode, boolean selfOnGround,
                                           boolean onlyWhileDamaged, boolean firstSelfHitSeen,
                                           boolean knockbackExempts, boolean canCriticalHit) {
        if (!criticalsMode) {
            return false;
        }

        if (selfOnGround) {
            return false;
        }

        if (onlyWhileDamaged && !firstSelfHitSeen) {
            return false;
        }

        if (knockbackExempts) {
            return false;
        }

        return !canCriticalHit;
    }
}
