package net.venomous.meteoraddon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Support for tests that assert on source text rather than behaviour.
 *
 * <p>Some invariants genuinely have no behavioural seam: "BlockUtils.place is never called with
 * {@code rotate = true}" is a property of the call site, not of anything callable. Those tests read
 * the source. Done naively they are a liability, and this repository has already paid for it -
 * hoisting one expression into a local variable, with no behaviour change, broke two assertions that
 * matched a single call expression.</p>
 *
 * <p>Three things make such a test survive an unrelated refactor:</p>
 * <ol>
 *   <li><b>Normalise before matching.</b> Whitespace, line breaks and comment layout are not the
 *       subject of the assertion.</li>
 *   <li><b>Extract by braces, not by text search.</b> A method body is found by balanced braces, so
 *       an assertion does not accidentally match an identical fragment in a neighbouring method.</li>
 *   <li><b>Fail loudly when an anchor moves.</b> {@link #requireAnchor} distinguishes "the invariant
 *       broke" from "this test is looking at code that no longer exists", which are very different
 *       problems and used to present identically.</li>
 * </ol>
 *
 * <p>Prefer a behavioural test wherever one exists. Use this only where the invariant is about the
 * shape of the code.</p>
 */
abstract class SourceGuard {
    private static final Path ROOT = Path.of(System.getProperty("user.dir")).toAbsolutePath();

    /** Collapses all whitespace runs to a single space. */
    protected static String normalise(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Removes whitespace entirely, so {@code foo(bar)} and {@code foo( bar )} are the same anchor.
     *
     * <p>Formatting is not the subject of any of these assertions, and re-spacing a call is exactly
     * the kind of change a formatter makes without altering behaviour.</p>
     */
    protected static String squeeze(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** Strips comments, then normalises. Prose in a javadoc must not satisfy an assertion. */
    protected static String code(String text) {
        return normalise(text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " "));
    }

    protected String source(String relative) {
        Path dir = ROOT;

        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException e) {
                    throw new AssertionError("could not read " + relative, e);
                }
            }
            dir = dir.getParent();
        }

        throw new AssertionError("could not find " + relative + " above " + ROOT);
    }

    /**
     * The body of a method, found by balanced braces from its signature.
     *
     * <p>Searching for the next {@code "\n    }"} was the previous approach and it silently returns
     * the wrong region when the indentation does not match.</p>
     */
    protected static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return null;

        int open = source.indexOf('{', start);
        if (open < 0) return null;

        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open + 1, i);
        }

        return null;
    }

    /**
     * Asserts an anchor still exists, failing with a diagnostic that distinguishes a moved anchor
     * from a broken invariant.
     */
    protected static void requireAnchor(String where, String what, String... anchors) {
        List<String> missing = new ArrayList<>();
        String haystack = squeeze(where);

        for (String anchor : anchors) {
            if (!haystack.contains(squeeze(anchor))) missing.add(anchor);
        }

        org.junit.jupiter.api.Assertions.assertTrue(missing.isEmpty(),
            "This guard is pointed at code that no longer exists (" + missing + "). That is not a "
                + "violated invariant - the test needs re-pointing at the new shape. Behaviour may "
                + "well be unchanged.");
    }

    /** Strips package-private helper noise so anchors read as the thing being asserted. */
    protected static Pattern pattern(String regex) {
        return Pattern.compile(regex);
    }

    protected static boolean matches(String regex, String text) {
        Matcher matcher = pattern(regex).matcher(text);
        return matcher.find();
    }
}
