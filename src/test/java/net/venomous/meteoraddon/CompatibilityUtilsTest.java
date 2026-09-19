package net.venomous.meteoraddon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.utils.InventoryUtils;
import net.venomous.meteoraddon.utils.McCompat;
import net.venomous.meteoraddon.utils.TickTimer;
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

    @Test
    void testTickTimer() {
        TickTimer timer = new TickTimer();
        assertTrue(timer.ready());
        assertEquals(0, timer.getTicksLeft());

        timer.reset(5);
        assertFalse(timer.ready());
        assertEquals(5, timer.getTicksLeft());

        timer.tick();
        assertEquals(4, timer.getTicksLeft());

        for (int i = 0; i < 4; i++) {
            timer.tick();
        }
        assertTrue(timer.ready());
        assertEquals(0, timer.getTicksLeft());

        assertTrue(timer.readyAndReset(10));
        assertFalse(timer.ready());
        assertEquals(10, timer.getTicksLeft());

        timer.clear();
        assertTrue(timer.ready());
        assertEquals(0, timer.getTicksLeft());
    }

    @Test
    void testInventorySlotBounds() {
        assertTrue(InventoryUtils.isValidHotbarSlot(0));
        assertTrue(InventoryUtils.isValidHotbarSlot(4));
        assertTrue(InventoryUtils.isValidHotbarSlot(8));
        assertFalse(InventoryUtils.isValidHotbarSlot(-1));
        assertFalse(InventoryUtils.isValidHotbarSlot(9));

        assertTrue(InventoryUtils.isValidPlayerInventorySlot(0));
        assertTrue(InventoryUtils.isValidPlayerInventorySlot(35));
        assertFalse(InventoryUtils.isValidPlayerInventorySlot(-1));
        assertFalse(InventoryUtils.isValidPlayerInventorySlot(36));
    }

    @Test
    void testDurabilityPercent() {
        assertEquals(100, InventoryUtils.getDurabilityPercent(ItemStack.EMPTY));
    }
}
