package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails when a test asserts a tautology.
 *
 * <p>{@code assertTrue(true)} can never fail, so it documents nothing and reports a green run
 * for code it never exercised. Four test classes shipped in that shape before this guard
 * existed; they were deleted rather than left to look like coverage.</p>
 */
public class TestQualityTest {
    private static final Path ROOT = Path.of("src/test/java/net/venomous/meteoraddon");

    private static final Pattern TAUTOLOGY = Pattern.compile(
        "\\bassert(?:True|False)\\s*\\(\\s*(?:true|false)\\b");

    @Test
    void noTestAssertsATautology() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (var paths = Files.walk(ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                Matcher matcher = TAUTOLOGY.matcher(code);

                while (matcher.find()) {
                    offenders.add(file.getFileName() + ": "
                        + matcher.group().replaceAll("\\s+", " "));
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "Tests that assert a tautology can never fail; replace them with a real check:\n  "
                + String.join("\n  ", offenders));
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("(?m)//.*$", " ");
    }
}
