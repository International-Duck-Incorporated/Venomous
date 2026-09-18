package net.venomous.meteoraddon.modules.fun;

import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.venomous.meteoraddon.VenomousAddon;

public class Derp extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Derp.YawMode> yawMode = this.sgGeneral
      .add(((Builder)((Builder)new Builder().name("yaw-mode")).defaultValue(Derp.YawMode.RANDOM)).build());
   private final Setting<Derp.PitchMode> pitchMode = this.sgGeneral
      .add(((Builder)((Builder)new Builder().name("pitch-mode")).defaultValue(Derp.PitchMode.RANDOM)).build());
   private final Setting<Boolean> handDerp = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                  .name("hand-derp"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> serverSide = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                  .name("server-side-only"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> safePitch = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                  .name("safe-pitch"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> spinSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("spin-speed"))
               .defaultValue(50.0)
               .min(-70.0)
               .max(70.0)
               .visible(() -> this.yawMode.get() == Derp.YawMode.SPIN))
            .build()
      );
   private float spinYaw = 0.0F;

   public Derp() {
      super(VenomousAddon.CATEGORY, "v-derp", "Makes it look like you're derping around.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         float yaw = switch ((Derp.YawMode)this.yawMode.get()) {
            case RANDOM -> ThreadLocalRandom.current().nextFloat() * 360.0F - 180.0F;
            case SPIN -> {
               this.spinYaw = this.spinYaw + ((Double)this.spinSpeed.get()).floatValue();
               yield this.spinYaw;
            }
            case STATIC -> 0.0F;
         };

         float pitch = switch ((Derp.PitchMode)this.pitchMode.get()) {
            case RANDOM -> this.safePitch.get()
               ? ThreadLocalRandom.current().nextFloat() * 180.0F - 90.0F
               : ThreadLocalRandom.current().nextFloat() * 360.0F - 180.0F;
            case STATIC -> -90.0F;
         };
         if ((Boolean)this.serverSide.get()) {
            Rotations.rotate(yaw, pitch, 10, null);
         } else {
            this.mc.player.setYRot(yaw);
            this.mc.player.setXRot(pitch);
         }

         if ((Boolean)this.handDerp.get()) {
            this.mc.player.swing(ThreadLocalRandom.current().nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
         }
      }
   }

   public enum PitchMode {
      RANDOM,
      STATIC;
   }

   public enum YawMode {
      RANDOM,
      SPIN,
      STATIC;
   }
}
