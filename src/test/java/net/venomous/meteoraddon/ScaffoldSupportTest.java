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
     * The targeting itself. A candidate that is not {@link #isSupported} must be skipped, so the
     * module walks on to the next one instead of locking onto a cell it can never fill.
     */
    @Test
    void theTargetSelectorOnlyReturnsCellsThatCanHoldABlock() {
        String body = squeeze(methodBody(source(SCAFFOLD), "private BlockPos getPlacePos()"));

        requireAnchor(body, "getPlacePos", "isSupported");

        assertFalse(body.contains(".isAir())"),
            "isAir() is the check that started all of this: a free cell can still have nothing to "
                + "place against, and the cell has to be screened on having a support face");

        assertTrue(matches("if\\(this\\.isSupported\\(below\\)\\)", body),
            "the cell directly below the player must be screened on support, not on emptiness");

        assertTrue(matches("if\\(!this\\.isSupported\\(check\\)\\)", body),
            "the sideways candidates must be screened the same way, and skipped rather than "
                + "returned when they fail");
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

        requireAnchor(body, "aimAndPlace", "ScaffoldAim.hasSupport");

        int guard = squeeze(body).indexOf("ScaffoldAim.hasSupport(");
        int face = squeeze(body).indexOf("float[]face=this.faceAimAt(pos);");

        assertTrue(guard >= 0 && face > guard,
            "the support screen has to come before the aim is worked out, not after it");
    }

    /**
     * The instrumentation that found all of this is gone. It is tagged {@code DEBUG-scf} for exactly
     * this reason: one grep should be enough to prove nothing was left behind.
     */
    @Test
    void theTemporaryScaffoldInstrumentationIsGone() {
        String code = code(source(SCAFFOLD));

        assertFalse(code.contains("DEBUG-scf"),
            "the per-tick debug line is temporary and must not ship; it prints once per tick, every "
                + "tick, forever, for as long as the module is on");

        assertFalse(code.contains("debugState("),
            "the debugState helper should have been deleted with the call site that used it");
    }
}