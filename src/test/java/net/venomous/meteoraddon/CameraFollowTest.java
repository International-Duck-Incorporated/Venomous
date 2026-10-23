package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the camera-follow wiring of {@code v-scaffold}.
 *
 * <p>The server's yaw must be the camera's own yaw on <em>every</em> tick the player is moving,
 * not only on the ticks the staging loop had a target to aim at. The ticks the loop early-returns
 * on - no supporting face for the cell (mid-jump), no block in the hotbar, no cell underfoot -
 * used to skip {@code Rotations.rotate()} entirely, and Meteor's rotation hold then re-sent the
 * last staged angle for up to {@code rotation-hold} ticks (default 4) while writing the player's
 * rotation to that old angle for each packet. The position delta in those packets was integrated
 * by physics that ran on the live camera, so every degree the mouse moved was a walk the server
 * did not predict, and Grim's {@code Simulation} reported it at a millimetre: 119 flags in the
 * 7.17.1 live run, in decaying clusters that restarted with each staging gap.</p>
 *
 * <p>The fix re-stages the rotation on those ticks with the camera's yaw read fresh, the held aim
 * pitch and its wobble. Asserted on source, because "the refresh happens on exactly the ticks the
 * staging loop skips" is a property of the call sites and has no behavioural seam. See
 * {@link SourceGuard} for why that is done defensively.</p>
 */
public class CameraFollowTest extends SourceGuard {
    private static final String SCAFFOLD =
        "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java";
    private static final String PREROTATE =
        "src/main/java/net/venomous/meteoraddon/utils/PreRotate.java";

    /**
     * Every tick that early-returns out of its technique's staging loop must refresh the staged
     * rotation before moving on.
     */
    @Test
    void everyNoTargetTickRefreshesTheStagedRotation() {
        // code() strips the inline comments, because a squeezed comment's words would otherwise
        // glue themselves into the middle of every anchor.
        String grim = squeeze(code(methodBody(source(SCAFFOLD), "private void tickGrim()")));
        requireAnchor(grim, "tickGrim", "this.releaseManagedSneak();", "this.followCamera();");
        assertTrue(grim.contains("this.releaseManagedSneak();this.followCamera();return;"),
            "tickGrim's targetless tick is exactly when a staged rotation would outlive the tick "
                + "that staged it, so that tick must refresh before returning");

        String standard = squeeze(code(methodBody(source(SCAFFOLD), "private void tickStandardMode()")));
        requireAnchor(standard, "tickStandardMode", "this.releaseManagedSneak();", "this.followCamera();");
        assertTrue(standard.contains("this.releaseManagedSneak();this.followCamera();return;"),
            "tickStandardMode's targetless must not leave the server holding a yaw from a "
                + "previous tick against a walk this tick performed");
    }

    /**
     * The gate inside {@code aimAndPlace} - the mid-jump "no supporting face for the cell" tick -
     * must refresh too, not only the earliest returns in the technique methods.
     */
    @Test
    void theAimGateReturnsRefreshAsWell() {
        String aim = squeeze(code(methodBody(source(SCAFFOLD), "private void aimAndPlace(BlockPos pos)")));

        requireAnchor(aim, "aimAndPlace", "this.followCamera();");

        assertTrue(aim.contains("if(side==null||!BlockUtils.canPlace(pos)){this.followCamera();return;}"),
            "a tick with no placeable face is the archetypal mid-jump gap; it must not leave the "
                + "staged rotation to be held stale by Meteor for the packets of that tick");
    }

    /**
     * The refresh itself is a re-stage of the camera's own yaw - never a camera write, never a
     * smoothed or derived yaw, and it does not mutate the queue.
     */
    @Test
    void theRefreshIsTheCamerasOwnYawAndTouchesNoCamera() {
        String body = squeeze(code(methodBody(source(SCAFFOLD), "private void followCamera()")));

        requireAnchor(body, "followCamera", "PreRotate.followCameraYaw", "getYRot", "this.rotate.get()");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("this.mc.player.getYRot()"),
            "the refreshed yaw is read straight off the camera, freshly, in the tick whose walk "
                + "it has to agree with");

        assertTrue(squeezed.contains("this.rotate.get()"),
            "the follow only runs while the module is spoofing at all");

        String code = code(source(SCAFFOLD));
        assertFalse(code.contains("setYRot("),
            "the camera is the player's; the follow re-stages what the server is told, which is "
                + "a different thing entirely");
        assertFalse(code.contains("setXRot("),
            "same, for the pitch: the refresh stages the held aim pitch, it does not move the view");
    }

    /**
     * The hold pitch carries the wobble, so a still mouse and a held aim cannot repeat the exact
     * same rotation for {@code AimDuplicateLook}.
     */
    @Test
    void theRefreshedPitchStillCarriesTheWobble() {
        String body = squeeze(code(methodBody(source(SCAFFOLD), "private void followCamera()")));

        requireAnchor(body, "followCamera", "PITCH_WOBBLE", "Math.sin(this.scaffoldCount",
            "this.stagedPitch");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("Math.sin(this.scaffoldCount"),
            "the wobble is a sine rather than a random number, so the deltas stay smooth");

        assertTrue(squeezed.contains("this.stagedPitch"),
            "the held pitch is the one the last staged aim already had, not a fresh computation");
    }

    /**
     * PreRotate's readiness decision must honour the yawMatters flag it records.
     *
     * <p>Its own javadoc promises that a caller whose yaw is the player's own waits only for the
     * pitch. Ignoring the flag made a moving mouse hold every placement back a tick - and the
     * flag was already there, which is what makes the bug worth a guard of its own: the contract
     * existed and was silently dropped.</p>
     */
    @Test
    void theReadinessDecisionHonoursYawMatters() {
        String body = methodBody(source(PREROTATE),
            "private static boolean isServerReady(DeferredActions.Pending<Runnable> pending)");

        requireAnchor(body, "isServerReady", "yawMatters", "RotationReadiness.isReady",
            "Rotations.serverYaw");

        assertTrue(squeeze(body).contains("pending.yawMatters()"),
            "the flag says whether the yaw has to have landed; a caller whose yaw is the player's "
                + "own owns that yaw and does not wait for it");
    }

    /**
     * The refresh helper stages only what the server is told, and touches nothing else.
     *
     * <p>It must not mutate the queue - the staged entry keeps the aim it is waiting for and its
     * age-out clock keeps running - and it must exist under the same no-guard commitment as the
     * rotateThen family, which is why this test pins the shape instead of the overload policy.</p>
     */
    @Test
    void theRefreshHelperIsAReStageAndNothingMore() {
        String body = methodBody(source(PREROTATE),
            "public static void followCameraYaw(Module owner, double cameraYaw, double pitch)");

        requireAnchor(body, "followCameraYaw", "Rotations.rotate");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("Rotations.rotate(cameraYaw,pitch,10,null)"),
            "the helper is one re-stage of what the server is told, at the same priority as the "
                + "caller's own staging so neither can outrank the other within a tick");

        assertFalse(squeezed.contains("PENDING"),
            "the queue is the staged action's own idea of its aim; the refresh must neither add "
                + "to it (that would reset its age-out clock) nor remove from it");
    }
}
