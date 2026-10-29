package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BowAimbot module.
 *
 * <p>The module itself cannot be instantiated in a plain unit test - module construction reaches
 * the Minecraft render system - so what these tests pin are the contracts that are unit-testable:
 * the module class is loadable and {@code ModuleRegistry} registers it.</p>
 */
public class BowAimbotTest {

    @Test
    void moduleIsRegisteredAndLoadable() throws Exception {
        Class<?> bowAimbotClass = Class.forName("net.venomous.meteoraddon.modules.combat.BowAimbot");
        assertNotNull(bowAimbotClass);

        Path registry = Path.of("src/main/java/net/venomous/meteoraddon/modules/ModuleRegistry.java");
        String content = Files.readString(registry);
        assertTrue(content.contains("Modules.get().add(new BowAimbot());"),
            "BowAimbot must be registered in ModuleRegistry");
    }
}
