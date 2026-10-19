package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the deploy script.
 *
 * <p>This exists because of a mistake that shipped silently. The deploy command named the jar
 * explicitly, {@code build/libs/venomous-7.6.0.jar}, while the version in the tree was 7.12.0.
 * {@code build/libs} accumulates every jar that has ever been built, so the stale one was sitting
 * there next to the current one, the copy succeeded, and the game ran a build that was several
 * versions old. Nothing in the build failed and the only symptom was the version in the game's
 * startup banner.</p>
 */
public class DeployScriptTest {
    private static Path repoRoot() {
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

    private static String script() throws IOException {
        Path path = repoRoot().resolve("scripts/deploy.ps1");
        assertTrue(Files.isRegularFile(path), "scripts/deploy.ps1 is missing");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The script without its comments, so a version in the prose is not mistaken for code. */
    private static String code(String script) {
        StringBuilder out = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * The version has to be read out of the build, not typed into the command.
     */
    @Test
    void theVersionIsReadFromTheBuildRatherThanHardCoded() throws IOException {
        // Comments are stripped: the script explains the mistake in prose, and that prose is
        // allowed to name a version. What must not happen is a version in the code.
        String script = code(script());

        assertTrue(script.contains("mod-version"),
            "the deploy script must read the version out of gradle/libs.versions.toml");

        assertTrue(script.contains("venomous-$version.jar"),
            "the jar it copies has to be named from that version, not written out literally");

        assertFalse(script.contains("venomous-7."),
            "a hard-coded venomous-<version>.jar in the deploy script is exactly the mistake that "
                + "shipped a stale build; the version belongs in libs.versions.toml and nowhere else");
    }

    /**
     * And the old jars have to go, so two versions can never be loaded at once and the stale one
     * cannot be picked up by accident.
     */
    @Test
    void theOldJarsAreClearedOutBeforeAndAfter() throws IOException {
        String script = code(script());

        assertTrue(script.contains("build\\libs") || script.contains("build/libs"),
            "the script must clear build/libs so there is only one candidate jar to copy");

        assertTrue(script.contains("venomous-*.jar"),
            "and it must clear the old venomous jars out of the mods folder, or two versions of the "
                + "addon can be loaded at once");
    }

    /**
     * The deployed file has to be checked against the built one, so a copy that went somewhere
     * unexpected is caught rather than assumed.
     */
    @Test
    void theDeployedJarIsCheckedAgainstTheBuiltOne() throws IOException {
        String script = code(script());

        assertTrue(script.contains("Get-FileHash") && script.contains("SHA256"),
            "the script must compare the hash of the built jar with the deployed one");

        assertTrue(script.contains("ModsPath"),
            "and it must deploy to the instance's mods folder");
    }
}
