package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class AntiVanishLogicTest {
    @Test
    void testVanishDetectionViaMsgAutocomplete() {
        // Tablist visible players
        Set<String> tablist = Set.of("alice", "bob", "steve");

        // Autocomplete suggestions returned by /msg
        List<String> msgSuggestions = List.of("Alice", "Bob", "Steve", "HiddenAdmin", "StealthMod");

        Set<String> vanished = new HashSet<>();
        for (String name : msgSuggestions) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!tablist.contains(lower)) {
                vanished.add(name);
            }
        }

        assertEquals(2, vanished.size());
        assertTrue(vanished.contains("HiddenAdmin"));
        assertTrue(vanished.contains("StealthMod"));
        assertFalse(vanished.contains("Alice"));
    }
}
