package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Guards against acknowledging the same server teleport twice. */
class VBlinkSourceGuardTest extends SourceGuard {
    @Test
    void vanillaRemainsTheOnlyTeleportConfirmationSender() {
        String body = code(source(
            "src/main/java/net/venomous/meteoraddon/modules/movement/VBlink.java"));

        assertFalse(body.contains("ServerboundAcceptTeleportationPacket"),
            "Meteor posts Receive before vanilla handles ClientboundPlayerPositionPacket; "
                + "manually accepting it here makes vanilla send a second confirmation");
    }
}