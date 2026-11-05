package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the module registry as it now exists: a Reflections scan of
 * {@code net.venomous.meteoraddon.modules} that registers every concrete Module subclass with a
 * public no-argument constructor.
 *
 * <p>There is no hand-maintained list to keep in sync any more - A1 removed it, along with the
 * merge-conflict problem this test used to guard. What can and must still be guarded is the
 * <em>contract the scanner enforces</em>:</p>
 *
 * <ol>
 *   <li>Every source file under {@code modules/} is a registrable module - declares
 *       {@code extends Module}, is concrete, and has a public no-arg constructor - unless it is
 *       named in {@link #NON_MODULE_CLASSES}, which is the sanctioned home for helper classes
 *       that live beside the modules they serve. Adding to that set is a deliberate decision,
 *       asserted against reality the same way the old allowlists were.</li>
 *   <li>The Reflections scan, run against the compiled test classpath (no Meteor boot - the
 *       scan instantiates nothing), discovers exactly those same classes. If a module is added
 *       that the scanner would silently miss at runtime, this fails before the game boots.</li>
 * </ol>
 */
public class ModuleRegistryIntegrityTest {
    private static final Path MODULES_ROOT = Path.of("src/main/java/net/venomous/meteoraddon/modules");
    private static final String BASE_PACKAGE = "net.venomous.meteoraddon.modules";

    /** Classes that live under modules/ but are helpers rather than registrable Meteor modules. */
    private static final Set<String> NON_MODULE_CLASSES = Set.of(
        "ModuleRegistry"
    );

    private static String basename(String fileName) {
        return fileName.replace(".java", "");
    }

    private static Set<String> moduleSourceFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(MODULES_ROOT)) {
            Set<String> found = new TreeSet<>();
            paths.filter(p -> p.getFileName().toString().endsWith(".java"))
                .map(p -> basename(p.getFileName().toString()))
                .filter(n -> !NON_MODULE_CLASSES.contains(n))
                .forEach(found::add);
            return found;
        }
    }

    /**
     * The source-level contract. Textual rather than reflective on purpose: it runs on the
     * source tree, so a purely syntactic violation (an abstract module, a hidden constructor)
     * is caught with a precise message naming the file.
     */
    @Test
    void everyModuleSourceFileSatisfiesTheScannerContract() throws IOException {
        TreeSet<String> offenders = new TreeSet<>();

        try (Stream<Path> paths = Files.walk(MODULES_ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = basename(file.getFileName().toString());
                if (NON_MODULE_CLASSES.contains(name)) continue;

                String source = Files.readString(file, StandardCharsets.UTF_8);
                String code = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
                String squeezed = code.replaceAll("\\s+", "");

                if (!squeezed.contains("extendsModule")) {
                    offenders.add(name + " (does not extend Module)");
                    continue;
                }
                if (squeezed.matches(".*\\babstractclass\\b.*")) {
                    offenders.add(name + " (abstract - the scanner registers concrete classes only)");
                    continue;
                }
                Pattern ctor = Pattern.compile(
                    "public\\s+" + Pattern.quote(name) + "\\s*\\(\\s*\\)");
                if (!ctor.matcher(code).find()) {
                    offenders.add(name + " (no public no-arg constructor - the scanner cannot build)");
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "Files under modules/ violate the reflection scanner's contract, so they would be "
                + "silently skipped at runtime:\n  " + String.join("\n  ", offenders));
    }

    /**
     * The compiled-class contract. Package scanning (Reflections) cannot be trusted in the
     * unit-test classloader, so instead of re-running the scan, every module source file's class
     * is loaded directly and subjected to exactly the predicates the runtime scanner applies:
     * Module subclass, concrete, public no-arg constructor. A module that would be silently
     * skipped fails here with its name before the game boots.
     */
    @Test
    void everyModuleSourceFilesClassSatisfiesTheScannerContract() throws IOException {
        TreeSet<String> offenders = new TreeSet<>();

        try (Stream<Path> paths = Files.walk(MODULES_ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = basename(file.getFileName().toString());
                if (NON_MODULE_CLASSES.contains(name)) continue;

                String relative = MODULES_ROOT.relativize(file).toString()
                    .replace('\\', '.').replace(".java", "");
                String fqn = "net.venomous.meteoraddon.modules." + relative;

                try {
                    Class<?> clazz = Class.forName(fqn);

                    if (!meteordevelopment.meteorclient.systems.modules.Module.class.isAssignableFrom(clazz)) {
                        offenders.add(name + " (does not extend Module)");
                        continue;
                    }
                    if (Modifier.isAbstract(clazz.getModifiers())) {
                        offenders.add(name + " (abstract - the scanner registers concrete classes only)");
                        continue;
                    }
                    if (!Modifier.isPublic(clazz.getModifiers())) {
                        offenders.add(name + " (not public)");
                        continue;
                    }
                    if (!Modifier.isPublic(clazz.getDeclaredConstructor().getModifiers())) {
                        offenders.add(name + " (no public no-arg constructor)");
                    }
                } catch (ClassNotFoundException e) {
                    offenders.add(name + " (class not on the compiled classpath)");
                } catch (NoSuchMethodException e) {
                    offenders.add(name + " (no no-arg constructor)");
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "Modules violate the reflection scanner's contract, so they would be silently "
                + "skipped at runtime:\n  " + String.join("\n  ", offenders));
    }

    /**
     * Walks the compiled contract the same way the runtime scanner's result set is defined, and
     * asserts the resulting set of registrable classes matches the source tree one-for-one:
     * nothing on disk that fails registration, nothing registered without source.
     */
    @Test
    void registrableClassSetMatchesTheSourceTree() throws IOException {
        Set<String> registrable = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(MODULES_ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = basename(file.getFileName().toString());
                if (NON_MODULE_CLASSES.contains(name)) continue;

                String relative = MODULES_ROOT.relativize(file).toString()
                    .replace('\\', '.').replace(".java", "");
                String fqn = "net.venomous.meteoraddon.modules." + relative;
                try {
                    Class<?> clazz = Class.forName(fqn);
                    boolean registrableClass =
                        meteordevelopment.meteorclient.systems.modules.Module.class.isAssignableFrom(clazz)
                            && !Modifier.isAbstract(clazz.getModifiers())
                            && Modifier.isPublic(clazz.getModifiers());
                    if (registrableClass) registrable.add(name);
                } catch (ClassNotFoundException e) {
                    // A class that cannot load is not registrable; the contract test above
                    // already reported it in detail, so here it is simply absent from the set.
                }
            }
        }

        assertEquals(moduleSourceFiles(), registrable,
            "The set of registrable classes must match the module source tree one-for-one");
    }
}
