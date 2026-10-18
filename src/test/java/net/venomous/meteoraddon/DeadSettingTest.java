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
 * Fails when a module registers a setting it never reads.
 *
 * <p>Fifteen such settings were found: declared, given a name and description so they appear in
 * the GUI, and then never consulted. A control that visibly does nothing is worse than no control
 * at all, because it tells the user a capability exists. Several modules shipped with an entire
 * feature unwired behind one - middle-click-action's "humanlike timing" was the original find.</p>
 *
 * <p>This is a source scanner rather than reflection: settings are constructed through chained
 * builders, are private, and Meteor's Setting has no "was this ever read" flag. Comments and
 * string literals are blanked first, so a name mentioned only inside a description string does
 * not read as a use - that distinction is what makes the check meaningful.</p>
 */
public class DeadSettingTest {
    private static final Path MODULES = Path.of("src/main/java/net/venomous/meteoraddon/modules");

    /** A setting declaration: {@code private final Setting<X> name = ...} */
    private static final Pattern DECLARATION =
        Pattern.compile("private\\s+(?:final\\s+)?Setting(?:Group)?<[^>]*>\\s+(\\w+)\\s*=");

    /**
     * Modules allowed to carry dead settings, because they are scaffolds rather than
     * implementations.
     *
     * <p>Intentionally empty. It started out listing four stub modules - FlagCheck, FreecamMining,
     * InventoryTracker and TNTTimer - none of which had any behaviour for a setting to gate.
     * All four have since been written, so the list is now empty and any new entry has to be a
     * deliberate decision rather than a leftover.</p>
     *
     * <p>The equality assertion below means the list cannot quietly grow: adding a module here
     * requires also having a setting that genuinely has nothing behind it.</p>
     */
    private static final Set<String> STUB_MODULES = Set.of();

    @Test
    void noModuleRegistersASettingItNeverReads() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : javaFiles(MODULES)) {
            String code = blankCommentsAndStrings(Files.readString(file, StandardCharsets.UTF_8));
            Matcher matcher = DECLARATION.matcher(code);

            while (matcher.find()) {
                String name = matcher.group(1);
                int uses = countWord(code, name);

                // One occurrence is the declaration itself and nothing else.
                if (uses <= 1) {
                    offenders.add(file.getFileName() + " -> " + name);
                }
            }
        }

        // Settings belonging to a module that is itself an unimplemented stub are reported
        // separately rather than counted here: there is no behaviour for them to gate, so
        // "no setting reads it" is the module being unfinished, not a setting being dead.
        Set<String> stubFiles = new TreeSet<>();
        List<String> dead = new ArrayList<>();

        for (String offender : offenders) {
            String file = offender.substring(0, offender.indexOf(" -> "));

            if (STUB_MODULES.contains(file)) stubFiles.add(file);
            else dead.add(offender);
        }

        // The allowlist is not an excuse: anything outside it must be genuinely fixed, and the
        // allowlist is asserted to match exactly the stub files still offending, so it cannot
        // quietly grow and cannot outlive the stub it was written for.
        assertEquals(STUB_MODULES, stubFiles,
            "The stub allowlist is out of date. Either a stub module gained real behaviour "
                + "(remove it from the list), or a new stub appeared (add it deliberately).");

        assertTrue(dead.isEmpty(),
            "Settings registered but never read (they appear in the GUI and do nothing):\n  "
                + String.join("\n  ", dead));
    }

    @Test
    void theScannerCanActuallyDetectADeadSetting() throws Exception {
        // Self-test. A scanner that cannot fail is worse than no scanner - the POST-placement
        // regression test in this repo silently passed against a live bug for exactly that
        // reason. This asserts the detector fires on a source file with a known dead setting,
        // and stays quiet on one where the setting is used.
        assertTrue(hasDeadSetting("""
            class Sample extends Module {
                private final Setting<Boolean> neverRead = this.sg.add(new BoolSetting.Builder().name("never-read").build());
            }
            """), "a declared-and-unused setting must be detected");

        assertFalse(hasDeadSetting("""
            class Sample extends Module {
                private final Setting<Boolean> actuallyRead = this.sg.add(new BoolSetting.Builder().name("actually-read").build());
                void use() { if ((Boolean)this.actuallyRead.get()) { doThing(); } }
            }
            """), "a setting that is read must not be reported");
    }

    private static boolean hasDeadSetting(String source) {
        Matcher matcher = DECLARATION.matcher(source);
        if (!matcher.find()) return false;
        return countWord(source, matcher.group(1)) <= 1;
    }

    private static int countWord(String code, String word) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(code);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        List<Path> found = new ArrayList<>();
        Deque<Path> queue = new ArrayDeque<>();
        queue.push(root);

        while (!queue.isEmpty()) {
            for (Path child : Files.list(queue.pop()).toList()) {
                if (Files.isDirectory(child)) queue.push(child);
                else if (child.toString().endsWith(".java")) found.add(child);
            }
        }

        return found;
    }

    /**
     * Replaces comment and string contents with spaces, keeping newlines so line numbers and
     * offsets stay aligned. Without this, a setting named in its own {@code .name("...")} string
     * would count as a use and the check would never fire.
     */
    private static String blankCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') { inLineComment = false; out.append(c); } else out.append(' ');
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') { out.append("  "); i++; inBlockComment = false; } else out.append(c == '\n' ? '\n' : ' ');
                continue;
            }

            if (inString) {
                if (escaped) { out.append(' '); escaped = false; continue; }
                if (c == '\\') { out.append(' '); escaped = true; continue; }
                if (c == '"') { out.append('"'); inString = false; continue; }
                out.append(c == '\n' ? '\n' : ' ');
                continue;
            }

            if (inChar) {
                if (escaped) { out.append(' '); escaped = false; continue; }
                if (c == '\\') { out.append(' '); escaped = true; continue; }
                if (c == '\'') { out.append('\''); inChar = false; continue; }
                out.append(c == '\n' ? '\n' : ' ');
                continue;
            }

            if (c == '/' && next == '/') { out.append("  "); i++; inLineComment = true; continue; }
            if (c == '/' && next == '*') { out.append("  "); i++; inBlockComment = true; continue; }
            if (c == '"') { out.append('"'); inString = true; continue; }
            if (c == '\'') { out.append('\''); inChar = true; continue; }

            out.append(c);
        }

        return out.toString();
    }

    @Test
    void blankingPreservesLineStructure() {
        // Offsets must survive, or line numbers in failure messages would be wrong.
        String source = "a\n\"two\\nlines\"\n// three\nb";
        String blanked = blankCommentsAndStrings(source);

        assertEquals(source.lines().count(), blanked.lines().count(),
            "blanking must not change the number of lines");
        assertEquals(source.length(), blanked.length(),
            "blanking must be character-for-character, so offsets stay valid");
    }

    @Test
    void everyRegisteredModuleIsStillScanned() throws IOException {
        // Guards the scanner itself: if the path or the file walk silently matched nothing,
        // the check above would pass vacuously forever.
        assertTrue(javaFiles(MODULES).size() > 100,
            "expected the full module tree to be walked, found far too few files");
    }

}
