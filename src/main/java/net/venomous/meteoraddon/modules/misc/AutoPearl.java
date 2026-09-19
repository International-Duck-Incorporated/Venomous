package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

public class AutoPearl extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> health = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("health")).description("Health to throw pearl at.")).defaultValue(10)).min(0).max(20).build());
   private final Setting<Boolean> onGround = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("on-ground"))
                  .description("Only throw when on ground."))
               .defaultValue(true))
            .build()
      );

   public AutoPearl() {
      super(VenomousAddon.CATEGORY, "autopearl", "Auto-throw ender pearls when health is low.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         if (this.mc.player.getHealth() <= ((Integer)this.health.get()).intValue() && (!(Boolean)this.onGround.get() || this.mc.player.onGround())) {
            this.throwPearl();
         }
      }
   }

   private void throwPearl() {
      FindItemResult result = InvUtils.findInHotbar(new Item[]{Items.ENDER_PEARL});
      if (result.found()) {
         InvUtils.swap(result.slot(), true);
         this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
         InvUtils.swapBack();
      }
   }
}
