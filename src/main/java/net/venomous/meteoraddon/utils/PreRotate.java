package net.venomous.meteoraddon.utils;

import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.phys.Vec3;

/**
 * Rotate, then act <em>before</em> the movement packet.
 *
 * <h2>Why this exists</h2>
 *
 * Meteor's {@link Rotations#rotate(double, double, int, Runnable)} runs its callback from
 * {@code SendMovementPacketsEvent.Post} — that is, <em>after</em> the tick's position packet has
 * already been flushed. Any interaction performed from that callback therefore reaches the server
 * <em>after</em> the movement packet for the same tick.
 *
 * <p>A vanilla client always sends its interaction before its movement packet. The inverted ordering
 * is exactly what anticheats fingerprint and report as "attacking/placing on POST".
 *
 * <p>This helper keeps Meteor's rotation handling (so the rotation itself is still spoofed and
 * smoothed normally) but defers the <em>action</em> to {@code SendMovementPacketsEvent.Pre} of a
 * following tick, once the server has actually observed the rotation the action depends on.
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
        queue(owner, owner::isActive, yaw, pitch, priority, action, true);
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
        queue(owner, owner::isActive, yaw, pitch, priority, action, false);
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
        queue(owner, guard, yaw, pitch, priority, action, true);
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
    private static void queue(Object owner, Guard guard, double yaw, double pitch, int priority, Runnable action, boolean replace) {
        if (action == null) {
            Rotations.rotate(yaw, pitch, priority, null);
            return;
        }

        init();

        // Ask Meteor to perform the rotation, but deliberately pass no callback: running it would
        // put us back in the POST phase, which is the whole problem.
        Rotations.rotate(yaw, pitch, priority, null);

        PENDING.add(owner, guard, (float) yaw, (float) pitch, action, replace);
    }

    /** Drops every queued action. Call from {@code onDeactivate}. */
    public static void cancelAll() {
        PENDING.clear();
    }

    /**
     * Fires queued actions ahead of the movement packet.
     *
     * <p>Runs at {@code HIGHEST} priority so the interaction is written before anything else this
     * tick tries to send movement.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
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

    /** Safety net: never let actions survive across a disconnect or world change. */
    @EventHandler
    private static void onTick(TickEvent.Pre event) {
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
