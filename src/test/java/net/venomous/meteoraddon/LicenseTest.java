package net.venomous.meteoraddon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the project's licensing.
 *
 * <p>Venomous links against Meteor Client, which is GPL-3.0. A work that links GPL-3.0 code must
 * itself be distributed under the GPL, so the LICENSE file and the {@code license} field in
 * {@code fabric.mod.json} are a compliance requirement rather than a formality. These tests stop
 * either from being dropped or quietly changed to an incompatible licence.
 */
public class LicenseTest {

    private static Path repoRoot() {
        Path candidate = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && candidate != null; i++) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    @Test
    void testLicenseFileIsPresentAndIsGpl3() throws Exception {
        Path license = repoRoot().resolve("LICENSE");
        assertTrue(Files.exists(license), "LICENSE must exist at the repository root");

        String text = new String(Files.readAllBytes(license), StandardCharsets.UTF_8);
        assertTrue(
            text.contains("GNU GENERAL PUBLIC LICENSE"),
            "LICENSE must be the GNU GPL - Venomous links GPL-3.0 code and cannot be relicensed"
        );
        assertTrue(text.contains("Version 3, 29 June 2007"), "LICENSE must be version 3 of the GPL");

        // Sanity check that the full text is present rather than a truncated stub.
        assertTrue(text.contains("TERMS AND CONDITIONS"), "LICENSE looks truncated");
        assertTrue(text.contains("END OF TERMS AND CONDITIONS"), "LICENSE looks truncated");
    }

    @Test
    void testFabricModJsonDeclaresGpl() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(is, "fabric.mod.json should exist in classpath resources");

            JsonObject json = JsonParser
                .parseReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .getAsJsonObject();

            assertTrue(json.has("license"), "fabric.mod.json must declare a license field");
            assertEquals(
                "GPL-3.0-or-later",
                json.get("license").getAsString(),
                "Venomous links Meteor Client (GPL-3.0) and must stay GPL-compatible"
            );
        }
    }
}
