package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

/** Guards restoration of the exact boat that BoatPhase modified. */
class BoatPhaseSourceGuardTest extends SourceGuard {
    private static final String MODULE =
        "src/main/java/net/venomous/meteoraddon/modules/movement/BoatPhase.java";

    @Test
    void changingOrLeavingTheBoatRestoresThePreviousBoat() {
        String tick = squeeze(code(methodBody(source(MODULE), "private void onTick(Pre event)")));
        requireAnchor(tick, "onTick", "if (this.phasedBoat != current)",
            "this.phasedBoat.noPhysics = false", "this.phasedBoat = current");

        String deactivate = squeeze(code(methodBody(source(MODULE), "public void onDeactivate()")));
        requireAnchor(deactivate, "onDeactivate", "if (this.phasedBoat != null)",
            "this.phasedBoat.noPhysics = false", "this.phasedBoat = null");
    }
}