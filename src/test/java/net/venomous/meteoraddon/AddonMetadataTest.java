package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
