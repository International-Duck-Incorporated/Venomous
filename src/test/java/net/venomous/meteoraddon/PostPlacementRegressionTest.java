package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against reintroducing the "attacking/placing on POST" bug.
 *
 * <p>Meteor's {@code Rotations.rotate(yaw, pitch, priority, callback)} invokes its callback from
 * {@code SendMovementPacketsEvent.Post}, i.e. after the tick's movement packet has been flushed.
 * Performing an interaction from there sends it after the position packet, which anticheats
 * fingerprint as post-placement. Interactions must be routed through
 * {@code net.venomous.meteoraddon.utils.PreRotate} instead, which fires them in the PRE phase.
 *
 * <p>Passing {@code null} as the callback is fine — that is a pure rotation with no action.
 *
 * <p>This is deliberately a brace-matching scanner rather than a regex: lambda bodies contain both
 * {@code ;} and {@code )}, which defeats any straightforward pattern.
 */
public class PostPlacementRegressionTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /** {@code PreRotate} is the one place allowed to call the raw rotate API. */
    private static final String ALLOWED = "PreRotate.java";

    private static final String NEEDLE = "Rotations.rotate";

    @Test
    void testNoModulePerformsActionsFromTheRotationCallback() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (path.getFileName().toString().equals(ALLOWED)) {
                    continue;
                }

                String source = Files.readString(path, StandardCharsets.UTF_8);
                for (String args : findCallArguments(source)) {
                    String last = lastTopLevelArgument(args);
                    if (isAction(last)) {
                        offenders.add(SOURCE_ROOT.relativize(path) + " -> rotate(..., " + collapse(last) + ")");
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "These call Rotations.rotate with an action callback, which runs in the POST phase and "
                + "flags on anticheats. Use PreRotate.rotateThen(...) instead:\n  "
                + String.join("\n  ", offenders));
    }

    /** The scanner must actually be capable of catching the bug it guards against. */
    @Test
    void testScannerDetectsAPostCallback() {
        String bad = """
            Rotations.rotate(getYaw(pos), getPitch(pos), 10, () -> {
                mc.gameMode.destroyBlock(pos);
            });
            """;
        List<String> calls = findCallArguments(bad);
        assertFalse(calls.isEmpty(), "scanner should find the call");
        assertTrue(isAction(lastTopLevelArgument(calls.getFirst())), "scanner should flag a lambda callback");

        String good = "Rotations.rotate(yaw, pitch, 10, null);";
        assertFalse(isAction(lastTopLevelArgument(findCallArguments(good).getFirst())),
            "a null callback is a pure rotation and must not be flagged");
    }

    /** Extracts the raw argument text of every {@code Rotations.rotate(...)} call. */
    private static List<String> findCallArguments(String source) {
        List<String> found = new ArrayList<>();
        int index = 0;

        while ((index = source.indexOf(NEEDLE, index)) >= 0) {
            int open = source.indexOf('(', index);
            if (open < 0) {
                break;
            }

            int depth = 0;
            int cursor = open;
            while (cursor < source.length()) {
                char c = source.charAt(cursor);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                cursor++;
            }

            if (cursor >= source.length()) {
                break;
            }

            found.add(source.substring(open + 1, cursor));
            index = cursor + 1;
        }

        return found;
    }

    /** Splits on commas at nesting depth zero and returns the final argument. */
    private static String lastTopLevelArgument(String args) {
        int depth = 0;
        int lastSplit = -1;

        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                lastSplit = i;
            }
        }

        return args.substring(lastSplit + 1).trim();
    }

    private static boolean isAction(String argument) {
        if (argument.isEmpty() || argument.equals("null")) {
            return false;
        }
        return argument.contains("->") || argument.contains("::") || argument.equals("action");
    }

    private static String collapse(String snippet) {
        String flat = snippet.replaceAll("\\s+", " ").trim();
        return flat.length() > 80 ? flat.substring(0, 80) + "..." : flat;
    }
}
