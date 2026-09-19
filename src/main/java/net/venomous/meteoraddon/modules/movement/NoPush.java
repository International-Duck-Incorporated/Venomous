package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class NoPush extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<NoPush.PushBy> noPushBy = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("push-by")).description("What type of pushing to prevent.")).defaultValue(NoPush.PushBy.ENTITIES))
            .build()
      );
   private final Setting<Boolean> preventSinking = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("prevent-sinking"))
                  .description("Prevent sinking in water."))
               .defaultValue(true))
            .build()
      );

   public NoPush() {
      super(VenomousAddon.CATEGORY, "no-push", "Disables being pushed by entities, liquids, etc.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null
         && (Boolean)this.preventSinking.get()
         && !this.mc.options.keyJump.isDown()
         && !this.mc.options.keyShift.isDown()
         && this.mc.player.isInWater()
         && this.mc.player.getDeltaMovement().y < 0.0) {
         this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
      }
   }

   public enum PushBy {
      ENTITIES,
      BLOCKS,
      FISHING_ROD,
      LIQUIDS,
      SINKING;
   }
}
