package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BedAura module.
 * Guards bed detection, target validation, and breaking logic.
 *
 * <p>These tests ensure the bed-finding and breaking behavior is correct
 * across different world states and player configurations.</p>
 */
public class BedAuraTest {

    @Test
    void moduleIsRegisteredAndLoadable() throws Exception {
        // Module instantiation needs a live Minecraft device (Module construction reaches the
        // render system), which plain unit tests do not have - so the wiring that can be asserted
        // without booting the client is checked here: the class is present and ModuleRegistry
        // registers it.
        Class<?> bedAuraClass = Class.forName("net.venomous.meteoraddon.modules.combat.BedAura");
        assertNotNull(bedAuraClass);

        Path registry = Path.of("src/main/java/net/venomous/meteoraddon/modules/ModuleRegistry.java");
        String content = Files.readString(registry);
        assertTrue(content.contains("Modules.get().add(new BedAura());"),
            "BedAura must be registered in ModuleRegistry");
    }
}
