package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;

public class VNoBlockInteract extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> onlyWhenHoldingWeapon = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("only-when-holding-weapon")).description("Only prevent block interaction when holding a weapon."))
               .defaultValue(true))
            .build()
      );

   public VNoBlockInteract() {
      super(VenomousAddon.CATEGORY, "v-no-block-interact", "Prevents accidental block interaction (chests, buttons, etc).");
   }

   @EventHandler
   private void onInteractBlock(InteractBlockEvent event) {
      if (this.shouldCancelBlockInteract()) {
         event.cancel();
      }
   }

   public boolean shouldCancelBlockInteract() {
      if (!this.isActive()) {
         return false;
      }

      if (!(Boolean)this.onlyWhenHoldingWeapon.get()) {
         return true;
      }

      if (this.mc.player == null) {
         return false;
      }

      Item item = this.mc.player.getMainHandItem().getItem();
      return McCompat.isWeapon(item);
   }
}
