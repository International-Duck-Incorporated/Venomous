package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the packet ordering of every {@code v-scaffold} placement.
 *
 * <p>Meteor's {@code BlockUtils.place(pos, block, rotate = true, …)} calls
 * {@code Rotations.rotate(yaw, pitch, runnable)}, and Meteor runs that callback from
 * {@code SendMovementPacketsEvent.Post}. The interaction therefore leaves <em>after</em> the
 * movement packet for the same tick. A vanilla client always sends the interaction first.</p>
 *
 * <p>The anticheat names it outright, which is how this was found:</p>
 *
 * <pre>{@code
 *   [Anticheat-Test] >Looks like you are currently attacking/placing on POST,
 *                       this is a very common check on most anti-cheats
 * }</pre>
 *
 * <p>{@code PreRotate} exists to avoid exactly this, but only the GrimScaffold and NCP paths were
 * routed through it - every other path passed {@code rotate = true} straight to
 * {@code BlockUtils}. The overload has now been deleted, so a call site cannot reintroduce it.
 */
public class ScaffoldPlacementOrderTest {
    private static String scaffoldSource() throws IOException {
        return source("src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java");
    }

    private static String preRotateSource() throws IOException {
        return source("src/main/java/net/venomous/meteoraddon/utils/PreRotate.java");
    }

    private static String mixinSource(String relative) throws IOException {
        return source(relative);
    }

    /** Reads a source file from the project, walking up until it finds one. */
    private static String source(String relative) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            dir = dir.getParent();
        }

        throw new AssertionError("could not find " + relative + " above " + System.getProperty("user.dir"));
    }

    /** Strips comments so prose in a javadoc cannot satisfy or break a source assertion. */
    private static String code(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    @Test
    void blockUtilsIsNeverAskedToRotate() throws IOException {
        String code = code(scaffoldSource());

        // The argument list ends at the first ')' that is followed by the end of the statement -
        // a ';' for an expression, a '{' for an if. Matching on ');' alone was how this guard
        // passed for the wrong reason: it ran on to a ');' somewhere later in the method and only
        // matched because that happened to exist. The call is now an if-block, and the call has to
        // be found where it is rather than wherever a stray semicolon turned up.
        Matcher matcher = Pattern.compile("BlockUtils\\.place\\s*\\(([^;{}]*?)\\)\\s*[;{]").matcher(code);

        int calls = 0;
        while (matcher.find()) {
            calls++;

            // Third argument is the rotate flag. It must be a literal false.
            String[] args = matcher.group(1).split(",");
            assertTrue(args.length >= 3,
                "could not read the rotate argument from: BlockUtils.place(" + matcher.group(1) + ")");
            assertFalse(args[2].trim().startsWith("true"),
                "BlockUtils.place called with rotate=true at: " + matcher.group(1).trim()
                    + ". That routes placement through Rotations.rotate, whose callback runs on "
                    + "SendMovementPacketsEvent.Post, so the interaction leaves after the movement "
                    + "packet. Use aimAndPlace instead, which defers through PreRotate to Pre.");
        }

        assertTrue(calls > 0, "expected at least one BlockUtils.place call to check");
    }

    @Test
    void theRotatingOverloadOfPlaceIsGone() throws IOException {
        String code = code(scaffoldSource());

        assertFalse(code.contains("private boolean place(BlockPos pos, boolean"),
            "place(pos, shouldRotate) has been deleted. Re-adding it invites a call site to pass "
                + "rotate=true and put every placement back on POST.");
    }

    @Test
    void rotationAndPlaceGoesThroughPreRotate() throws IOException {
        String code = code(scaffoldSource());

        assertTrue(code.contains("PreRotate.rotateThenOnPitch(this, face[0], face[1], () -> {"),
            "aimAndPlace must defer the placement through PreRotate so it leaves before the "
                + "movement packet, not after it.");
    }

    /**
     * The placement has to leave before the crouch, not merely before the movement packet.
     *
     * <p>These are different points and the difference is the whole of Grim's
     * {@code PacketOrderF}. A 26.2 client sends a tick's packets like this:</p>
     *
     * <pre>{@code
     *   [the camera write]  [the input packet: the crouch]  [the movement packet]
     * }</pre>
     *
     * <p>so the movement packet's own pre-phase is <em>after</em> the crouch has gone out. Grim
     * reads the crouch as lasting from the sneak packet until the next movement packet and cancels
     * any action that arrives inside that window, and the live log has it:</p>
     *
     * <pre>{@code
     *   Grim > ItsEspress0 failed PacketOrderF x0.0 action=place, sprinting=false, sneaking=true
     * }</pre>
     *
     * <p>A cancelled placement is worse than a flag: the client is left standing on a block the
     * server never received, and the two disagree about where the player is - which is the
     * Simulation offset that follows every one of them in the log.</p>
     *
     * <p>So the action leaves from the hook in the player's tick that sits between the camera
     * write and the input packet.</p>
     */
    @Test
    void thePlacementLeavesBeforeTheCrouch() throws IOException {
        String code = code(preRotateSource());

        assertFalse(code.contains("SendMovementPacketsEvent"),
            "the movement packet's pre-phase is after the input packet, so an action fired from "
                + "there lands inside the crouch and Grim's PacketOrderF cancels it");

        assertTrue(code.contains("PreRotate.dispatch()") || code.contains("public static void dispatch("),
            "the action has to leave from the hook that sits between the camera write and the "
                + "input packet");

        String mixin = code(mixinSource("src/main/java/net/venomous/meteoraddon/mixin/LocalPlayerTickMixin.java"));

        assertTrue(mixin.contains("AbstractClientPlayer;tick()V"),
            "the hook has to be right after the player's tick: that is where the camera has been "
                + "written and the input packet has not been sent yet");

        assertTrue(mixin.contains("PreRotate.dispatch()"),
            "and that is where the queued actions go out");
    }

    /**
     * The wait is on the pitch alone.
     *
     * <p>The yaw is the player's own, and it moves with the mouse. PreRotate's readiness test
     * compares the requested yaw with the last one sent, so waiting for the yaw would measure the
     * mouse rather than the rotation - and the action is discarded after five ticks, so the
     * bridge would only build while the player happened to be holding the mouse still.</p>
     */
    @Test
    void theWaitIsOnThePitchAlone() throws IOException {
        String code = code(scaffoldSource());

        assertFalse(code.contains("PreRotate.rotateThen(this, face[0], face[1]"),
            "waiting for the yaw would hold the placement back every time the player looked around");

        assertTrue(code.contains("PreRotate.rotateThenOnPitch("),
            "the pitch-only variant is the one that fits a caller whose yaw is the player's own");
    }
}