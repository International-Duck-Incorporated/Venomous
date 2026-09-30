package net.venomous.meteoraddon.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.VenomousAddon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Plants one flat layer of bamboo in a configurable square around the player. */
public final class BambooPlanter extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Integer> areaSize = this.sgGeneral.add(new IntSetting.Builder()
      .name("area-size")
      .description("Width and length of the square planting area.")
      .defaultValue(5)
      .range(1, 15)
      .sliderRange(1, 15)
      .build());

   private final Setting<Integer> placeDelay = this.sgGeneral.add(new IntSetting.Builder()
      .name("place-delay")
      .description("Ticks between successful bamboo placements.")
      .defaultValue(2)
      .range(0, 20)
      .sliderRange(0, 20)
      .build());

   private final Setting<Boolean> rotate = this.sgGeneral.add(new BoolSetting.Builder()
      .name("rotate")
      .description("Rotate server-side toward each placement.")
      .defaultValue(true)
      .build());

   private final Setting<Boolean> swing = this.sgGeneral.add(new BoolSetting.Builder()
      .name("swing")
      .description("Swing the hand when placing bamboo.")
      .defaultValue(true)
      .build());

   private int cooldown;

   public BambooPlanter() {
      super(VenomousAddon.CATEGORY, "bamboo-planter", "Plants a single-height X by X layer of bamboo on valid ground.");
   }

   @Override
   public void onActivate() {
      this.cooldown = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) return;
      if (this.cooldown > 0) {
         this.cooldown--;
         return;
      }

      FindItemResult bamboo = InvUtils.findInHotbar(Items.BAMBOO);
      if (!bamboo.found()) return;

      for (BlockPos target : this.targetsNearestFirst()) {
         if (!this.canPlantOneHigh(target)) continue;
         if (BlockUtils.place(target, bamboo, this.rotate.get(), 10, this.swing.get(), true)) {
            this.cooldown = this.placeDelay.get();
            return;
         }
      }
   }

   private List<BlockPos> targetsNearestFirst() {
      int size = this.areaSize.get();
      int start = -(size / 2);
      int end = start + size;
      int plantingY = this.mc.player.blockPosition().getY();
      BlockPos center = this.mc.player.blockPosition();
      List<BlockPos> targets = new ArrayList<>(size * size);

      for (int x = start; x < end; x++) {
         for (int z = start; z < end; z++) {
            targets.add(new BlockPos(center.getX() + x, plantingY, center.getZ() + z));
         }
      }

      targets.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(this.mc.player.position())));
      return targets;
   }

   private boolean canPlantOneHigh(BlockPos target) {
      if (!this.mc.level.getBlockState(target).isAir()) return false;
      BlockState below = this.mc.level.getBlockState(target.below());
      if (below.is(Blocks.BAMBOO) || below.is(Blocks.BAMBOO_SAPLING)) return false;
      return Blocks.BAMBOO_SAPLING.defaultBlockState().canSurvive(this.mc.level, target)
         && BlockUtils.canPlace(target);
   }
}
