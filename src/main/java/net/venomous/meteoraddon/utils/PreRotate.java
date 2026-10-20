package net.venomous.meteoraddon.utils;

import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.phys.Vec3;

/**
 * Rotate, then act <em>before</em> the movement packet - and before the crouch.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Meteor's {@link Rotations#rotate(double, double, int, Runnable)} runs its callback from
 * {@code SendMovementPacketsEvent.Post} — that is, <em>after</em> the tick's position packet has
 * already been flushed. Any interaction performed from that callback therefore reaches the server
 * <em>after</em> the movement packet for the same tick.
 *
 * <p>A vanilla client always sends its interaction before its movement packet. The inverted ordering
 * is exactly what anticheats fingerprint and report as "attacking/placing on POST".
 *
 * <p>But before the movement packet is not early enough either. The player's tick sends the input
 * packet - the one that carries the crouch and the sprint - before it sends the movement packet,
 * so an action fired from the movement packet's own pre-phase lands <em>after</em> the crouch. Grim
 * reads that as an action sent inside the crouch, and cancels it. The action therefore goes out
 * at the head of the tick instead, which is the earliest point Meteor offers and still after the
 * mouse has been read.
 *
 * <p>This helper keeps Meteor's rotation handling (so the rotation itself is still spoofed and
 * smoothed normally) but defers the <em>action</em> to a following tick, once the server has
 * actually observed the rotation the action depends on.
 *
 * <h2>Usage</h2>
 *
 * Replace:
 * <pre>{@code Rotations.rotate(yaw, pitch, 10, () -> doThing());}</pre>
 * with:
 * <pre>{@code PreRotate.rotateThen(this, yaw, pitch, () -> doThing());}</pre>
 *
 * <p>Passing {@code this} ties the action to the module's lifetime: it is dropped if the module
 * is switched off before the rotation lands.</p>
 */
public final class PreRotate {
    /** How close the server-side rotation must be, in degrees, before the action fires. */
    private static final float TOLERANCE = 1.5F;

    /** Ticks a queued action may wait for its rotation before being discarded. */
    private static final int MAX_AGE = 5;

    private static final DeferredActions<Runnable> PENDING = new DeferredActions<>();
    private static boolean initialised = false;

    private PreRotate() {
    }

    /** Subscribes the dispatcher to Meteor's event bus. Idempotent. */
    public static void init() {
        if (!initialised && MeteorClient.EVENT_BUS != null) {
            MeteorClient.EVENT_BUS.subscribe(PreRotate.class);
            initialised = true;
        }
    }

    /**
     * Decides whether a queued action is still worth running.
     *
     * <p>A queued action outlives the tick that requested it by up to {@link #MAX_AGE} ticks. A
     * module switched off in that window would otherwise still perform its interaction - and
     * would do so against whatever state its teardown just cleared. Every call site must
     * supply one; there is deliberately no overload that omits it, so the compiler enforces it.</p>
     */
    @FunctionalInterface
    public interface Guard extends DeferredActions.Guard {
    }

    /** Convenience for module callers: the action is dropped unless {@code owner} is still on. */
    public static void rotateThen(Module owner, double yaw, double pitch, Runnable action) {
        rotateThen(owner, yaw, pitch, 10, action);
    }

    /** As above, with an explicit Meteor rotation priority. */
    public static void rotateThen(Module owner, double yaw, double pitch, int priority, Runnable action) {
        queue(owner, owner::isActive, yaw, pitch, priority, action, true, true);
    }

    /**
     * As {@link #rotateThen(Module, double, double, Runnable)}, but the action waits only for the
     * pitch to be sent.
     *
     * <p>For a caller whose yaw is the player's own. The yaw then changes as the player looks
     * around, and comparing the requested yaw with the last one sent measures the mouse rather
     * than the rotation: two degrees of mouse movement in a tick is enough to hold the action back,
     * and the action is discarded after five ticks - so the caller would act only while the player
     * happened to be holding the mouse still, which is the opposite of what a free camera is for.</p>
     *
     * <p>The yaw does not need waiting for either way. Whether the rotation the server holds is the
     * one the caller needs is the caller's own question, and it is better asked of the rotation the
     * server actually has - the scaffold asks it of the ray it traces.</p>
     */
    public static void rotateThenOnPitch(Module owner, double yaw, double pitch, Runnable action) {
        queue(owner, owner::isActive, yaw, pitch, 10, action, true, false);
    }

    /**
     * Appends to the caller's existing queue instead of superseding it.
     *
     * <p>For macros that deliberately queue a sequence of actions which must all run, such as
     * throwing an item and then using a charge. Both share one aim, so under the default
     * superseding behaviour the second request would silently discard the first.</p>
     */
    public static void rotateThenSequence(Module owner, double yaw, double pitch, Runnable action) {
        rotateThenSequence(owner, yaw, pitch, 10, action);
    }

    /** As above, with an explicit Meteor rotation priority. */
    public static void rotateThenSequence(Module owner, double yaw, double pitch, int priority, Runnable action) {
        queue(owner, owner::isActive, yaw, pitch, priority, action, false, true);
    }

    /**
     * General form, for callers that are not modules.
     *
     * <p>{@code owner} must be a stable identity for the caller - typically {@code this}. Do not
     * pass the guard as the owner: a method reference such as {@code this::isRunning} creates a
     * fresh instance on every call, so nothing would ever supersede anything.</p>
     */
    public static void rotateThen(Object owner, Guard guard, double yaw, double pitch, Runnable action) {
        rotateThen(owner, guard, yaw, pitch, 10, action);
    }

