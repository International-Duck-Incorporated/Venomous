package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MaceAuraTest {

    @Test
    void moduleIsRegisteredAndLoadable() throws Exception {
        Class<?> maceAuraClass = Class.forName("net.venomous.meteoraddon.modules.combat.MaceAura");
        assertNotNull(maceAuraClass);

        Path registry = Path.of("src/main/java/net/venomous/meteoraddon/modules/ModuleRegistry.java");
        String content = Files.readString(registry);
        assertTrue(content.contains("Modules.get().add(new MaceAura());"),
            "MaceAura must be registered in ModuleRegistry");
    }
}
