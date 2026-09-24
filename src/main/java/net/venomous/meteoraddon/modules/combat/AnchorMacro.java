package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class AnchorMacro extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> autoFind = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("auto-find")).description("Automatically find nearby anchors.")).defaultValue(true)).build());
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("Range for auto-finding anchors."))
            .defaultValue(5.0)
            .min(1.0)
            .sliderMax(6.0)
            .build()
      );
   private int timer;

   public AnchorMacro() {
      super(VenomousAddon.CATEGORY, "anchor-macro", "Smart anchor charger and exploder.");
   }

   public void onActivate() {
      this.timer = 0;
   }

   @Override
   public void onDeactivate() {
      this.timer = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.timer > 0) {
            this.timer--;
         } else {
            BlockPos targetPos = null;
            if (this.mc.hitResult instanceof BlockHitResult bhr && this.mc.level.getBlockState(bhr.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
               targetPos = bhr.getBlockPos();
            } else if ((Boolean)this.autoFind.get()) {
               targetPos = this.findAnchor();
            }

            if (targetPos != null) {
               int charges = (Integer)this.mc.level.getBlockState(targetPos).getValue(RespawnAnchorBlock.CHARGE);
               BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(targetPos), Direction.UP, targetPos, false);
               if (charges < 4) {
                  FindItemResult glowstone = InvUtils.findInHotbar(new Item[]{Items.GLOWSTONE});
                  if (glowstone.found()) {
                     InvUtils.swap(glowstone.slot(), true);
                     this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, bhr);
                     InvUtils.swapBack();
                     this.timer = 1;
                  }
               } else {
                  this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, bhr);
                  this.timer = 1;
               }
            }
         }
      }
   }

   private BlockPos findAnchor() {
      BlockPos playerPos = this.mc.player.blockPosition();
      double r = (Double)this.range.get();

      for (int x = (int)(-r); x <= r; x++) {
         for (int y = (int)(-r); y <= r; y++) {
            for (int z = (int)(-r); z <= r; z++) {
               BlockPos p = playerPos.offset(x, y, z);
               if (this.mc.level.getBlockState(p).is(Blocks.RESPAWN_ANCHOR)) {
                  return p;
               }
            }
         }
      }

      return null;
   }
}
