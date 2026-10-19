package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the input compensation mixin.
 *
 * <p>The mixin is the only part of the freelook work that runs inside the game, and a mistake in it
 * is a hard crash rather than a misbehaviour: the first version shipped without
 * {@code cancellable = true} and the client died on the first tick with
 * {@code CancellationException: The call modifyInput is not cancellable}. The crash report is the
 * only symptom, and it arrives as a wall of stack trace with nothing pointing at the addon.</p>
 */
public class LocalPlayerMixinTest {
    private static Path repoRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd;
        for (int i = 0; i < 4 && candidate != null; i++) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return cwd;
    }

    private static String mixin() throws IOException {
        Path path = repoRoot()
            .resolve("src/main/java/net/venomous/meteoraddon/mixin/LocalPlayerMixin.java");
        assertTrue(Files.isRegularFile(path), "LocalPlayerMixin.java is missing");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The source without its comments, so a name in the prose is not mistaken for code. */
    private static String code(String source) {
        StringBuilder out = new StringBuilder();
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * The rotation has to be applied where it happens exactly once per tick.
     *
     * <p>Injecting into {@code LocalPlayer.modifyInput} was wrong, and it cost a session. ViaFabricPlus
     * redirects that method, so the injection could fire more than once in a tick and rotate the
     * input more than once. The offsets went from 0.009 to 0.156 - ten times worse - and past
     * Grim's immediate-setback threshold, which teleports the player back every tick: frozen in
     * place, with the flags to show for it.</p>
     */
    @Test
    void theRotationIsAppliedWhereItHappensOncePerTick() throws IOException {
        String source = code(mixin());

        assertTrue(source.contains("@Redirect"),
            "the compensation has to be a redirect on the call that produces the movement input, "
                + "not an injection into a method another mod redirects");

        assertTrue(source.contains("getMoveVector"),
            "getMoveVector is called exactly once by LocalPlayer.applyInput, so redirecting it "
                + "applies the rotation exactly once per tick");

        assertFalse(source.contains("modifyInput"),
            "modifyInput is redirected by ViaFabricPlus, so a rotation applied there can be applied "
                + "more than once in a tick");
    }

    /**
     * And it has to target the right method.
     *
     * <p>{@code modifyInput} is the game's own hook for altering the movement vector, where it
     * scales the input for sneaking and for using an item. Rotating it there preserves the scale and
     * the direction together, and the game's own adjustments are untouched.</p>
     */
    @Test
    void itTargetsTheGamesOwnInputHook() throws IOException {
        String source = mixin();

        assertTrue(source.contains("modifyInput"),
            "the compensation has to be applied to LocalPlayer.modifyInput, the point where the game "
                + "resolves and scales the movement input");

        assertTrue(source.contains("Lnet/minecraft/world/phys/Vec2;"),
            "and the descriptor has to be exact, or the injection silently fails to apply");
    }

    /**
     * The mixin has to be registered, or none of it runs.
     */
    @Test
    void theMixinIsRegistered() throws IOException {
        Path path = repoRoot().resolve("src/main/resources/venomous.mixins.json");
        assertTrue(Files.isRegularFile(path), "venomous.mixins.json is missing");

        String json = Files.readString(path, StandardCharsets.UTF_8);

        assertTrue(json.contains("\"LocalPlayerMixin\""),
            "the mixin is not in venomous.mixins.json, so the input is never compensated and the "
                + "movement keeps disagreeing with the server's prediction");
    }
}
