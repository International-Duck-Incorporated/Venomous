package net.venomous.meteoraddon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.modules.combat.VAutoCity;
import net.venomous.meteoraddon.modules.movement.HoleSnap;
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
        assertTrue(HoleSnap.isBlastResistant(Blocks.BEDROCK.defaultBlockState()));
        assertTrue(HoleSnap.isBlastResistant(Blocks.OBSIDIAN.defaultBlockState()));
        assertTrue(HoleSnap.isBlastResistant(Blocks.CRYING_OBSIDIAN.defaultBlockState()));
        assertTrue(HoleSnap.isBlastResistant(Blocks.ENDER_CHEST.defaultBlockState()));
        assertTrue(HoleSnap.isBlastResistant(Blocks.RESPAWN_ANCHOR.defaultBlockState()));
        assertTrue(HoleSnap.isBlastResistant(Blocks.NETHERITE_BLOCK.defaultBlockState()));

        assertFalse(HoleSnap.isBlastResistant(Blocks.DIRT.defaultBlockState()));
        assertFalse(HoleSnap.isBlastResistant(Blocks.STONE.defaultBlockState()));
        assertFalse(HoleSnap.isBlastResistant(Blocks.AIR.defaultBlockState()));
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