    /** As above, with an explicit Meteor rotation priority. */
    public static void rotateThen(Object owner, Guard guard, double yaw, double pitch, int priority, Runnable action) {
        queue(owner, guard, yaw, pitch, priority, action, true, true);
    }

    /**
     * Requests a rotation and runs {@code action} in the PRE phase once the server has seen it.
     *
     * @param guard    re-checked immediately before the action runs, outside the lock; a false
     *                 result discards the action
     * @param yaw      target yaw, degrees
     * @param pitch    target pitch, degrees
     * @param priority rotation priority handed to Meteor, higher wins contention
     * @param action   the interaction to perform; never run from POST
     */
    private static void queue(Object owner, Guard guard, double yaw, double pitch, int priority,
                               Runnable action, boolean replace, boolean yawMatters) {
        if (action == null) {
            Rotations.rotate(yaw, pitch, priority, null);
            return;
        }

        init();

        // Ask Meteor to perform the rotation, but deliberately pass no callback: running it would
        // put us back in the POST phase, which is the whole problem.
        Rotations.rotate(yaw, pitch, priority, null);

        PENDING.add(owner, guard, (float) yaw, (float) pitch, action, replace, yawMatters);
    }

    /**
     * Asks for the rotation without queuing an action to go with it.
     *
     * <p>For a caller that wants the server to be told an angle every tick but only acts
     * occasionally. Queueing an action on every tick would fill the queue, and a caller that asks
     * "is an action of mine waiting?" would then be told yes every tick - which is what stopped
     * the crouch from ever coming on.</p>
     */
    public static void rotateOnly(Module owner, double yaw, double pitch) {
        Rotations.rotate(yaw, pitch, 10, null);
    }

    /** Drops every queued action. Call from {@code onDeactivate}. */
    public static void cancelAll() {
        PENDING.clear();
    }

    /**
     * Whether {@code owner} has an action queued and still waiting to run.
     *
     * <p>It fires in the movement packet's PRE phase, which is after the player's own tick has
     * already sent whatever input packets that tick carries. A caller that must not share a tick
     * with its own queued action - a placement and the sneak input, say - asks this at the start of
     * the tick and holds off until the queue is empty again.</p>
     */
    public static boolean hasPending(Module owner) {
        return PENDING.has(owner);
    }

    /**
     * Fires the queued actions.
     *
     * <p>Called from the hook in the player's tick that sits between the camera write and the
     * input packet - see {@link #dispatch} for why that exact point matters.</p>
     */
    public static void dispatch() {
        if (PENDING.isEmpty()) {
            return;
        }

        List<DeferredActions.Pending<Runnable>> ready = PENDING.drain(PreRotate::isServerReady, MAX_AGE);

        // Run outside the lock: actions may themselves queue further rotations. The guard
        // is evaluated here, at the last possible moment, rather than when the action was
        // collected - a queued action may legitimately have been invalidated in between.
        for (DeferredActions.Pending<Runnable> pending : ready) {
            if (pending.guard() != null && !pending.guard().shouldStillRun()) continue;
            pending.action().run();
        }
    }

    /**
     * Fires queued actions ahead of everything the tick sends.
     *
     * <p>This is not called from the movement packet's pre-phase any more, and the reason is the
     * order of the packets a 26.2 client sends in one tick:</p>
     *
     * <pre>{@code
     *   [the camera write]  [the input packet: the crouch]  [the movement packet]
     * }</pre> *
     *
     * <p>So the movement packet's pre-phase is <em>after</em> the crouch has been sent, and an
     * action fired from there lands inside the crouch. Grim's {@code PacketOrderF} - "sent action
     * packets after sneak or sprint input in an invalid order" - flags and <em>cancels</em> any
     * action packet that arrives while the server still believes the player is sneaking. The
     * server believes it from the moment the sneak packet arrives until the next movement packet,
     * and in that order the placement falls inside the window. The live log has it:</p>
     *
     * <pre>{@code
     *   Grim > ItsEspress0 failed PacketOrderF x0.0 action=place, sprinting=false, sneaking=true
     * }</pre>
     *
     * <p>and a cancelled placement is worse than a flag: the client is left standing on a block
     * the server never received, and the two disagree about where the player is by tenths of a
     * block.</p>
     *
     * <p>On a server older than the client the same thing happens one step further down. The
     * 1.21.2 input packet is translated into the old protocol's "entity action" packet, and Grim
     * reads the crouch from that - so the placement still has to leave before it.</p>
     *
     * <p>The hook is therefore the one point that is after the camera write - the camera has to
     * be the aim when the placement is made, because the interaction is validated against it -
     * and before the input packet. The rotation is unaffected: the action waits for the rotation
     * the server already holds, and the server holds the rotation from the previous tick's
     * movement packet, which is the one the aim was worked out for.</p>
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onTickPre(TickEvent.Pre event) {
        // Safety net: never let an action survive across a disconnect or a world change.
        if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) {
            cancelAll();
        }
    }

    /** True once the rotation the server last received matches what this action needs. */
    private static boolean isServerReady(DeferredActions.Pending<Runnable> pending) {
        float yawDiff = Math.abs(wrapDegrees(Rotations.serverYaw - pending.yaw()));
        float pitchDiff = Math.abs(Rotations.serverPitch - pending.pitch());
        return yawDiff <= TOLERANCE && pitchDiff <= TOLERANCE;
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
