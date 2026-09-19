package net.venomous.meteoraddon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.modules.combat.VAutoCity;
import net.venomous.meteoraddon.modules.movement.VHoleSnap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScavengedModulesLogicTest {
    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testHoleSnapBlastResistantBlocks() {
        assertTrue(VHoleSnap.isBlastResistant(Blocks.BEDROCK.defaultBlockState()));
        assertTrue(VHoleSnap.isBlastResistant(Blocks.OBSIDIAN.defaultBlockState()));
        assertTrue(VHoleSnap.isBlastResistant(Blocks.CRYING_OBSIDIAN.defaultBlockState()));
        assertTrue(VHoleSnap.isBlastResistant(Blocks.ENDER_CHEST.defaultBlockState()));
        assertTrue(VHoleSnap.isBlastResistant(Blocks.RESPAWN_ANCHOR.defaultBlockState()));
        assertTrue(VHoleSnap.isBlastResistant(Blocks.NETHERITE_BLOCK.defaultBlockState()));

        assertFalse(VHoleSnap.isBlastResistant(Blocks.DIRT.defaultBlockState()));
        assertFalse(VHoleSnap.isBlastResistant(Blocks.STONE.defaultBlockState()));
        assertFalse(VHoleSnap.isBlastResistant(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void testAutoCityMineableBlocks() {
        assertTrue(VAutoCity.isMineableSurroundBlock(Blocks.OBSIDIAN.defaultBlockState()));
        assertTrue(VAutoCity.isMineableSurroundBlock(Blocks.CRYING_OBSIDIAN.defaultBlockState()));
        assertTrue(VAutoCity.isMineableSurroundBlock(Blocks.ENDER_CHEST.defaultBlockState()));
        assertTrue(VAutoCity.isMineableSurroundBlock(Blocks.RESPAWN_ANCHOR.defaultBlockState()));
        assertTrue(VAutoCity.isMineableSurroundBlock(Blocks.ANVIL.defaultBlockState()));

        assertFalse(VAutoCity.isMineableSurroundBlock(Blocks.BEDROCK.defaultBlockState()));
        assertFalse(VAutoCity.isMineableSurroundBlock(Blocks.COBBLESTONE.defaultBlockState()));
        assertFalse(VAutoCity.isMineableSurroundBlock(Blocks.SAND.defaultBlockState()));
    }

    @Test
    void testAutoEZTemplateFormatting() {
        String template = "GG {player}! Kills: {kills}, HP: {hp}";
        String formatted = template
            .replace("{player}", "TargetDuck")
            .replace("{kills}", "7")
            .replace("{hp}", "18.5");

        assertEquals("GG TargetDuck! Kills: 7, HP: 18.5", formatted);
    }
}
