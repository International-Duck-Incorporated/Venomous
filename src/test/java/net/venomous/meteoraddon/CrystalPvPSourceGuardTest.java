package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards CrystalPvP's entity filter at the attack call site. */
class CrystalPvPSourceGuardTest extends SourceGuard {
    private static final String MODULE =
        "src/main/java/net/venomous/meteoraddon/modules/combat/CrystalPvP.java";

    @Test
    void theBreakMacroAttacksOnlyEndCrystals() {
        String body = squeeze(code(methodBody(source(MODULE),
            "private void handleEntityInteraction(EntityHitResult entityHitResult)")));

        requireAnchor(body, "handleEntityInteraction", "entityHitResult.getEntity()",
            "if (!(entity instanceof EndCrystal)) return;", "this.mc.gameMode.attack");

        int filter = body.indexOf("if(!(entityinstanceofEndCrystal))return;");
        int attack = body.indexOf("this.mc.gameMode.attack");
        assertTrue(filter >= 0 && attack > filter,
            "Every entity attack must be dominated by the EndCrystal type check");
    }
}