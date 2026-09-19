package net.venomous.meteoraddon.modules.detection;

import com.mojang.brigadier.suggestion.Suggestion;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.world.level.GameType;
import net.venomous.meteoraddon.VenomousAddon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class VAntiVanish extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Integer> interval = this.sgGeneral.add(
       new IntSetting.Builder()
           .name("check-interval")
           .description("Interval between autocomplete vanish checks in ticks.")
           .defaultValue(60)
           .min(10)
           .sliderMax(200)
           .build()
   );

   private final Setting<Boolean> checkMsg = this.sgGeneral.add(
       new BoolSetting.Builder()
           .name("check-msg-autocomplete")
           .description("Check if players can be /msg or tab-completed while hidden from the tab list.")
           .defaultValue(true)
           .build()
   );

   private final Setting<List<String>> commands = this.sgGeneral.add(
       new StringListSetting.Builder()
           .name("commands")
           .description("Command prefixes queried for autocomplete vanish detection.")
           .defaultValue(List.of("/msg", "/tell", "/w", "/whisper", "/t", "/minecraft:msg"))
           .visible(this.checkMsg::get)
           .build()
   );

   private final Setting<Boolean> checkLeave = this.sgGeneral.add(
       new BoolSetting.Builder()
           .name("check-silent-leave")
           .description("Warn when a player leaves the tab list without a normal chat disconnect message.")
           .defaultValue(true)
           .build()
   );

   private final Setting<Boolean> detectSpectators = this.sgGeneral.add(
       new BoolSetting.Builder()
           .name("detect-spectators")
           .description("Detect players switching to GameMode 3 Spectator mode in tab packets.")
           .defaultValue(true)
           .build()
   );

   private final Set<Integer> pendingQueryIds = Collections.synchronizedSet(new HashSet<>());
   private final Set<String> detectedVanished = new HashSet<>();
   private Map<UUID, String> playerCache = new HashMap<>();
   private final List<String> messageCache = new ArrayList<>();
   private final Random random = new Random();
   private int timer;

   public VAntiVanish() {
      super(VenomousAddon.CATEGORY, "v-anti-vanish", "Detects vanished staff and hidden players via /msg tab completion and silent tab removals.");
   }

   @Override
   public void onActivate() {
      this.timer = 0;
      this.pendingQueryIds.clear();
      this.detectedVanished.clear();
      this.messageCache.clear();
      this.updatePlayerCache();
   }

   @Override
   public void onDeactivate() {
      this.pendingQueryIds.clear();
      this.detectedVanished.clear();
      this.messageCache.clear();
      this.playerCache.clear();
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (this.mc.player == null || this.mc.getConnection() == null) return;

      // Handle command autocomplete suggestions for vanish detection
      if (this.checkMsg.get() && event.packet instanceof ClientboundCommandSuggestionsPacket packet) {
         if (this.pendingQueryIds.remove(packet.id())) {
            event.cancel();

            Set<String> tablistNames = this.getTablistNames();
            String clientName = this.mc.player.getName().getString();

            for (Suggestion suggestion : packet.toSuggestions().getList()) {
               String name = suggestion.getText().trim();

               if (!this.isValidUsername(name) || name.equalsIgnoreCase(clientName)) {
                  continue;
               }

               String lower = name.toLowerCase(Locale.ROOT);

               // If the suggested username is NOT present on the player tablist, the player is vanished!
               if (!tablistNames.contains(lower)) {
                  if (this.detectedVanished.add(lower)) {
                     this.warning("§7[§aVenomous§7] §cVanished player detected via /msg: §e" + name, new Object[0]);
                  }
               } else {
                  // Player is visible in tablist now; remove from vanished set
                  this.detectedVanished.remove(lower);
               }
            }
         }
      }

      // Handle Spectator GameMode detection in player info packets
      if (this.detectSpectators.get() && event.packet instanceof ClientboundPlayerInfoUpdatePacket packet) {
         for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            if (packet.actions().contains(Action.UPDATE_GAME_MODE) && entry.gameMode() == GameType.SPECTATOR) {
               String name = entry.profile() != null ? entry.profile().name() : "Unknown";
               this.warning("§7[§aVenomous§7] §eSpectator detected in tab list: §f" + name, new Object[0]);
            }
         }
      }
   }

   @EventHandler
   private void onReceiveMessage(ReceiveMessageEvent event) {
      this.messageCache.add(event.getMessage().getString());
      if (this.messageCache.size() > 50) {
         this.messageCache.remove(0);
      }
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player == null || this.mc.getConnection() == null) return;

      if (++this.timer >= this.interval.get()) {
         this.timer = 0;

         // 1. Silent tablist leave detection
         if (this.checkLeave.get()) {
            Map<UUID, String> oldPlayers = Map.copyOf(this.playerCache);
            this.updatePlayerCache();

            for (Map.Entry<UUID, String> entry : oldPlayers.entrySet()) {
               String name = entry.getValue();
               if (!this.playerCache.containsKey(entry.getKey())
                   && this.isValidUsername(name)
                   && this.messageCache.stream().noneMatch(s -> s.contains(name))) {
                  this.warning("§7[§aVenomous§7] §e" + name + " §7may have vanished (silent tab leave).", new Object[0]);
               }
            }
         }

         // 2. Query /msg and command completions
         if (this.checkMsg.get()) {
            List<String> prefixList = this.commands.get();
            if (prefixList != null && !prefixList.isEmpty()) {
               for (String cmd : prefixList) {
                  int id = this.random.nextInt(1000000);
                  this.pendingQueryIds.add(id);
                  String query = cmd.endsWith(" ") ? cmd : cmd + " ";
                  this.mc.getConnection().send(new ServerboundCommandSuggestionPacket(id, query));
               }
            }
         }

         this.messageCache.clear();
      }
   }

   private void updatePlayerCache() {
      if (this.mc.getConnection() != null) {
         this.playerCache = this.mc.getConnection().getOnlinePlayers().stream()
             .collect(Collectors.toMap(
                 e -> e.getProfile().id(),
                 e -> e.getProfile().name(),
                 (a, b) -> a
             ));
      }
   }

   private Set<String> getTablistNames() {
      if (this.mc.getConnection() == null) return Collections.emptySet();
      return this.mc.getConnection().getOnlinePlayers().stream()
          .map(p -> p.getProfile().name().toLowerCase(Locale.ROOT))
          .collect(Collectors.toSet());
   }

   private boolean isValidUsername(String name) {
      if (name == null || name.isEmpty() || name.contains(" ")) return false;
      if (this.mc.player != null && name.equalsIgnoreCase(this.mc.player.getName().getString())) return false;
      return name.length() >= 3 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$");
   }

   @Override
   public String getInfoString() {
      int count = this.detectedVanished.size();
      if (count > 0) {
         return count + " Vanished";
      }
      return null;
   }
}
