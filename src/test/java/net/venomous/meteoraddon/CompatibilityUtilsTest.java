package net.venomous.meteoraddon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.utils.McCompat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompatibilityUtilsTest {
    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testWeaponChecks() {
        assertTrue(McCompat.isSword(Items.NETHERITE_SWORD));
        assertTrue(McCompat.isSword(Items.DIAMOND_SWORD));
        assertTrue(McCompat.isSword(Items.IRON_SWORD));
        assertTrue(McCompat.isSword(Items.WOODEN_SWORD));
        assertFalse(McCompat.isSword(Items.NETHERITE_PICKAXE));
        assertFalse(McCompat.isSword(Items.DIRT));

        assertTrue(McCompat.isAxe(Items.NETHERITE_AXE));
        assertTrue(McCompat.isAxe(Items.DIAMOND_AXE));
        assertTrue(McCompat.isAxe(Items.IRON_AXE));
        assertFalse(McCompat.isAxe(Items.BOW));

        assertTrue(McCompat.isWeapon(Items.NETHERITE_SWORD));
        assertTrue(McCompat.isWeapon(Items.DIAMOND_AXE));
        assertTrue(McCompat.isWeapon(Items.MACE));
        assertFalse(McCompat.isWeapon(Items.STICK));
    }

    @Test
    void testStreamSupport() {
        List<String> items = List.of("a", "b", "c");
        long count = McCompat.stream(items).count();
        assertEquals(3, count);
    }

}
