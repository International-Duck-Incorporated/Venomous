package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the placement-targeting rules of {@code v-scaffold}.
 *
 * <p>Everything here came out of a live run against anticheat-test.com rather than from theory. The
 * instrumentation was one tagged line per tick; it printed {@code side=NULL canPlace=true} on exactly
 * the ticks Grim reported {@code AirLiquidPlace}, and printed the same line ten times in a row on
 * cells that could never be filled.</p>
 *
 * <p>Two separate mistakes were involved and both are asserted against, because fixing either one
 * alone leaves the other in place:</p>
 *
 * <ul>
 *   <li>the check was {@code canPlace}, which asks whether the cell is <em>free</em> rather than
 *       whether anything can hold a block; and</li>
 *   <li>{@code getPlacePos} handed back those dead cells, so the module sat on one forever instead
 *       of moving to the next candidate.</li>
 * </ul>
 *
 * <p>Asserts on source because "never targets a cell with no support" is a property of the wiring
 * rather than of any value that can be observed from a unit test - the decision needs a world. See
 * {@link SourceGuard} for how and why that is done.</p>
 */
public class ScaffoldSupportTest extends SourceGuard {
    private static final String SCAFFOLD =
        "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java";

    /**
     * The targeting itself.
     *
     * <p>Only the cell underfoot is a candidate, and only when it can hold a block. The sideways
     * candidates that used to be here were the expand technique - asked for removal once and left
     * in - and they are what Grim's {@code PositionPlace} ("Placed a block against a hidden face")
     * fires on, because placing the cell beside the bridge means clicking the support's far face
     * while standing on the near side of it.</p>
     */
    @Test
    void theTargetSelectorOnlyReturnsTheCellUnderfoot() {
        String body = squeeze(methodBody(source(SCAFFOLD), "private BlockPos getPlacePos()"));

        requireAnchor(body, "getPlacePos", "isSupported");

        assertTrue(body.contains("BlockPos.containing(this.mc.player.getX(),y,this.mc.player.getZ())"),
            "the target is the cell directly beneath the player");

        assertTrue(matches("returnthis\\.isSupported\\(below\\)\\?below:null;", body),
            "and it is returned only when it has a supporting face, otherwise null so the caller "
                + "does nothing this tick");

        assertFalse(body.contains(".isAir()"),
            "isAir() is the check that started all of this: a free cell can still have nothing to "
                + "place against, and the cell has to be screened on having a support face");
    }

    /**
     * The sideways candidates must not come back.
     *
     * <p>This is the expand technique. It is worth a guard of its own because it is invisible: the
     * method still compiles, still returns a {@code BlockPos}, and still places blocks - it just
     * places them where the player cannot see the face being clicked, which is a silent flag rather
     * than a broken module.</p>
     */
    @Test
    void theExpandTechniqueIsNotReintroducedIntoTargeting() {
        String body = squeeze(methodBody(source(SCAFFOLD), "private BlockPos getPlacePos()"));

        assertFalse(body.contains("Direction.NORTH") || body.contains("Direction.EAST"),
            "sweeping the four horizontal neighbours is the expand technique; it places a cell whose "
                + "clicked face is hidden from the eye, which is Grim's PositionPlace");

        assertFalse(body.contains(".relative("),
            "no candidate may be offset from the cell underfoot");
    }

    /**
     * The definition of the screen. {@code canPlace} is necessary but not sufficient: it is true for
     * a free cell with no adjacent support, which is precisely the air-place case.
     */
    @Test
    void theSupportScreenRequiresBothAFreeCellAndAPlaceSide() {
        String body = squeeze(methodBody(source(SCAFFOLD), "private boolean isSupported(BlockPos pos)"));

        requireAnchor(body, "isSupported", "ScaffoldAim.hasSupport");

        assertTrue(body.contains(
                "ScaffoldAim.hasSupport(BlockUtils.canPlace(pos),BlockUtils.getPlaceSide(pos)!=null)"),
            "support means canPlace AND a resolved place side; canPlace on its own cannot tell a "
                + "placeable cell from a free cell with nothing around it to build on");
    }

    /**
     * The click itself is screened at the choke point too, so the rule holds for every caller -
     * {@code aimAndPlace}, {@code doTower}, or anything added later.
     */
    @Test
    void theClickIsScreenedAtTheChokePointNotOnlyAtTheCallers() {
        String body = methodBody(source(SCAFFOLD), "private boolean place(BlockPos pos)");

        requireAnchor(body, "place", "isSupported");

        assertTrue(squeeze(body).contains("if(this.isSupported(pos))"),
            "place() is where the interaction actually leaves the client, so this is the last point "
                + "at which a cell with no support can be refused");
    }

    /**
     * The rotation is not computed for a cell that will be refused anyway. Aiming at a dead target
     * means the camera sweeps toward it and the server rotation chases it, all to send nothing.
     */
    @Test
    void theRotationIsNotWorkedOutForACellThatWillBeRefused() {
        String body = methodBody(source(SCAFFOLD), "private void aimAndPlace(BlockPos pos)");

        requireAnchor(body, "aimAndPlace", "getPlaceSide");

        int guard = squeeze(body).indexOf("Directionside=BlockUtils.getPlaceSide(pos);");
        int face = squeeze(body).indexOf("float[]face=this.aimRotation(pos);");

        assertTrue(guard >= 0 && face > guard,
            "the cell has to be resolved - and refused if it has no support - before the aim is "
                + "worked out, not after it");
    }

    /**
     * The bridge level must hold while the player is in the air.
     *
     * <p>It used to follow the jump key, and that is half of why jumping while bridging dropped
     * the player off the bridge: the module built a column upwards instead of the next cell of the
     * row, so the row stopped growing while the player kept walking backwards along it. The live
     * log caught it - the target's Y climbed 83, 84, 85, 87 while the player was airborne.</p>
     */
    @Test
    void theBridgeLevelHoldsWhileThePlayerIsInTheAir() {
        String code = squeeze(code(source(SCAFFOLD)));

        assertFalse(code.contains("keyJump.isDown()"),
            "the jump key must not raise the level of the row; the module would build a column "
                + "instead of the bridge and the player would walk off the end of the row");

        assertTrue(squeeze(code).contains("this.mc.player.onGround()"),
            "the level follows the player while they are on the ground");

        assertTrue(squeeze(code).contains("!this.sameY.get()"),
            "and follows them in the air only when the player has asked for that behaviour");
    }

    /**
     * The instrumentation that found all of this is gone. It is tagged {@code DEBUG-scf} for exactly
     * this reason: one grep should be enough to prove nothing was left behind.
     */
    @Test
    void theTemporaryScaffoldInstrumentationIsGone() {
        String code = code(source(SCAFFOLD));

        assertTrue(code.contains("DEBUG-scf"),
            "the log lines are tagged, so one grep finds every one of them and nothing else in the "
                + "file can be mistaken for a probe");

        assertTrue(code.contains("if (!this.debugLogging.get()) return;"),
            "and they are behind a setting: a per-tick log was measured slowing the client down, so "
                + "it must be possible to turn it off and it must not be on by default in a build "
                + "that is meant to be played");

        assertFalse(code.contains("debugState("),
            "the debugState helper should have been deleted with the call site that used it");
    }
}