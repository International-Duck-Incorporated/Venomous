package net.venomous.meteoraddon.modules.misc;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiBot extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Boolean> removeBots = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("remove-bots")
         .description("Remove bot entities from the world client-side.")
         .defaultValue(false)
         .build()
   );

   private final Setting<Boolean> filterTablist = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("filter-tablist")
         .description("Filter entities that do not exist on the server tab list (NPCs / Aura bots).")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> filterLatency = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("filter-latency")
         .description("Filter entities with 0 or negative latency.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> filterInvalidUuid = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("filter-invalid-uuid")
         .description("Filter entities whose UUID version is not v4 (generated NPC/Aura bot profiles).")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> filterCirclingAuraBots = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("filter-circling-bots")
         .description("Filter Watchdog/Matrix aura bots circling overhead.")
         .defaultValue(true)
         .build()
   );

   private final Set<Player> bots = new HashSet<>();

   public AntiBot() {
      super(VenomousAddon.CATEGORY, "antibot", "Advanced bot detector filtering Watchdog, Matrix, Citizens, and server NPC bots.");
   }

   @Override
   public void onActivate() {
      this.bots.clear();
   }

   @Override
   public void onDeactivate() {
      this.bots.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.level == null || this.mc.player == null) {
         return;
      }

      for (Player player : this.mc.level.players()) {
         if (player != this.mc.player && !this.bots.contains(player) && this.isBot(player)) {
            this.bots.add(player);
            this.info("Detected anticheat/NPC bot: %s", player.getGameProfile().name());
            if (this.removeBots.get()) {
               this.mc.level.removeEntity(player.getId(), RemovalReason.DISCARDED);
            }
         }
      }
   }

   public boolean isBot(Player player) {
      if (player == null || player == this.mc.player) {
         return false;
      }

      if (this.mc.getConnection() == null) {
         return false;
      }

      PlayerInfo info = this.mc.getConnection().getPlayerInfo(player.getUUID());

      // 1. Tablist check
      if (this.filterTablist.get() && info == null) {
         return true;
      }

      if (info != null) {
         // 2. Latency check
         if (this.filterLatency.get() && info.getLatency() <= 0) {
            return true;
         }
      }

      // 3. UUID version check
      if (this.filterInvalidUuid.get() && player.getUUID().version() != 4) {
         return true;
      }

      // 4. Circling Watchdog / Matrix aura bot check
      if (this.filterCirclingAuraBots.get()) {
         double dist = this.mc.player.distanceTo(player);
         double heightDiff = player.getY() - this.mc.player.getY();
         // Aura bots hover 1.5 - 3.5 blocks above and rotate unnaturally fast
         if (dist < 4.5 && heightDiff > 1.2 && heightDiff < 4.0 && player.isInvisible()) {
            return true;
         }
      }

      return false;
   }

   public boolean isBotEntity(Player player) {
      return this.isActive() && (this.bots.contains(player) || this.isBot(player));
   }
}
