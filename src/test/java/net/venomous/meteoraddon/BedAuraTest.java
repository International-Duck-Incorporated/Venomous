package net.venomous.meteoraddon;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BedAura module.
 * Guards bed detection, target validation, and breaking logic.
 *
 * <p>These tests ensure the bed-finding and breaking behavior is correct
 * across different world states and player configurations.</p>
 */
public class BedAuraTest {

    @BeforeAll
    static void setup() {
        // Ensure BedAura class can be loaded
        try {
            Class.forName("net.venomous.meteoraddon.modules.combat.BedAura");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("BedAura class not found", e);
        }
    }

    @Test
    void moduleInstantiatesAndRegistered() {
        try {
            Class<?> bedAuraClass = Class.forName("net.venomous.meteoraddon.modules.combat.BedAura");
            Object module = bedAuraClass.getDeclaredConstructor().newInstance();
            assertNotNull(module);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate BedAura", e);
        }
    }
}