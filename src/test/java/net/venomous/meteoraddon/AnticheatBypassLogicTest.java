package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

public class AnticheatBypassLogicTest {

    @Test
    void testGcdQuantizationCalculation() {
        double sensitivity = 0.5; // Standard 100% or 50% sensitivity
        double f = sensitivity * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;

        assertTrue(gcd > 0.001, "GCD must be a positive non-zero value");

        float rawDelta = 12.34567f;
        float quantizedDelta = (float)(Math.round(rawDelta / gcd) * gcd);

        // Difference must be within half a step
        assertTrue(Math.abs(rawDelta - quantizedDelta) <= gcd / 2.0 + 1e-5);
    }

    @Test
    void testVulcanTransactionRange() {
        int minId = -30767;
        int maxId = -28767;

        int sample1 = -30000;
        int sample2 = -25000;

        assertTrue(sample1 >= minId && sample1 <= maxId, "Sample 1 should fall within Vulcan transaction range");
        assertFalse(sample2 >= minId && sample2 <= maxId, "Sample 2 should not fall within Vulcan transaction range");
    }

    @Test
    void testStrafeFrictionMath() {
        double baseSpeed = 0.2873;
        double speedAmpMultiplier = 1.0 + 0.2 * (1 + 1); // Speed II
        double boosted = baseSpeed * speedAmpMultiplier;

        assertEquals(0.40222, boosted, 1e-4);
        assertTrue(boosted > baseSpeed);
    }

    @Test
    void testStaffRankPrefixPattern() {
        Pattern pattern = Pattern.compile(
            "(?i).*(\\[(admin|mod|moderator|owner|staff|helper|dev|developer|builder|trainee|operator|gm|guard)\\]|\\b(admin|moderator|staff)\\b).*"
        );

        assertTrue(pattern.matcher("[Admin] Alice").matches());
        assertTrue(pattern.matcher("[MOD] Bob").matches());
        assertTrue(pattern.matcher("[Staff] Charlie").matches());
        assertTrue(pattern.matcher("Senior Admin Dave").matches());
        assertFalse(pattern.matcher("NormalPlayer123").matches());
        assertFalse(pattern.matcher("[VIP] CoolDude").matches());
    }

    @Test
    void testBotUuidVersionFiltering() {
        // Legitimate players have UUID Version 4
        UUID legitUuid = UUID.randomUUID();
        assertEquals(4, legitUuid.version(), "Random Java UUID is version 4");

        // Offline / NPC plugins often generate Name-based UUIDs (Version 3)
        UUID npcOfflineUuid = UUID.nameUUIDFromBytes("CitizenNPC".getBytes());
        assertEquals(3, npcOfflineUuid.version(), "Offline / NPC UUID is version 3");
    }
}
