package net.venomous.meteoraddon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddonMetadataTest {
    @Test
    void testAddonProperties() {
        VenomousAddon addon = new VenomousAddon();
        assertEquals("7.1.0", VenomousAddon.VERSION);
        assertEquals("net.venomous.meteoraddon", addon.getPackage());
        assertNotNull(addon.getRepo());
        assertEquals("International-Duck-Incorporated", addon.getRepo().owner());
        assertEquals("Venomous", addon.getRepo().name());
        assertEquals("Venomous", VenomousAddon.CATEGORY.name);
        assertEquals("Venomous OP", VenomousAddon.CATEGORY_OP.name);
    }

    /**
     * Guards a drift that already happened: the jar shipped as 7.0.1 while
     * {@link VenomousAddon#VERSION} still said 7.0.0, so the startup banner lied. The build
     * numbers the jar from {@code libs.versions.toml}, so the constant must be bumped with it.
     */
    /**
     * Two places state who made this: {@code fabric.mod.json}'s {@code authors} and
     * {@link VenomousAddon#getRepo()}'s owner. They had already drifted - the jar credited a
     * personal handle while the repo said the organisation - and there is nothing else in the build
     * that would notice.
     *
     * <p>Reads the packaged resource rather than the source file, so it is the {@code authors} the
     * shipped jar actually carries that gets checked, and punctuation differences between the org's
     * display name and its slug are not treated as a mismatch.</p>
     */
    @Test
    void packagedAuthorsAgreeWithTheRepositoryOwner() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(is, "fabric.mod.json should exist in classpath resources");

            JsonObject json = JsonParser
                .parseReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .getAsJsonObject();

            assertTrue(json.has("authors") && json.getAsJsonArray("authors").size() > 0,
                "fabric.mod.json must credit at least one author");

            String owner = new VenomousAddon().getRepo().owner();

            for (JsonElement element : json.getAsJsonArray("authors")) {
                assertEquals(normalise(owner), normalise(element.getAsString()),
                    "fabric.mod.json credits \"" + element.getAsString() + "\" but the repository "
                        + "owner is \"" + owner + "\". Anyone reading the mod listing should be told "
                        + "the same organisation the source lives under.");
            }
        }
    }

    /** Case- and punctuation-insensitive, so "International Duck Inc." matches its slug form. */
    private static String normalise(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Test
    void versionConstantMatchesTheVersionTheBuildStampsIntoTheJar() throws IOException {
        String toml = Files.readString(locate("gradle/libs.versions.toml"));
        String declared = null;

        for (String line : toml.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("mod-version")) continue;
            int firstQuote = trimmed.indexOf('"');
            int lastQuote = trimmed.lastIndexOf('"');
            assertTrue(firstQuote >= 0 && lastQuote > firstQuote,
                "mod-version in libs.versions.toml has no quoted value: " + trimmed);
            declared = trimmed.substring(firstQuote + 1, lastQuote);
            break;
        }

        assertNotNull(declared, "libs.versions.toml no longer declares mod-version");
        assertEquals(declared, VenomousAddon.VERSION,
            "VenomousAddon.VERSION has drifted from the version the build actually stamps into "
                + "the jar. Bump both together, or the startup banner will advertise the wrong one.");
    }

    /**
     * The banner used to spell the version out a second time, so bumping the jar and the
     * constant could each be forgotten independently. Requires the version to be written down
     * exactly once, in the constant everything else must read.
     */
    @Test
    void versionIsWrittenDownExactlyOnceInTheAddonClass() throws IOException {
        String code = stripComments(Files.readString(
            locate("src/main/java/net/venomous/meteoraddon/VenomousAddon.java")));

        List<String> literals = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]*)\"").matcher(code);
        Pattern version = Pattern.compile("\\d+\\.\\d+\\.\\d+");

        while (matcher.find()) {
            // Substring match, not full match: the banner reads "Initializing Venomous V7.1.0 -
            // ...", and that trailing prose is exactly the case this guard exists to catch.
            if (version.matcher(matcher.group(1)).find()) literals.add(matcher.group(1));
        }

        assertEquals(List.of(VenomousAddon.VERSION), literals,
            "Every version-shaped string literal in VenomousAddon must be the VERSION constant "
                + "itself. Found " + literals + " - a second one is a banner or message that will "
                + "quietly go stale on the next bump; reference VenomousAddon.VERSION instead.");
    }

    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /** Gradle can run tests from the project root or from a subproject, so search upward. */
    private static Path locate(String relative) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            dir = dir.getParent();
        }

        throw new AssertionError("could not find " + relative + " above " + System.getProperty("user.dir"));
    }
}
