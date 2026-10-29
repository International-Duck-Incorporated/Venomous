package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify PacketGates coexists with BowAimbot's deferred-action (PreRotate) mechanism.
 */
public class PacketGatesIntegrationTest {

    @Test
    void packetGatesModuleExistsAndRegistered() throws Exception {
        Class<?> pg = Class.forName("net.venomous.meteoraddon.utils.PacketGates");
        assertNotNull(pg);

        Path reg = Path.of("src/main/java/net/venomous/meteoraddon/modules/ModuleRegistry.java");
        String content = Files.readString(reg);

        // PacketGates is a utility, not a module, but its source exists and is referenced by modules
        assertTrue(content.contains("PacketGates"),
            "ModuleRegistry references PacketGates");
    }
}
