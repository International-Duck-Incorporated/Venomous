package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;

public class TargetObsidian extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder().name("target-range").description("How far away to look for a target.")
            .defaultValue(7.0)
            .min(0.0)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Integer> searchRadius = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("search-radius")
            .description("Radius around the target to check for existing obsidian.")
            .defaultValue(3)
            .min(1)
            .sliderMax(6)
            .build()
      );
   private final Setting<Double> placeRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder().name("place-range").description("How far away from you a block can be placed.")
            .defaultValue(4.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("ignore-friends")
            .description("Don't target friends.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("rotate")
            .description("Rotates towards the placement spot.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("swing")
            .description("Swings your hand when placing.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> placeDelay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("place-delay")
            .description("Ticks to wait between placements.")
            .defaultValue(2)
            .min(0)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("render")
            .description("Renders the block that's about to be placed.")
            .defaultValue(true)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the target block is rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("side-color")
            .description("Side color of the placement preview.")
            .defaultValue(new SettingColor(197, 137, 232, 10))
            .visible(() -> (Boolean)this.render.get() && ((ShapeMode)this.shapeMode.get()).sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("line-color")
            .description("Line color of the placement preview.")
            .defaultValue(new SettingColor(197, 137, 232))
            .visible(() -> (Boolean)this.render.get() && ((ShapeMode)this.shapeMode.get()).lines())
            .build()
      );
   private BlockPos placePos;
   private int timer;

   public TargetObsidian() {
      super(VenomousAddon.CATEGORY, "target-obsidian", "Places obsidian near your target.");
   }

   public void onActivate() {
      this.timer = 0;
      this.placePos = null;
   }

   @Override
   public void onDeactivate() {
      this.timer = 0;
      this.placePos = null;
   }

   @EventHandler
   private void onTick(Pre event) {
      this.placePos = null;
      Player target = this.findTarget();
      if (target != null) {
         if (this.timer > 0) {
            this.timer--;
         } else {
            BlockPos spot = this.scanForPlacement(target.blockPosition());
            if (spot != null) {
               FindItemResult obsidian = InvUtils.findInHotbar(new Item[]{Items.OBSIDIAN});
               if (obsidian.found()) {
                  this.placePos = spot;
                  if (BlockUtils.place(spot, obsidian, (Boolean)this.rotate.get(), 10, (Boolean)this.swing.get(), true)) {
                     this.timer = (Integer)this.placeDelay.get();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.placePos != null) {
         event.renderer.box(this.placePos, (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
      }
   }

   private Player findTarget() {
      Player closest = null;
      double closestDist = (Double)this.targetRange.get() * (Double)this.targetRange.get();

      for (Player player : this.mc.level.players()) {
         if (player != this.mc.player
            && !player.isSpectator()
            && !player.isCreative()
            && player.isAlive()
            && (!(Boolean)this.ignoreFriends.get() || Friends.get().shouldAttack(player))) {
            double dist = player.distanceToSqr(this.mc.player);
            if (!(dist > closestDist)) {
               closestDist = dist;
               closest = player;
            }
         }
      }

      return closest;
   }

   private BlockPos scanForPlacement(BlockPos center) {
      int r = (Integer)this.searchRadius.get();
      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;

      for (int x = -r; x <= r; x++) {
         for (int y = -1; y <= 1; y++) {
            for (int z = -r; z <= r; z++) {
               BlockPos pos = center.offset(x, y, z);
               if (this.mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                  return null;
               }

               if (BlockUtils.canPlace(pos) && PlayerUtils.isWithin(Vec3.atCenterOf(pos), (Double)this.placeRange.get())) {
                  double dist = center.distSqr(pos);
                  if (dist < bestDist) {
                     bestDist = dist;
                     best = pos;
                  }
               }
            }
         }
      }

      return best;
   }
}
