package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

public class Hunt extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<String> playerName = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("player-name")).description("The exact name of the player to hunt.")).defaultValue("")).build());

   public Hunt() {
      super(VenomousAddon.CATEGORY, "v-hunt", "Walks toward a specific player by name.");
   }

   public void onDeactivate() {
      PathManagers.get().stop();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (!((String)this.playerName.get()).isEmpty()) {
            Player target = this.mc
               .level
               .players()
               .stream()
               .filter(p -> p.getGameProfile().name().equalsIgnoreCase((String)this.playerName.get()))
               .findFirst()
               .orElse(null);
            if (target != null) {
               PathManagers.get().moveTo(target.blockPosition());
            }
         }
      }
   }
}
