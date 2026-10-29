package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class NoClipTest {

    @Test
    void moduleIsRegisteredAndLoadable() throws Exception {
        Class<?> noClipClass = Class.forName("net.venomous.meteoraddon.modules.movement.NoClip");
        assertNotNull(noClipClass);

        Path registry = Path.of("src/main/java/net/venomous/meteoraddon/modules/ModuleRegistry.java");
        String content = Files.readString(registry);
        assertTrue(content.contains("Modules.get().add(new NoClip());"),
            "NoClip must be registered in ModuleRegistry");
    }
}
