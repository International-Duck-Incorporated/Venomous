package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards state that used to survive a module's mode or limiter being changed. */
class ModeTransitionStateTest extends SourceGuard {
    @Test
    void phaseRestoresPhysicsAndDrainsBlinkPacketsWhenLeavingModes() {
        String body = squeeze(code(methodBody(source(
            "src/main/java/net/venomous/meteoraddon/modules/movement/Phase.java"),
            "private void onTick(Pre event)")));

        requireAnchor(body, "Phase.onTick", "Phase.Mode activeMode = this.mode.get()",
            "this.mc.player.noPhysics = true", "this.noPhysicsManaged = true",
            "else if (this.noPhysicsManaged)", "this.mc.player.noPhysics = false",
            "activeMode != Phase.Mode.Blink && !this.blinkQueue.isEmpty()", "this.flush()");
    }

    @Test
    void clipDoesNotLeaveNoPhysicsEnabledAfterLeavingNoClipMode() {
        String body = squeeze(code(methodBody(source(
            "src/main/java/net/venomous/meteoraddon/modules/movement/Clip.java"),
            "private void onTick(Pre event)")));

        requireAnchor(body, "Clip.onTick", "this.mc.player.noPhysics = true",
            "this.noPhysicsManaged = true", "else if (this.noPhysicsManaged)",
            "this.mc.player.noPhysics = false", "this.noPhysicsManaged = false");
    }

    @Test
    void noFallDrainsBlinkPacketsWhenTheModeChanges() {
        String body = squeeze(code(methodBody(source(
            "src/main/java/net/venomous/meteoraddon/modules/player/NoFallPlus.java"),
            "private void onTick(Pre event)")));

        requireAnchor(body, "NoFallPlus.onTick",
            "this.mode.get() != NoFallPlus.Mode.Blink && !this.blinkQueue.isEmpty()",
            "this.flushBlink()");
    }

    @Test
    void disablingTheNetworkPacketLimiterAlsoDrainsItsQueue() {
        String body = squeeze(code(methodBody(source(
            "src/main/java/net/venomous/meteoraddon/modules/network/GlobalNetworkInterceptor.java"),
            "private void onTick(Pre pre)")));

        requireAnchor(body, "GlobalNetworkInterceptor.onTick", "if (!this.tickQueue.isEmpty())",
            "if (this.enforceTickOrder.get())", "this.flushQueue()");
        assertTrue(body.contains("else{this.flushQueue();}"),
            "Turning off the one-packet limit must flush instead of abandoning the old queue");
    }
}