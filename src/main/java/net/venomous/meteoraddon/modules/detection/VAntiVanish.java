package net.venomous.meteoraddon.modules.detection;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.world.level.GameType;
import net.venomous.meteoraddon.VenomousAddon;

public class VAntiVanish extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<VAntiVanish.Mode> mode = this.sgGeneral.add(
      new EnumSetting.Builder<VAntiVanish.Mode>()
         .name("mode")
         .description("Vanish detection method.")
         .defaultValue(VAntiVanish.Mode.MsgAutocomplete)
         .build()
   );

   private final Setting<Integer> interval = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("interval")
         .description("Vanish check interval in ticks.")
         .defaultValue(60)
         .min(10)
         .sliderMax(200)
         .build()
   );

   private final Setting<String> command = this.sgGeneral.add(
      new StringSetting.Builder()
         .name("command")
         .description("Command prefix for autocomplete scanning.")
         .defaultValue("/msg")
         .visible(() -> this.mode.get() == VAntiVanish.Mode.MsgAutocomplete || this.mode.get() == VAntiVanish.Mode.RealJoinMessage)
         .build()
   );

   private final Setting<Boolean> detectSpectators = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("detect-spectators")
         .description("Detect spectators updating in the player info packet.")
         .defaultValue(true)
         .build()
   );

   private Map<UUID, String> playerCache = new HashMap<>();
   private final List<String> messageCache = new ArrayList<>();
   private final Random random = new Random();
   private final Set<Integer> pendingCompletionIds = new HashSet<>();
   private List<String> completionPlayerCache = new ArrayList<>();
   private final Set<String> knownVanished = new HashSet<>();
   private int timer;

   public VAntiVanish() {
      super(VenomousAddon.CATEGORY, "v-anti-vanish", "Detects vanished staff via /msg autocomplete and tablist cross-referencing.");
   }

   @Override
   public void onActivate() {
      this.timer = 0;
      this.pendingCompletionIds.clear();
      this.messageCache.clear();
      this.completionPlayerCache = new ArrayList<>();
      this.knownVanished.clear();
      if (this.mc.getConnection() != null) {
         this.playerCache = this.mc.getConnection().getOnlinePlayers().stream()
            .collect(Collectors.toMap(e -> e.getProfile().id(), e -> e.getProfile().name(), (a, b) -> a));
      }
   }

   @Override
   public void onDeactivate() {
      this.pendingCompletionIds.clear();
      this.messageCache.clear();
      this.completionPlayerCache.clear();
      this.playerCache.clear();
      this.knownVanished.clear();
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (event.packet instanceof ClientboundCommandSuggestionsPacket packet && this.pendingCompletionIds.remove(packet.id())) {
         List<String> suggestions = packet.toSuggestions().getList().stream()
            .map(Suggestion::getText)
            .filter(this::validName)
            .toList();

         if (this.mode.get() == VAntiVanish.Mode.MsgAutocomplete) {
            this.handleMsgAutocompleteVanish(suggestions);
         } else if (this.mode.get() == VAntiVanish.Mode.RealJoinMessage) {
            this.handleJoinVanish(suggestions);
         }
         event.cancel();
      }

      if (this.detectSpectators.get() && event.packet instanceof ClientboundPlayerInfoUpdatePacket packet) {
         for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            if (packet.actions().contains(Action.UPDATE_GAME_MODE) && entry.gameMode() == GameType.SPECTATOR) {
               String name = entry.profile() != null ? entry.profile().name() : "Unknown";
               this.warning("Spectator detected in tab list: %s", name);
            }
         }
      }
   }

   private void handleMsgAutocompleteVanish(List<String> suggestions) {
      if (this.mc.getConnection() == null) {
         return;
      }

      Set<String> tablistNames = this.mc.getConnection().getOnlinePlayers().stream()
         .map(p -> p.getProfile().name().toLowerCase(Locale.ROOT))
         .collect(Collectors.toSet());

      for (String suggestedName : suggestions) {
         String lower = suggestedName.toLowerCase(Locale.ROOT);
         if (!tablistNames.contains(lower)) {
            if (this.knownVanished.add(lower)) {
               this.warning("Vanished player detected via /msg autocomplete: %s", suggestedName);
            }
         } else {
            this.knownVanished.remove(lower);
         }
      }
   }

   private void handleJoinVanish(List<String> suggestions) {
      List<String> lastUsernames = List.copyOf(this.completionPlayerCache);
      this.completionPlayerCache = suggestions;

      if (!lastUsernames.isEmpty()) {
         for (String name : this.completionPlayerCache) {
            if (!lastUsernames.contains(name)) {
               this.info("Player appeared in autocomplete: %s", name);
            }
         }
         for (String name : lastUsernames) {
            if (!this.completionPlayerCache.contains(name)) {
               this.info("Player disappeared from autocomplete: %s", name);
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
      if (this.mc.player == null || this.mc.getConnection() == null) {
         return;
      }

      if (++this.timer >= this.interval.get()) {
         this.timer = 0;

         if (this.mode.get() == VAntiVanish.Mode.LeaveMessage) {
            Map<UUID, String> oldPlayers = Map.copyOf(this.playerCache);
            this.playerCache = this.mc.getConnection().getOnlinePlayers().stream()
               .collect(Collectors.toMap(e -> e.getProfile().id(), e -> e.getProfile().name(), (a, b) -> a));

            for (Map.Entry<UUID, String> entry : oldPlayers.entrySet()) {
               String name = entry.getValue();
               if (!this.playerCache.containsKey(entry.getKey())
                  && this.validName(name)
                  && this.messageCache.stream().noneMatch(s -> s.contains(name))) {
                  this.warning("%s may have vanished (no leave message).", name);
               }
            }
            this.messageCache.clear();
         } else {
            int id = this.random.nextInt(10000) + 1;
            this.pendingCompletionIds.add(id);
            String cmd = this.command.get().trim();
            if (!cmd.endsWith(" ")) {
               cmd += " ";
            }
            this.mc.getConnection().send(new ServerboundCommandSuggestionPacket(id, cmd));
         }
      }
   }

   private boolean validName(String name) {
      if (name == null || name.isEmpty() || name.contains(" ") || name.length() < 3 || name.length() > 16) {
         return false;
      }
      if (this.mc.player != null && Objects.equals(name, this.mc.player.getName().getString())) {
         return false;
      }
      return true;
   }

   @Override
   public String getInfoString() {
      if (this.mode.get() == VAntiVanish.Mode.MsgAutocomplete && !this.knownVanished.isEmpty()) {
         return this.knownVanished.size() + " Vanished";
      }
      return this.mode.get().name();
   }

   public enum Mode {
      MsgAutocomplete,
      LeaveMessage,
      RealJoinMessage;
   }
}
