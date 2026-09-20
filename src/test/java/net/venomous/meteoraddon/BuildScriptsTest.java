package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the repository's build entry points.
 *
 * <p>Motivation: CI failed with {@code ./gradlew: Permission denied} (exit 126) because the
 * committed {@code gradlew} had mode 100644 instead of 100755. Nothing in the build caught it,
 * because a local developer usually invokes an already-executable copy or their IDE's own Gradle.
 * The failure only showed up on a fresh clone in CI. These tests make it a build failure instead.
 */
public class BuildScriptsTest {

    private static Path repoRoot() {
        // Tests run with the project directory as CWD under Gradle.
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd;
        for (int i = 0; i < 4 && candidate != null; i++) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return cwd;
    }

    /**
     * The POSIX executable bit is not modelled on Windows filesystems, so this check only makes
     * sense on POSIX. Git still stores the mode correctly either way.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testGradlewIsExecutable() {
        Path gradlew = repoRoot().resolve("gradlew");
        assertTrue(Files.exists(gradlew), "gradlew must exist at the repository root");
        assertTrue(
            Files.isExecutable(gradlew),
            "gradlew is not executable. CI runs './gradlew build' directly and will fail with "
                + "'Permission denied' (exit 126). Fix with: git update-index --chmod=+x gradlew"
        );
    }

    @Test
    void testGradleWrapperIsComplete() {
        Path root = repoRoot();
        assertTrue(Files.exists(root.resolve("gradlew")), "gradlew must be committed");
        assertTrue(Files.exists(root.resolve("gradlew.bat")), "gradlew.bat must be committed");
        assertTrue(
            Files.exists(root.resolve("gradle/wrapper/gradle-wrapper.jar")),
            "gradle-wrapper.jar must be committed, or the wrapper cannot bootstrap"
        );
        assertTrue(
            Files.exists(root.resolve("gradle/wrapper/gradle-wrapper.properties")),
            "gradle-wrapper.properties must be committed"
        );
    }

    /**
     * gradlew.bat is consumed by cmd.exe, which requires CRLF line endings. A checkout that
     * normalises it to LF produces a batch file Windows may mis-parse.
     */
    @Test
    void testGradlewBatKeepsWindowsLineEndings() throws IOException {
        Path bat = repoRoot().resolve("gradlew.bat");
        assertTrue(Files.exists(bat), "gradlew.bat must exist");

        String content = new String(Files.readAllBytes(bat), StandardCharsets.UTF_8);
        assertTrue(content.contains("\r\n"), "gradlew.bat must keep CRLF line endings for cmd.exe");
    }
}
