package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module may cancel its own deferred action, but must never clear the shared queue.
 *
 * <p>PreRotate is global to the addon. A global clear from one module's target loss or teardown
 * also discards unrelated work, such as another combat module's staged interaction.</p>
 */
class PreRotateIsolationTest extends SourceGuard {
    @Test
    void modulesNeverClearTheGlobalDeferredActionQueue() throws IOException {
        Path root = Path.of("src/main/java/net/venomous/meteoraddon/modules");
        List<String> offenders;

        try (var files = Files.walk(root)) {
            offenders = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> {
                    try {
                        return squeeze(code(Files.readString(path))).contains("PreRotate.cancelAll();");
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .map(root::relativize)
                .map(Path::toString)
                .sorted()
                .toList();
        }

        assertTrue(offenders.isEmpty(),
            "Use PreRotate.cancel(this) for module-owned work; global clears affect other modules: "
                + offenders);
    }
}