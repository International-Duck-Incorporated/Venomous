package net.venomous.meteoraddon.modules.detection;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.world.level.GameType;
import net.venomous.meteoraddon.VenomousAddon;

public class VAntiVanish extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> interval = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("interval")).description("Vanish check interval in ticks.")).defaultValue(100))
            .min(20)
            .sliderMax(300)
            .build()
      );
   private final Setting<VAntiVanish.Mode> mode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("mode"))
                  .description("Detection method."))
               .defaultValue(VAntiVanish.Mode.LeaveMessage))
            .build()
      );
   private final Setting<String> command = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name("command"))
                     .description("Command completion prefix used to detect names."))
                  .defaultValue("minecraft:msg"))
               .visible(() -> this.mode.get() == VAntiVanish.Mode.RealJoinMessage))
            .build()
      );
   private final Setting<Boolean> detectSpectators = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("detect-spectators"))
                  .description("Try to detect spectators using tab list and packet analysis."))
               .defaultValue(true))
            .build()
      );
   private Map<UUID, String> playerCache = new HashMap<>();
   private final List<String> messageCache = new ArrayList<>();
   private final Random random = new Random();
   private final List<Integer> completionIds = new ArrayList<>();
   private List<String> completionPlayerCache = new ArrayList<>();
   private int timer;

   public VAntiVanish() {
      super(VenomousAddon.CATEGORY, "v-anti-vanish", "Notifies when players disappear without a normal leave message or are in spectator.");
   }

   public void onActivate() {
      this.timer = 0;
      this.completionIds.clear();
      this.messageCache.clear();
      this.completionPlayerCache = new ArrayList<>();
      if (this.mc.getConnection() != null) {
         this.playerCache = this.mc.getConnection().getOnlinePlayers().stream().collect(Collectors.toMap(e -> e.getProfile().id(), e -> e.getProfile().name()));
      }
   }

   public void onDeactivate() {
      this.completionIds.clear();
      this.messageCache.clear();
      this.completionPlayerCache.clear();
      this.playerCache.clear();
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (this.mode.get() == VAntiVanish.Mode.RealJoinMessage
         && event.packet instanceof ClientboundCommandSuggestionsPacket packet2
         && this.completionIds.contains(packet2.id())) {
         List<String> lastUsernames = this.completionPlayerCache.stream().toList();
         this.completionPlayerCache = packet2.toSuggestions().getList().stream().<String>map(Suggestion::getText).toList();
         this.completionIds.remove(packet2.id());
         event.cancel();
         if (!lastUsernames.isEmpty()) {
            Predicate<String> changed = name -> lastUsernames.contains(name) != this.completionPlayerCache.contains(name);

            for (String name2 : this.completionPlayerCache) {
               if (this.validName(name2) && changed.test(name2)) {
                  this.info("Player joined: %s", new Object[]{name2});
               }
            }

            for (String name2 : lastUsernames) {
               if (this.validName(name2) && changed.test(name2)) {
                  this.info("Player left: %s", new Object[]{name2});
               }
            }
         }
      }

      if ((Boolean)this.detectSpectators.get() && event.packet instanceof ClientboundPlayerInfoUpdatePacket packet) {
         for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            if (packet.actions().contains(Action.UPDATE_GAME_MODE) && entry.gameMode() == GameType.SPECTATOR) {
               this.warning("Spectator detected in tab list: " + (entry.profile() != null ? entry.profile().name() : "Unknown"), new Object[0]);
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
      if (this.mc.player != null && this.mc.getConnection() != null && ++this.timer >= (Integer)this.interval.get()) {
         this.timer = 0;
         if (this.mode.get() == VAntiVanish.Mode.LeaveMessage) {
            Map<UUID, String> oldPlayers = Map.copyOf(this.playerCache);
            this.playerCache = this.mc
               .getConnection()
               .getOnlinePlayers()
               .stream()
               .collect(Collectors.toMap(e -> e.getProfile().id(), e -> e.getProfile().name()));

            for (Entry<UUID, String> entry : oldPlayers.entrySet()) {
               String name;
               if (!this.playerCache.containsKey(entry.getKey())
                  && this.validName(name = entry.getValue())
                  && this.messageCache.stream().noneMatch(s -> s.contains(name))) {
                  this.warning("%s may have vanished.", new Object[]{name});
               }
            }
         } else {
            int id = this.random.nextInt(10000);
            this.completionIds.add(id);
            this.mc.getConnection().send(new ServerboundCommandSuggestionPacket(id, (String)this.command.get() + " "));
         }

         this.messageCache.clear();
      }
   }

   private boolean validName(String name) {
      return this.mc.player != null && Objects.equals(name, this.mc.player.getName().getString())
         ? false
         : !name.contains(" ") && name.length() >= 3 && name.length() <= 16;
   }

   public enum Mode {
      LeaveMessage,
      RealJoinMessage;
   }
}
