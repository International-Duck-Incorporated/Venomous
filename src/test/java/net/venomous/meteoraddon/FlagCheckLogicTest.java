package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the decision that separates an anticheat setback from a teleport reply.
 *
 * <p>{@code FlagCheck} listens for a ClientboundPlayerPositionPacket and has to decide whether
 * it represents the server pushing us back - a flag - or simply answering a teleport we asked
 * for. The two packets are identical in shape, so the only thing separating them is whether an
 * accept-teleport request went out recently.</p>
 *
 * <p>The logic is modelled here in plain terms so it can be exercised without a world, a server
 * or a running client. This is a model, not the class under test: FlagCheck needs a live
 * Minecraft instance to receive packets at all. {@code FlagCheckStructureTest} separately pins
 * the real class to the same rules, so the two cannot drift apart silently.</p>
 */
public class FlagCheckLogicTest {
    /** Mirror of the grace window: ticks after requesting a teleport that a correction is expected. */
    private static final int GRACE_TICKS = 20;
    private static final long TICK_MS = 50L;
    private static final double MIN_DISPLACEMENT = 0.05;

    private long teleportRequestedAt = 0L;
    private int flags = 0;

    boolean onPositionCorrection(long now, double displacement, boolean consumeGrace) {
        if (teleportRequestedAt != 0L && now - teleportRequestedAt <= GRACE_TICKS * TICK_MS) {
            if (consumeGrace) teleportRequestedAt = 0L;
            return false;
        }

        if (displacement < MIN_DISPLACEMENT) return false;

        flags++;
        return true;
    }

    void onAcceptTeleportRequest(long now) {
        teleportRequestedAt = now;
    }

    @Test
    void anUnrequestedCorrectionIsAFlag() {
        assertTrue(onPositionCorrection(10_000L, 3.0, true),
            "a setback with no teleport request behind it must be flagged");
        assertEquals(1, flags);
    }

    @Test
    void aCorrectionInsideTheGraceWindowIsExpected() {
        onAcceptTeleportRequest(10_000L);

        assertFalse(onPositionCorrection(10_000L + GRACE_TICKS * TICK_MS, 40.0, true),
            "the reply to a teleport we requested is not a flag, however far it moved us");
        assertEquals(0, flags);
    }

    @Test
    void theGraceWindowExpires() {
        onAcceptTeleportRequest(10_000L);
        long tooLate = 10_000L + (GRACE_TICKS + 1) * TICK_MS;

        assertTrue(onPositionCorrection(tooLate, 3.0, true),
            "once the grace window has passed, a correction is a flag again");
    }

    @Test
    void graceIsConsumedSoItCannotCoverTwoCorrections() {
        onAcceptTeleportRequest(10_000L);

        assertFalse(onPositionCorrection(10_010L, 3.0, true), "first correction is the reply");
        assertTrue(onPositionCorrection(10_020L, 3.0, true),
            "a second correction inside the same window is not a reply to anything");
        assertEquals(1, flags);
    }

    @Test
    void subBlockNudgesAreNotFlags() {
        assertFalse(onPositionCorrection(10_000L, 0.04, true),
            "sub-block movement is ordinary chunk rounding, not a setback");
        assertEquals(0, flags);
    }

    @Test
    void theGraceWindowForgivesAnyDisplacement() {
        onAcceptTeleportRequest(10_000L);

        // This is the documented limitation, pinned deliberately. The window is keyed purely on
        // timing, so a genuine flag landing inside it is forgiven - including a large one. Making
        // it size-aware would need a threshold the protocol does not give us, and would risk
        // flagging the ordinary teleport replies to chorus fruit and similar.
        assertFalse(onPositionCorrection(10_005L, 100.0, true),
            "documented behaviour: grace is keyed on timing alone, not displacement size");
    }

    @Test
    void aSmallCorrectionWithNoRequestIsStillIgnored() {
        assertFalse(onPositionCorrection(10_000L, 0.01, true),
            "the displacement floor applies regardless of whether a teleport was requested");
        assertEquals(0, flags);
    }
}
