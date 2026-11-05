package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Guards the per-spectator meaning of SpectatorAlert's cooldown setting. */
class SpectatorAlertSourceGuardTest extends SourceGuard {
    private static final String MODULE =
        "src/main/java/net/venomous/meteoraddon/modules/detection/SpectatorAlert.java";

    @Test
    void cooldownExpiresEachSpectatorFromTheirOwnAlertTime() {
        String body = squeeze(code(methodBody(source(MODULE), "private void onTick(Pre event)")));

        requireAnchor(body, "onTick", "this.alerted.entrySet().removeIf",
            "this.timer - entry.getValue() >= cooldownTicks",
            "this.alerted.putIfAbsent(player.getUUID(), this.timer)");
        assertFalse(body.contains("this.alerted.clear()"),
            "A global reset lets a spectator alert again one tick after their previous alert");
    }
}