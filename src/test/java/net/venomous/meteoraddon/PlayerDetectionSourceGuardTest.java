package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards against reacting again to the entire player list on every membership change. */
class PlayerDetectionSourceGuardTest extends SourceGuard {
    private static final String MODULE =
        "src/main/java/net/venomous/meteoraddon/modules/detection/PlayerDetection.java";

    @Test
    void onlyNewlyDetectedPlayersTriggerReactions() {
        String body = squeeze(code(methodBody(source(MODULE), "private void onTick(Pre event)")));

        requireAnchor(body, "onTick", "newPlayers.removeAll(this.detectedPlayers)",
            "handlePlayerDetection(Set.copyOf(newPlayers))");
        assertTrue(body.contains("if(!newPlayers.isEmpty())"),
            "A departure or another membership change must not fire without a new arrival");
        assertFalse(body.contains("handlePlayerDetection(Set.copyOf(currentPlayers))"),
            "Existing players must not be reported and toggle their modules again");
    }
}