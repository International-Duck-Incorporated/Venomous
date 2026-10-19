package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    /**
     * The injection has to be cancellable, or replacing the return value throws.
     */
    @Test
    void theInjectionIsCancellableSoTheReturnValueCanBeReplaced() throws IOException {
        String source = mixin();

        assertTrue(source.contains("cancellable = true"),
            "setReturnValue on a CallbackInfoReturnable throws \"the call modifyInput is not "
                + "cancellable\" without cancellable = true, and that is a hard client crash on the "
                + "first tick rather than a warning");
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
