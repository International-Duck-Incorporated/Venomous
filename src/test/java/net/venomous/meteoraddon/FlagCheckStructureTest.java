package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@code FlagCheck} to the rules {@link FlagCheckLogicTest} exercises.
 *
 * <p>FlagCheck decides flag-versus-teleport-reply from packet handlers, so it cannot be unit
 * tested directly without a running client and a server that misbehaves. The decision logic is
 * modelled in FlagCheckLogicTest instead. That leaves a gap - the model could drift away from
 * what the module actually does - so this test asserts the real source still implements the same
 * four rules the model encodes.</p>
 */
public class FlagCheckStructureTest {
    private static final Path FLAG_CHECK = Path.of(
        "src/main/java/net/venomous/meteoraddon/modules/misc/FlagCheck.java");

    private static String source() throws IOException {
        return Files.readString(FLAG_CHECK, StandardCharsets.UTF_8);
    }

    @Test
    void stillDistinguishesTeleportRepliesFromSetbacks() throws IOException {
        String code = source();

        assertTrue(code.contains("instanceof ServerboundAcceptTeleportationPacket"),
            "an outbound accept-teleport must still arm the grace window");
        assertTrue(code.contains("teleportRequestedAt = System.currentTimeMillis()"),
            "the grace window must be timestamped at the moment of the request");
        assertTrue(code.contains("now - this.teleportRequestedAt <= (Integer)this.graceTicks.get()"),
            "the grace window must be compared against the grace-ticks setting");
        assertTrue(code.contains("this.teleportRequestedAt = 0L;"),
            "the grace window must be consumed so it cannot cover a later genuine flag");
    }

    @Test
    void stillIgnoresSubBlockNudges() throws IOException {
        assertTrue(source().contains("displacement < (Double)this.minDisplacement.get()"),
            "the displacement floor must still apply, or chunk rounding reads as a flag");
    }

    @Test
    void stillRecordsAndAgesFlags() throws IOException {
        String code = source();

        assertTrue(code.contains("this.flags.addLast"), "a qualifying correction must be recorded");
        assertTrue(code.contains("FLAG_MEMORY_MS"), "flags must age out rather than grow forever");
        assertTrue(code.contains("recentFlagCount"), "recent flags must be readable");
    }

    @Test
    void stillResetsEverythingOnToggle() throws IOException {
        String code = source();

        List<String> missing = new ArrayList<>();
        for (String field : List.of("this.flags.clear()", "this.teleportRequestedAt = 0L",
                "this.flagsThisSession = 0", "this.lastAnnouncedAt = 0L")) {
            if (!code.contains(field)) missing.add(field);
        }

        assertEquals(List.of(), missing,
            "state surviving a toggle would misreport the next session's flags: " + missing);
    }

    @Test
    void theStubModuleWasActuallyReplaced() throws IOException {
        // FlagCheck shipped as seventeen lines with a single setting and no event handlers at
        // all. Guard that so it cannot quietly revert to a shell.
        String code = source();

        assertTrue(code.contains("@EventHandler"), "FlagCheck must subscribe to packet events");
        assertTrue(code.contains("ClientboundPlayerPositionPacket"),
            "the setback packet is the whole basis of the module");
        assertTrue(code.lines().count() > 80,
            "FlagCheck is suspiciously short - did the implementation get reverted?");
    }
}
