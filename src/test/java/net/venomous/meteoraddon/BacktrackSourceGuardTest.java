package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the position-stream state that BacktrackMath's geometry depends on. */
class BacktrackSourceGuardTest extends SourceGuard {
    private static final String BACKTRACK =
        "src/main/java/net/venomous/meteoraddon/modules/network/Backtrack.java";

    @Test
    void everyMovementPacketAdvancesTheTrackedStreamPosition() {
        String body = squeeze(code(methodBody(source(BACKTRACK),
            "private boolean handleTargetMove(ClientboundMoveEntityPacket move)")));

        requireAnchor(body, "handleTargetMove", "double[] next =", "this.heldPos = next;",
            "if (worthHolding)");

        int update = body.indexOf("this.heldPos=next;");
        int holdBranch = body.indexOf("if(worthHolding){");

        assertTrue(update >= 0 && holdBranch >= 0,
            "The guard's position-update or hold-branch anchor no longer exists");
        assertTrue(update < holdBranch,
            "The stream position must advance before deciding whether to hold. Updating only in "
                + "the hold branch leaves passed-through movement out of all later deltas");
    }
}