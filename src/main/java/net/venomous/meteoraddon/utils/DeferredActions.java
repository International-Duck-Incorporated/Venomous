package net.venomous.meteoraddon.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pending deferred actions, keyed by the caller that requested them.
 *
 * <p>Deliberately free of any Minecraft types so the queueing rules can be unit tested on a
 * plain JVM. Deciding whether the server rotation has caught up stays with the caller, which
 * is passed in as a predicate.</p>
 *
 * <p>PreRotate used to hold a bare list, which meant a module that re-requested its action every
 * tick while the rotation was still settling accumulated one entry per tick. When the rotation
 * finally landed, every copy fired in the same tick - several identical START/STOP destroy
 * packets for one block. Superseding the caller's own earlier entries expresses "this is my
 * current intent" instead of building a backlog.</p>
 *
 * @param <A> the action type
 */
public final class DeferredActions<A> {
    /** Re-checked immediately before an action runs; a false result discards it. */
    @FunctionalInterface
    public interface Guard {
        boolean shouldStillRun();
    }

    /** One queued action, with the aim it was waiting for. */
    public static final class Pending<A> {
        private final Object owner;
        private final Guard guard;
        private final float yaw;
        private final float pitch;
        private final A action;
        private final boolean yawMatters;
        private int age;

        Pending(Object owner, Guard guard, float yaw, float pitch, A action, boolean yawMatters) {
            this.owner = owner;
            this.guard = guard;
            this.yaw = yaw;
            this.pitch = pitch;
            this.action = action;
            this.yawMatters = yawMatters;
        }

        /**
         * Whether the action has to wait for the yaw as well as the pitch.
         *
         * <p>False for a caller whose yaw is the player's own: that yaw changes as the player looks
         * around, so waiting for it measures the mouse rather than the rotation.</p>
         */
        public boolean yawMatters() {
            return this.yawMatters;
        }

        public float yaw() {
            return this.yaw;
        }

        public float pitch() {
            return this.pitch;
        }

        public A action() {
            return this.action;
        }

        public Guard guard() {
            return this.guard;
        }

        void age() {
            this.age++;
        }
    }

    private final List<Pending<A>> pending = new ArrayList<>();

    /**
     * Queues an action.
     *
     * @param owner   identity of the caller; used to supersede its own earlier entries
     * @param guard   checked immediately before the action runs, may be {@code null}
     * @param yaw     aim in degrees, reported back through {@link Pending#yaw()}
     * @param pitch   aim in degrees, reported back through {@link Pending#pitch()}
     * @param action  the action to run once the aim is reached
     * @param replace when true, this caller's earlier pending entries are discarded first.
     *                Pass false when the caller is deliberately queuing a sequence of actions
     *                that must all run, such as a macro throwing an item then using a charge.
     */
    public synchronized void add(Object owner, Guard guard, float yaw, float pitch, A action, boolean replace) {
        add(owner, guard, yaw, pitch, action, replace, true);
    }

    /**
     * Queues an action, with a choice about whether the yaw has to have landed.
     *
     * @param yawMatters false when the caller does not need the yaw to have been sent - because
     *                   the yaw is the player's own and moves with the mouse
     */
    public synchronized void add(Object owner, Guard guard, float yaw, float pitch, A action,
                                 boolean replace, boolean yawMatters) {
        if (replace) {
            this.pending.removeIf(entry -> entry.owner == owner);
        }

        this.pending.add(new Pending<>(owner, guard, yaw, pitch, action, yawMatters));
    }

    public synchronized boolean isEmpty() {
        return this.pending.isEmpty();
    }

    /**
     * Whether this caller has an action queued and still waiting to run.
     *
     * <p>Used to keep two packets that must not share a tick apart - a placement and the sneak
     * input that goes with it, say. The caller asks at the start of the tick, while the action
     * queued by the previous tick is still in the queue, and it fires at the end of this one.</p>
     *
     * @param owner identity of the caller, compared by reference as everywhere else here
     */
    public synchronized boolean has(Object owner) {
        for (Pending<A> entry : this.pending) {
            if (entry.owner == owner) return true;
        }

        return false;
    }

    /**
     * The newest action this caller has queued and still waiting to run, or {@code null}.
     *
     * <p>"Newest" and not "only": a caller in sequence mode queues several entries at once and
     * they all sit here until they fire, so the last one in queue order is its current intent.
     * Callers ask this for what their aim was when they queued - the yaw the rotation is being
     * held at and the pitch it is waiting for - without keeping their own copy of the aim.</p>
     *
     * @param owner identity of the caller, compared by reference as everywhere else here
     */
    public synchronized Pending<A> find(Object owner) {
        Pending<A> found = null;

        for (Pending<A> entry : this.pending) {
            if (entry.owner == owner) found = entry;
        }

        return found;
    }

    public synchronized int size() {
        return this.pending.size();
    }

    public synchronized void clear() {
        this.pending.clear();
    }

    /**
     * Removes and returns every entry whose aim has been reached, in the order it was queued.
     * Entries that never became ready are aged, and discarded once older than {@code maxAge}.
     *
     * @param isReady decides whether an entry's rotation has landed
     * @param maxAge  ticks an entry may wait before being discarded
     */
    public synchronized List<Pending<A>> drain(Predicate<Pending<A>> isReady, int maxAge) {
        if (this.pending.isEmpty()) {
            return List.of();
        }

        List<Pending<A>> ready = null;

        for (int i = this.pending.size() - 1; i >= 0; i--) {
            Pending<A> entry = this.pending.get(i);

            if (isReady.test(entry)) {
                if (ready == null) {
                    ready = new ArrayList<>(2);
                }

                ready.add(entry);
                this.pending.remove(i);
            } else if (++entry.age > maxAge) {
                this.pending.remove(i);
            }
        }

        return ready == null ? List.of() : reversed(ready);
    }

    /**
     * The loop above walks back to front so entries can be removed while iterating, which leaves
     * the survivors in reverse queue order. A sequenced macro depends on the real order - it
     * would throw the wind charge before the pearl it was supposed to follow.
     */
    private static <A> List<Pending<A>> reversed(List<Pending<A>> ready) {
        Collections.reverse(ready);
        return ready;
    }
}
