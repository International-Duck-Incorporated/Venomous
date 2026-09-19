package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleRegistryIntegrityTest {
    @Test
    void testRegistryContainsAllModulesWithoutDuplicates() throws Exception {
        Path registryPath = Path.of("src/main/java/net/venomous/meteoraddon/modules/ModuleRegistry.java");
        assertTrue(Files.exists(registryPath), "ModuleRegistry.java must exist");

        String content = Files.readString(registryPath, StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("Modules\\.get\\(\\)\\.add\\(new\\s+(\\w+)\\(\\)\\);");
        Matcher matcher = pattern.matcher(content);

        Set<String> registeredModules = new HashSet<>();
        int count = 0;

        while (matcher.find()) {
            String moduleName = matcher.group(1);
            assertFalse(registeredModules.contains(moduleName), "Duplicate module registration in ModuleRegistry: " + moduleName);
            registeredModules.add(moduleName);
            count++;
        }

        assertEquals(124, count, "All 124 modules should be registered");
    }
}
