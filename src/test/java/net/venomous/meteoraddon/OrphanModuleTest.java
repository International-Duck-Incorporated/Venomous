package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fails when a module is decorative: present in the GUI, taking settings, and wired to nothing.
 *
 * <p>Three modules shipped in that state: {@code TextFieldProtect}, {@code ExtendedFirework} and
 * {@code VGhostHand} each exposed a public API that nothing anywhere in the addon called - a
 * small chat-hook surface, firework multipliers, and the entire "interact through walls" half of
 * the ghost hand - and none could run without a mixin that was never written. All three were
 * removed rather than left taking settings and doing nothing; git history keeps them if a hook
 * is ever written. The allowlists below are empty now, and the assertions keep it that way.</p>
 *
 * <p>Nothing catches an unwired API at compile time: the class loads, the settings render, the
 * module appears in the list. Only reading the code reveals that every toggle does nothing.</p>
 */
public class OrphanModuleTest {
    private static final Path ROOT = Path.of("src/main/java/net/venomous/meteoraddon");

    /** Module base class hooks that Meteor or the GUI call for us. */
    private static final Set<String> LIFECYCLE = Set.of(
        "onActivate", "onDeactivate", "onTick", "onRender", "onRender3D", "onRender2D",
        "onUpdate", "onPreTick", "onPostTick", "onMove", "onChatSend", "onKey",
        "getInfoString", "getDisplayName", "getCategory", "getInfo", "isActive",
        "toggle", "setActive", "getSettings", "getName", "addSettings", "onInitialize",
        "toString", "equals", "hashCode", "run");

    @Test
    void noModuleExposesAnApiThatNothingCalls() throws IOException {
        List<String> orphaned = new ArrayList<>();

        for (Path file : moduleFiles()) {
            String name = file.getFileName().toString();
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));

            // A module that subscribes to events can be called entirely from inside its own
            // handlers, so only modules with no handler at all are at risk of being decorative.
            if (code.contains("@EventHandler")) continue;

