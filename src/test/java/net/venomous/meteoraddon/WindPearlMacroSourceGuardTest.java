package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the timing boundary between the macro's two deferred actions. */
class WindPearlMacroSourceGuardTest extends SourceGuard {
    private static final String MACRO =
        "src/main/java/net/venomous/meteoraddon/modules/combat/WindPearlMacro.java";

    @Test
    void theDelayStartsOnlyAfterThePearlIsActuallyThrown() {
        String activation = squeeze(code(methodBody(source(MACRO), "public void onActivate()")));
        requireAnchor(activation, "onActivate", "this.mc.gameMode.useItem", "this.pearlThrown = true;");

        assertTrue(activation.indexOf("this.mc.gameMode.useItem") >= 0
                && activation.indexOf("this.mc.gameMode.useItem") < activation.indexOf("this.pearlThrown=true;"),
            "The pearl-thrown latch must be set by the deferred action after the real useItem call");

        String tick = squeeze(code(methodBody(source(MACRO), "private void onTick(Pre event)")));
        requireAnchor(tick, "onTick", "this.throwingPearl", "this.pearlThrown", "this.tickCounter++");
        assertTrue(tick.contains("if(this.throwingPearl&&this.pearlThrown&&this.mc.player!=null)"),
            "The countdown must wait for the deferred throw; otherwise a short delay queues the "
                + "wind charge before the pearl and both can fire together");
    }
}