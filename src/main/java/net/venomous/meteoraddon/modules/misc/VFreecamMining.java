package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class VFreecamMining extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("Maximum range to mine blocks from freecam position.")
            .defaultValue(64.0)
            .min(4.0)
            .max(256.0)
            .sliderMax(128.0)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate towards the block before mining.")
            .defaultValue(false)
            .build()
      );

   public VFreecamMining() {
      super(VenomousAddon.CATEGORY, "v-freecam-mining", "Allows mining blocks while freecam is active.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) {
         return;
      }

      Freecam freecam = (Freecam)Modules.get().get(Freecam.class);
      if (freecam != null && freecam.isActive() && this.mc.options.keyAttack.isDown()) {
         Vec3 eyePos = freecam.getX(1.0F) != 0 ? new Vec3(freecam.getX(1.0F), freecam.getY(1.0F), freecam.getZ(1.0F)) : this.mc.player.getEyePosition();
         float yaw = (float) freecam.getYaw(1.0F);
         float pitch = (float) freecam.getPitch(1.0F);

         float f = (float) Math.cos(-yaw * 0.017453292F - 3.1415927F);
         float f1 = (float) Math.sin(-yaw * 0.017453292F - 3.1415927F);
         float f2 = (float) -Math.cos(-pitch * 0.017453292F);
         float f3 = (float) Math.sin(-pitch * 0.017453292F);
         Vec3 lookVec = new Vec3(f1 * f2, f3, f * f2);
         Vec3 reachVec = eyePos.add(lookVec.scale((Double)this.range.get()));

         BlockHitResult hit = this.mc.level.clip(new ClipContext(eyePos, reachVec, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));
         if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if ((Boolean)this.rotate.get()) {
               Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos));
            }
            this.mc.gameMode.continueDestroyBlock(pos, hit.getDirection());
            this.mc.player.swing(InteractionHand.MAIN_HAND);
         }
      }
   }

   public boolean isActiveAndFreecamOn() {
      return this.isActive() && Modules.get().isActive(Freecam.class);
   }

   public double getExtendedRange() {
      return this.isActive() ? (Double)this.range.get() : 4.5;
   }
}