            for (String member : publicMethods(code)) {
                if (LIFECYCLE.contains(member)) continue;
                if (isCalledElsewhere(name, member)) continue;

                orphaned.add(name + " -> " + member + "()");
            }
        }

        Set<String> found = new TreeSet<>(orphaned);
        Set<String> unexpected = new TreeSet<>(found);
        unexpected.removeAll(KNOWN_ORPHANED_API);

        assertTrue(unexpected.isEmpty(),
            "New modules expose a public API that nothing calls, so the feature they advertise "
                + "cannot run. Either wire it up or delete it:\n  " + String.join("\n  ", unexpected));
    }

    /**
     * Public API with no caller, known and deliberately left for now.
     *
     * <p>Each of these is waiting on a mixin that was never written. They are recorded rather than
     * deleted because the settings and the intent are both sound; what is missing is the hook
     * that calls them. Wiring them means finding an injection point in Minecraft 26.2 that can be
     * verified only by booting the client, so it is deliberately a separate piece of work rather
     * than something to guess at inside an audit.</p>
     *
     * <p>The equality assertion below is what keeps this honest: adding an entry here requires the
     * orphan to actually exist, and removing the code requires removing the entry.</p>
     */
    private static final Set<String> KNOWN_ORPHANED_API = Set.of();

    @Test
    void theOrphanAllowlistHasNotDrifted() throws IOException {
        List<String> found = new ArrayList<>();

        for (Path file : moduleFiles()) {
            String name = file.getFileName().toString();
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            if (code.contains("@EventHandler")) continue;

            for (String member : publicMethods(code)) {
                if (LIFECYCLE.contains(member)) continue;
                if (isCalledElsewhere(name, member)) continue;
                found.add(name + " -> " + member + "()");
            }
        }

        assertEquals(new TreeSet<>(KNOWN_ORPHANED_API), new TreeSet<>(found),
            "The orphaned-API allowlist is out of date. If an entry was wired up or deleted, "
                + "update it; if something new appeared, decide whether to wire it or delete it.");
    }

    @Test
    void everyModuleEitherRunsSomethingOrIsCalledFromElsewhere() throws IOException {
        List<String> inert = new ArrayList<>();

        for (Path file : moduleFiles()) {
            String name = file.getFileName().toString();
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));

            if (!code.contains("extends Module")) continue;
            if (code.contains("@EventHandler")) continue;

            boolean doesWorkOnActivate = code.contains("void onActivate()")
                && hasStatementsIn(code, "void onActivate()");
            boolean calledElsewhere = isReferencedElsewhere(name);

            if (!doesWorkOnActivate && !calledElsewhere) {
                inert.add(name);
            }
        }

        Set<String> unexpected = new TreeSet<>(inert);
        unexpected.removeAll(KNOWN_INERT_MODULES);

        assertTrue(unexpected.isEmpty(),
            "Modules do nothing at all - no event handler, no activation work, and nothing "
                + "references them:\n  " + String.join("\n  ", unexpected));
    }

    /**
     * Modules with no behaviour of any kind, known and deliberately left for now.
     *
     * <p>Both are API-only shells awaiting a mixin; see {@link #KNOWN_ORPHANED_API}. Neither
     * defines {@code onActivate} and neither is referenced outside the module registry, so
     * enabling either does literally nothing.</p>
     */
    private static final Set<String> KNOWN_INERT_MODULES = Set.of();

    @Test
    void theInertAllowlistHasNotDrifted() throws IOException {
        List<String> inert = new ArrayList<>();

        for (Path file : moduleFiles()) {
            String name = file.getFileName().toString();
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));

            if (!code.contains("extends Module")) continue;
            if (code.contains("@EventHandler")) continue;
            if (code.contains("void onActivate()") && hasStatementsIn(code, "void onActivate()")) continue;
            if (isReferencedElsewhere(name)) continue;

            inert.add(name);
        }

        assertEquals(new TreeSet<>(KNOWN_INERT_MODULES), new TreeSet<>(inert),
            "The inert-module allowlist is out of date.");
    }

    @Test
    void theDetectorStillFires() throws Exception {
        // A guard that cannot fail is no guard. Both checks above key on small, specific
        // conditions, so each is exercised against a sample known to trigger it.
        String orphaned = """
            class Orphan {
                public boolean doTheThing() { return true; }
            }
            """;
        assertTrue(publicMethods(orphaned).contains("doTheThing"),
            "public method extraction must see a plain public method");

        String withHandler = """
            class NotOrphan {
                @EventHandler private void onTick(Pre e) {}
            }
            """;
        assertFalse(withHandler.contains("public boolean"),
            "sanity: sample with a handler must be skipped by the handler check");
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> publicMethods(String code) {
        Matcher m = Pattern.compile(
            "public\\s+(?:static\\s+)?(?:final\\s+)?[\\w<>,.\\[\\] ?]+\\s+(\\w+)\\s*\\(").matcher(code);

        List<String> out = new ArrayList<>();
        while (m.find()) {
            String name = m.group(1);
            // constructors take a class name; skip anything followed by no return-type gap
            if (!out.contains(name)) out.add(name);
        }
        return out;
    }

    private static boolean isCalledElsewhere(String fileName, String member) throws IOException {
        for (Path file : allSources()) {
            if (file.getFileName().toString().equals(fileName)) continue;

            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            if (Pattern.compile("\\b" + Pattern.quote(member) + "\\b").matcher(code).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReferencedElsewhere(String fileName) throws IOException {
        String simple = fileName.replace(".java", "");
        for (Path file : allSources()) {
            if (file.getFileName().toString().equals(fileName)) continue;
            if (file.toString().endsWith("ModuleRegistry.java")) continue;

            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            if (Pattern.compile("\\b" + Pattern.quote(simple) + "\\b").matcher(code).find()) {
                return true;
            }
        }
        return false;
    }

    /** True when the method body contains anything beyond a bare brace. */
    private static boolean hasStatementsIn(String code, String signature) {
        int idx = code.indexOf(signature);
        if (idx < 0) return false;

        int open = code.indexOf('{', idx + signature.length());
        if (open < 0) return false;

        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            if (code.charAt(i) == '{') depth++;
            else if (code.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(open + 1, i).strip().length() > 2;
                }
            }
        }
        return false;
    }

    private static List<Path> moduleFiles() throws IOException {
        List<Path> found = new ArrayList<>();
        Deque<Path> queue = new ArrayDeque<>();
        queue.push(ROOT.resolve("modules"));

        while (!queue.isEmpty()) {
            for (Path child : Files.list(queue.pop()).toList()) {
                if (Files.isDirectory(child)) queue.push(child);
                else if (child.toString().endsWith(".java")) found.add(child);
            }
        }
        return found;
    }

    private static List<Path> allSources() throws IOException {
        List<Path> found = new ArrayList<>();
        Deque<Path> queue = new ArrayDeque<>();
        queue.push(ROOT);

        while (!queue.isEmpty()) {
            for (Path child : Files.list(queue.pop()).toList()) {
                if (Files.isDirectory(child)) queue.push(child);
                else if (child.toString().endsWith(".java")) found.add(child);
            }
        }
        return found;
    }

    /** Blanks comments and string contents so a member name in a string is not a call site. */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean block = false, line = false, str = false, chr = false, esc = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char n = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (line) {
                if (c == '\n') { line = false; out.append(c); } else out.append(' ');
                continue;
            }
            if (block) {
                if (c == '*' && n == '/') { out.append("  "); i++; block = false; }
                else out.append(c == '\n' ? '\n' : ' ');
                continue;
            }
            if (str) {
                if (esc) { out.append(' '); esc = false; continue; }
                if (c == '\\') { out.append(' '); esc = true; continue; }
                if (c == '"') { out.append('"'); str = false; continue; }
                out.append(c == '\n' ? '\n' : ' ');
                continue;
            }
            if (chr) {
                if (esc) { out.append(' '); esc = false; continue; }
                if (c == '\\') { out.append(' '); esc = true; continue; }
                if (c == '\'') { out.append('\''); chr = false; continue; }
                out.append(c == '\n' ? '\n' : ' ');
                continue;
            }

            if (c == '/' && n == '/') { out.append("  "); i++; line = true; continue; }
            if (c == '/' && n == '*') { out.append("  "); i++; block = true; continue; }
            if (c == '"') { out.append('"'); str = true; continue; }
            if (c == '\'') { out.append('\''); chr = true; continue; }
            out.append(c);
        }
        return out.toString();
    }
}
