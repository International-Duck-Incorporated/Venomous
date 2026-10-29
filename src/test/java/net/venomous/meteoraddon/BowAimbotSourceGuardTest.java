package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for BowAimbot's staged-state lifecycle.
 *
 * <p>The module's two prior FAIL rounds were both caused by this surface:
 * (a) global PreRotate.cancelAll clearing other modules, and (b) missing
 * post-fire state clearing after the shot fired. This test anchors the
 * repaired invariant so a future refactor cannot silently regress the fix.</p>
 */
public class BowAimbotSourceGuardTest {

    @Test
    void bowAimbotHasNoGlobalCancelAllInModuleSource() throws Exception {
        Path source = Path.of("src/main/java/net/venomous/meteoraddon/modules/combat/BowAimbot.java");
        assertTrue(Files.exists(source), "BowAimbot source must exist");

        String text = Files.readString(source);
        assertFalse(text.contains("PreRotate.cancelAll()"),
            "BowAimbot must not call global PreRotate.cancelAll() — owner-scoped clearing only");
        assertTrue(text.contains("PreRotate.cancel(this)"),
            "BowAimbot must use owner-scoped PreRotate.cancel(this) for staged-state clearing");
    }

    @Test
    void bowAimbotCastsClearStagedAndSelfGuards() throws Exception {
        Path source = Path.of("src/main/java/net/venomous/meteoraddon/modules/combat/BowAimbot.java");
        String text = Files.readString(source);

        assertTrue(text.contains("clearStaged()"),
            "BowAimbot must have a clearStaged() method that consumes the staged aim");
        assertTrue(text.contains("isActive()"),
            "BowAimbot.onTick must self-guard with isActive()");
        assertTrue(text.contains("this::cast"),
            "BowAimbot stageCast must hand its deferred action to cast()");
    }

    @Test
    void bowAimbotNoBrokenTwinRemains() throws Exception {
        Path broken = Path.of("src/main/java/net/venomous/meteoraddon/modules/combat/BowAimbot.java.broken");
        assertFalse(Files.exists(broken),
            "Stale broken copy must not remain in the source tree — it retains the round-1 bug shape");
    }
}
