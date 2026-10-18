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
        private int age;

        Pending(Object owner, Guard guard, float yaw, float pitch, A action) {
            this.owner = owner;
            this.guard = guard;
            this.yaw = yaw;
            this.pitch = pitch;
            this.action = action;
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
        if (replace) {
            this.pending.removeIf(entry -> entry.owner == owner);
        }

        this.pending.add(new Pending<>(owner, guard, yaw, pitch, action));
    }

    public synchronized boolean isEmpty() {
        return this.pending.isEmpty();
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
