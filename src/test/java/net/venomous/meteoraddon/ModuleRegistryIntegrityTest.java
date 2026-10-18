package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleRegistryIntegrityTest {
    private static final Path MODULES_ROOT = Path.of("src/main/java/net/venomous/meteoraddon/modules");
    private static final Path REGISTRY = MODULES_ROOT.resolve("ModuleRegistry.java");

    /**
     * Classes that live under modules/ but are helpers rather than registrable Meteor modules.
     */
    private static final Set<String> NON_MODULE_CLASSES = Set.of(
        "ModuleRegistry"
    );

    private static Set<String> registeredModules() throws Exception {
        assertTrue(Files.exists(REGISTRY), "ModuleRegistry.java must exist");

        String content = Files.readString(REGISTRY, StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("Modules\\.get\\(\\)\\.add\\(new\\s+(\\w+)\\(\\)\\);");
        Matcher matcher = pattern.matcher(content);

        Set<String> registered = new TreeSet<>();
        while (matcher.find()) {
            String moduleName = matcher.group(1);
            assertTrue(registered.add(moduleName),
                "Duplicate module registration in ModuleRegistry: " + moduleName);
        }
        return registered;
    }

    private static Set<String> moduleSourceFiles() throws Exception {
        try (Stream<Path> paths = Files.walk(MODULES_ROOT)) {
            Set<String> found = new TreeSet<>();
            paths.filter(p -> p.getFileName().toString().endsWith(".java"))
                .map(p -> p.getFileName().toString().replace(".java", ""))
                .filter(n -> !NON_MODULE_CLASSES.contains(n))
                .forEach(found::add);
            return found;
        }
    }

    @Test
    void testRegistryHasNoDuplicates() throws Exception {
        assertFalse(registeredModules().isEmpty(), "Registry should register at least one module");
    }

    /**
     * Derives the expected set from the source tree instead of hardcoding a count, so adding a
     * module only fails the build when it was genuinely forgotten in ModuleRegistry.
     */
    @Test
    void testEveryModuleSourceFileIsRegistered() throws Exception {
        Set<String> onDisk = moduleSourceFiles();
        Set<String> registered = registeredModules();

        Set<String> missing = new TreeSet<>(onDisk);
        missing.removeAll(registered);
        assertTrue(missing.isEmpty(),
            "Module classes exist on disk but are never registered in ModuleRegistry: " + missing
                + " (if one of these is a helper, add it to NON_MODULE_CLASSES)");

        Set<String> ghosts = new TreeSet<>(registered);
        ghosts.removeAll(onDisk);
        assertTrue(ghosts.isEmpty(),
            "ModuleRegistry registers classes with no matching source file: " + ghosts);
    }

    @Test
    void testRegistryImportsMatchRegistrations() throws Exception {
        String content = Files.readString(REGISTRY, StandardCharsets.UTF_8);
        Matcher importMatcher = Pattern
            .compile("import\\s+net\\.venomous\\.meteoraddon\\.modules\\.[\\w.]*?(\\w+);")
            .matcher(content);

        Set<String> imported = new HashSet<>();
        while (importMatcher.find()) imported.add(importMatcher.group(1));

        Set<String> registered = registeredModules();
        Set<String> unimported = new TreeSet<>(registered);
        unimported.removeAll(imported);

        assertTrue(unimported.isEmpty(),
            "Registered modules missing an import in ModuleRegistry: " + unimported);
    }
}
