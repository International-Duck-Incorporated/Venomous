package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prevents gameplay/network work from drifting back to TickEvent.Post.
 *
 * Post runs after the normal per-tick movement flush. Interactions, rotations, inventory clicks,
 * chat, and manually sent movement packets initiated there acquire an avoidable post-movement
 * ordering signature. Read-only modules also use Pre so a future action added to them is safe by
 * default. Render events remain the correct place for rendering, and MovementDebug deliberately
 * observes SendMovementPacketsEvent.Post without changing network state.
 */
class PreTickActionRegressionTest {
    @Test
    void noModuleUsesTickEventPost() throws IOException {
        Path root = Path.of("src/main/java/net/venomous/meteoraddon");
        List<String> offenders;

        try (var files = Files.walk(root)) {
            offenders = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("TickEvent.Post");
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .map(root::relativize)
                .map(Path::toString)
                .sorted()
                .toList();
        }

        assertTrue(offenders.isEmpty(), "TickEvent.Post handlers found: " + offenders);
    }
}
