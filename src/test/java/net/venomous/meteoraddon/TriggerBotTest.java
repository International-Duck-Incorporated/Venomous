package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TriggerBot module.
 */
public class TriggerBotTest {

    @Test
    void moduleIsRegisteredAndLoadable() throws Exception {
        Class<?> triggerBotClass = Class.forName("net.venomous.meteoraddon.modules.combat.TriggerBot");
        assertNotNull(triggerBotClass);

        Path registry = Path.of("src/main/java/net/venomous/meteoraddon/modules/ModuleRegistry.java");
        String content = Files.readString(registry);
        assertTrue(content.contains("Modules.get().add(new TriggerBot());"),
            "TriggerBot must be registered in ModuleRegistry");
    }
}
