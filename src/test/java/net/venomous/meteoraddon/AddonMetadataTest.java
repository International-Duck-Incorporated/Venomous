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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddonMetadataTest {
    @Test
    void testAddonProperties() {
        VenomousAddon addon = new VenomousAddon();
        assertEquals("7.13.0", VenomousAddon.VERSION);
        assertEquals("net.venomous.meteoraddon", addon.getPackage());
        assertEquals("Venomous", VenomousAddon.CATEGORY.name);
        assertEquals("Venomous OP", VenomousAddon.CATEGORY_OP.name);
    }

    /**
     * No repository is declared on purpose - the project is on GitLab and Meteor's addon API can
     * only describe GitHub.
     *
     * <p>Pinned because the failure mode is silent: handing back a {@code GithubRepo} would still
     * compile, still pass every behavioural check, and simply render a github.com link to a project
     * that is no longer the source of truth. Meteor treats {@code null} as "no repo" and guards on
     * it - {@code TitleScreenCredits} null-checks before {@code CommitsScreen} reads it - so the
     * only real cost is the title screen's commit link disappearing.</p>
     */
    @Test
    void noGithubRepoIsDeclaredBecauseTheProjectMovedToGitLab() {
        assertNull(new VenomousAddon().getRepo(),
            "Meteor's GithubRepo is a final record with no host field, so any value returned here "
                + "renders a github.com link. The project lives on GitLab - leave this null.");
    }

    /**
     * The only place a user is shown where the source actually lives.
     *
     * <p>With {@code getRepo()} returning null the in-client link is gone, which makes this field
     * load-bearing: it was still pointing at GitHub after the migration.</p>
     */
    @Test
    void packagedSourcesLinkPointsAtGitLabNotGitHub() throws IOException {
        JsonObject json = packagedModJson();

        assertTrue(json.has("contact") && json.getAsJsonObject("contact").has("sources"),
            "fabric.mod.json must state where the source lives");

        String sources = json.getAsJsonObject("contact").get("sources").getAsString();

        assertFalse(sources.contains("github.com"),
            "fabric.mod.json still advertises github.com (" + sources + "). The project moved to "
                + "GitLab, and since getRepo() is null this is the only source link a user gets.");

        assertTrue(sources.contains("gitlab.com/international-duck-incorporated"),
            "sources should point at the GitLab group, got " + sources);
    }

    /**
     * The credited author and the hosting organisation have to name the same body.
     *
     * <p>Taken from the {@code contact.sources} URL rather than a hardcoded owner, so there is no
     * third copy of the name to drift - move the project and this follows. They previously
     * disagreed: the jar credited a personal handle while the project already sat under the
     * organisation.</p>
     */
    @Test
    void packagedAuthorsAgreeWithTheHostingOrganisation() throws IOException {
        JsonObject json = packagedModJson();

        assertTrue(json.has("authors") && json.getAsJsonArray("authors").size() > 0,
            "fabric.mod.json must credit at least one author");

        String organisation = orgFromSources(
            json.getAsJsonObject("contact").get("sources").getAsString());

        for (JsonElement element : json.getAsJsonArray("authors")) {
            assertEquals(normalise(organisation), normalise(element.getAsString()),
                "fabric.mod.json credits \"" + element.getAsString() + "\" but the project is "
                    + "hosted under \"" + organisation + "\". Anyone reading the mod listing should "
                    + "be told the same organisation the source lives under.");
        }
    }

    /** First path segment of the sources URL: gitlab.com/our-org/our-project -> our-org. */
    private static String orgFromSources(String sources) {
        Matcher matcher = Pattern.compile("^https?://[^/]+/([^/]+)/[^/]+/?$").matcher(sources);
        assertTrue(matcher.matches(),
            "contact.sources should be a plain repository URL like "
                + "https://gitlab.com/<org>/<project>, got " + sources);
        return matcher.group(1);
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
     * The banner used to spell the version out a second time, so bumping the jar and the constant
     * could each be forgotten independently. Requires the version to be written down exactly once,
     * in the constant everything else must read.
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

    /**
     * The packaged resource, not the source file, so what is asserted is the metadata the shipped
     * jar actually carries after Gradle's resource filtering.
     */
    private static JsonObject packagedModJson() throws IOException {
        try (InputStream is = AddonMetadataTest.class.getClassLoader()
                .getResourceAsStream("fabric.mod.json")) {
            assertNotNull(is, "fabric.mod.json should exist in classpath resources");

            return JsonParser
                .parseReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    /** Case- and punctuation-insensitive, so "International Duck Inc." matches its slug form. */
    private static String normalise(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
