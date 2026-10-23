package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.DeferredActions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the queueing rules behind {@link net.venomous.meteoraddon.utils.PreRotate}.
 *
 * <p>PreRotate used to hold a bare list, so a module that re-requested its action every tick
 * while the rotation was settling piled up one entry per tick. Once the rotation landed, every
 * copy fired in the same tick - for v-auto-city that meant repeated START/STOP destroy packets
 * for a single block.</p>
 */
public class DeferredActionsTest {
    private static final DeferredActions.Guard ALWAYS = () -> true;
    private static final int MAX_AGE = 5;

    private static List<String> drain(DeferredActions<String> queue, boolean ready) {
        List<String> fired = new ArrayList<>();
        for (DeferredActions.Pending<String> entry : queue.drain(e -> ready, MAX_AGE)) {
            if (entry.guard() != null && !entry.guard().shouldStillRun()) continue;
            fired.add(entry.action());
        }
        return fired;
    }

    @Test
    void repeatedRequestsFromOneCallerCollapseToOne() {
        DeferredActions<String> queue = new DeferredActions<>();
        Object owner = new Object();

        // Five ticks of re-requesting, as v-auto-city does while its rotation settles.
        for (int i = 0; i < 5; i++) {
            queue.add(owner, ALWAYS, 0.0F, 0.0F, "mine", true);
        }

        assertEquals(1, queue.size(), "one caller re-asking must not build a backlog");
        assertEquals(List.of("mine"), drain(queue, true), "only the surviving action should fire");
    }

    @Test
    void newestRequestWins() {
        DeferredActions<String> queue = new DeferredActions<>();
        Object owner = new Object();

        queue.add(owner, ALWAYS, 0.0F, 0.0F, "stale", true);
        queue.add(owner, ALWAYS, 0.0F, 0.0F, "current", true);

        assertEquals(List.of("current"), drain(queue, true),
            "superseding must keep the newest intent, not the first one requested");
    }

    @Test
    void differentCallersAreIndependent() {
        DeferredActions<String> queue = new DeferredActions<>();
        Object city = new Object();
        Object farm = new Object();

        queue.add(city, ALWAYS, 0.0F, 0.0F, "city", true);
        queue.add(farm, ALWAYS, 0.0F, 0.0F, "farm", true);

        assertEquals(2, queue.size(), "one caller's action must not discard another's");
        assertEquals(List.of("city", "farm"), drain(queue, true), "queue order must be preserved");
    }

    @Test
    void sequenceModeKeepsEveryEntry() {
        DeferredActions<String> queue = new DeferredActions<>();
        Object macro = new Object();

        // pearl-wind-macro queues a throw then a charge on the same aim; both must survive.
        queue.add(macro, ALWAYS, 0.0F, 0.0F, "throw-pearl", false);
        queue.add(macro, ALWAYS, 0.0F, 0.0F, "use-wind-charge", false);

        assertEquals(2, queue.size(), "sequence mode must append rather than supersede");
        assertEquals(List.of("throw-pearl", "use-wind-charge"), drain(queue, true),
            "a sequenced macro must run every step");
    }

    @Test
    void unreadyActionsAgeOutInsteadOfFiring() {
        DeferredActions<String> queue = new DeferredActions<>();
        queue.add(new Object(), ALWAYS, 0.0F, 0.0F, "stuck", true);

        // MAX_AGE + 1 ticks with the rotation never landing.
        for (int tick = 0; tick <= MAX_AGE; tick++) {
            List<String> fired = drain(queue, false);
            assertTrue(fired.isEmpty(), "an action whose rotation never landed must not fire");
        }

        assertTrue(queue.isEmpty(), "an aged-out action must eventually be discarded");
    }

    @Test
    void firedActionsAreRemoved() {
        DeferredActions<String> queue = new DeferredActions<>();
        queue.add(new Object(), ALWAYS, 0.0F, 0.0F, "once", true);

        assertEquals(List.of("once"), drain(queue, true));
        assertTrue(queue.isEmpty(), "a fired action must not be retained");
        assertTrue(drain(queue, true).isEmpty(), "a fired action must not fire twice");
    }

    @Test
    void aFailingGuardDiscardsTheAction() {
        DeferredActions<String> queue = new DeferredActions<>();
        queue.add(new Object(), () -> false, 0.0F, 0.0F, "should-not-run", true);

        assertTrue(drain(queue, true).isEmpty(), "a guard returning false must drop the action");
    }

    @Test
    void clearDropsEverything() {
        DeferredActions<String> queue = new DeferredActions<>();
        queue.add(new Object(), ALWAYS, 0.0F, 0.0F, "a", true);
        queue.add(new Object(), ALWAYS, 0.0F, 0.0F, "b", true);

        queue.clear();
        assertTrue(queue.isEmpty());
        assertTrue(drain(queue, true).isEmpty());
    }

    /** A caller type where two distinct instances compare equal, to prove identity is used. */
    private static final class Caller {
        private final String name;

        Caller(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Caller caller && caller.name.equals(this.name);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }
    }

    @Test
    void ownerIsComparedByIdentityNotEquality() {
        DeferredActions<String> queue = new DeferredActions<>();

        // Distinct instances that equals() considers the same caller. They are still two
        // different callers, so one must not silently discard the other's action.
        Caller first = new Caller("auto-city");
        Caller second = new Caller("auto-city");
        assertEquals(first, second, "precondition: these must be equal for the test to mean anything");

        queue.add(first, ALWAYS, 0.0F, 0.0F, "from-first", true);
        queue.add(second, ALWAYS, 0.0F, 0.0F, "from-second", true);

        assertEquals(2, queue.size(),
            "distinct callers must not supersede each other just because equals() says they match");
    }

    @Test
    void findReturnsTheNewestPendingForTheOwner() {
        DeferredActions<String> queue = new DeferredActions<>();
        Object owner = new Object();

        // Explicit 7-arg adds: the yawMatters flag travels with the entry, and here it is as a
        // camera-follow caller would record it.
        queue.add(owner, ALWAYS, 10.0F, 20.0F, "first", true, false);
        queue.add(owner, ALWAYS, 30.0F, 40.0F, "second", true, false);

        DeferredActions.Pending<String> found = queue.find(owner);

        assertNotNull(found, "a caller with a queued action has something to find");
        assertEquals(30.0F, found.yaw(), 0.0F, "the newest intent is what the caller waits on");
        assertEquals(40.0F, found.pitch(), 0.0F);
        assertEquals("second", found.action());
        assertFalse(found.yawMatters(), "the yawMatters flag was recorded with the entry");
    }

    @Test
    void findIsNullWhenTheOwnerHasNothingQueued() {
        DeferredActions<String> queue = new DeferredActions<>();
        Object owner = new Object();
        Object stranger = new Object();

        queue.add(owner, ALWAYS, 0.0F, 0.0F, "ours", true);

        assertNull(queue.find(stranger),
            "a caller with nothing queued must read as exactly that, and never as another "
                + "caller's entry");

        assertEquals("ours", queue.find(owner).action());
    }

    @Test
    void findingDoesNotFireOrAgeAnything() {
        DeferredActions<String> queue = new DeferredActions<>();
        Object owner = new Object();

        queue.add(owner, ALWAYS, 0.0F, 0.0F, "ours", true);
        queue.find(owner);

        assertEquals(1, queue.size(), "a look must not consume the entry");
        assertEquals(List.of("ours"), drain(queue, true), "and a looked-at entry still fires");
    }
}
