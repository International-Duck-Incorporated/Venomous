package net.venomous.meteoraddon.modules.misc;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

public class VAntiBot extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> removeBots = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("remove-bots")).description("Remove bots from the world client-side.")).defaultValue(false)).build()
      );
   private final Setting<Boolean> filterLobbyBots = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("filter-lobby-bots")).description("Detect typical lobby bots (0 ping, no tab entry, etc)."))
               .defaultValue(true))
            .build()
      );
   private final List<Player> bots = new ArrayList<>();

   public VAntiBot() {
      super(VenomousAddon.CATEGORY, "v-antibot", "Detect and filter bot entities.");
   }

   public void onDeactivate() {
      this.bots.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.level != null) {
         for (Player player : this.mc.level.players()) {
            if (player != this.mc.player && !this.bots.contains(player) && this.isBot(player)) {
               this.bots.add(player);
               this.info("Detected bot: " + player.getGameProfile().name(), new Object[0]);
               if ((Boolean)this.removeBots.get()) {
                  this.mc.level.removeEntity(player.getId(), RemovalReason.DISCARDED);
               }
            }
         }
      }
   }

   private boolean isBot(Player player) {
      PlayerInfo info = this.mc.getConnection().getPlayerInfo(player.getUUID());
      if (info == null) {
         return true;
      } else {
         return this.filterLobbyBots.get() && info.getLatency() <= 0 ? true : player.getUUID().version() != 4 && (Boolean)this.filterLobbyBots.get();
      }
   }

   public boolean isBotEntity(Player player) {
      return this.isActive() && this.bots.contains(player);
   }
}
