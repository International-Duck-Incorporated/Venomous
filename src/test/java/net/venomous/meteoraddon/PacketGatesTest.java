package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PacketGates is a utility, not a module - {@code ModuleRegistry} auto-registers Module
 * subclasses, and PacketGates deliberately is not one. This test asserts the two things the
 * utility's existence contract actually consists of:
 *
 * <ol>
 *   <li>the utility class loads, and</li>
 *   <li>it is wired into the network modules that gate packets, so "exists but never used"
 *       cannot regress (FakeLag and LagRange consult {@code isRealtimePassthrough} before
 *       holding a packet, and if either stops consulting it the hold-the-realtime-packets
 *       guarantee is gone).</li>
 * </ol>
 *
 * <p>The previous assertion read the hand-maintained {@code ModuleRegistry.java} source for the
 * name "PacketGates"; that file no longer lists modules at all (reflection auto-registration),
 * so the source-text check was meaningless twice over.</p>
 */
public class PacketGatesTest {

    @Test
    void packetGatesUtilityLoads() throws Exception {
        Class<?> pg = Class.forName("net.venomous.meteoraddon.utils.PacketGates");
        assertNotNull(pg);
    }

    @Test
    void packetGatesGatesANetworkModule() throws IOException {
        Path network = Path.of("src/main/java/net/venomous/meteoraddon/modules/network");

        // Suite root layout sanity: the walk below is only meaningful if it started somewhere real.
        assertTrue(Files.isDirectory(network), "network module folder must exist");

        boolean wired;
        try (Stream<Path> files = Files.walk(network)) {
            wired = files
                .filter(p -> p.toString().endsWith(".java"))
                .anyMatch(p -> {
                    try {
                        return Files.readString(p).contains("PacketGates.isRealtimePassthrough");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        assertTrue(wired,
            "no network module consults PacketGates.isRealtimePassthrough - the utility is "
                + "unwired, which is the failure mode it was written to prevent");
    }
}
