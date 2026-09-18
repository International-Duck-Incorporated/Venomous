package net.venomous.meteoraddon.modules.detection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

public class VPlayerDetection extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgWhitelist = this.settings.createGroup("Whitelist");
   private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");
   private static final Set<String> PERMANENT_WHITELIST = Set.of("FreeCamera");
   private final Setting<Boolean> notifications;
   private final Setting<List<String>> userWhitelist;
   private final Setting<List<Module>> modulesToToggle;
   private final Setting<Boolean> enableWebhook;
   private final Setting<String> webhookUrl;
   private final Setting<Boolean> selfPing;
   private final Setting<String> discordId;
   private final Setting<Boolean> enableDisconnect;
   private final Setting<VPlayerDetection.Mode> notificationMode;
   private final Setting<Boolean> toggleOnPlayer;
   private final Set<String> detectedPlayers = new HashSet<>();
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();

   public VPlayerDetection() {
      super(VenomousAddon.CATEGORY, "v-player-detection", "Detects when players are in the world and reacts.");
      this.notifications = this.sgGeneral
         .add(((Builder)((Builder)((Builder)new Builder().name("notifications")).description("Show chat feedback.")).defaultValue(true)).build());
      this.userWhitelist = this.sgWhitelist
         .add(
            ((meteordevelopment.meteorclient.settings.StringListSetting.Builder)((meteordevelopment.meteorclient.settings.StringListSetting.Builder)((meteordevelopment.meteorclient.settings.StringListSetting.Builder)new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
                        .name("user-whitelist"))
                     .description("List of player names to ignore."))
                  .defaultValue(new ArrayList()))
               .build()
         );
      this.modulesToToggle = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.ModuleListSetting.Builder)((meteordevelopment.meteorclient.settings.ModuleListSetting.Builder)((meteordevelopment.meteorclient.settings.ModuleListSetting.Builder)new meteordevelopment.meteorclient.settings.ModuleListSetting.Builder()
                        .name("modules-to-toggle"))
                     .description("Modules to toggle when a non-whitelisted player is detected."))
                  .defaultValue(new ArrayList()))
               .build()
         );
      this.enableDisconnect = this.sgGeneral
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("disconnect")).description("Automatically disconnect when players are detected."))
                  .defaultValue(true))
               .build()
         );
      this.notificationMode = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("notification-mode"))
                     .description("How to notify when players are detected."))
                  .defaultValue(VPlayerDetection.Mode.Both))
               .build()
         );
      this.toggleOnPlayer = this.sgGeneral
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("toggle-on-player")).description("Automatically toggles this module when a player is detected."))
                  .defaultValue(true))
               .build()
         );
      this.enableWebhook = this.sgWebhook
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("webhook")).description("Send webhook notifications when players are detected."))
                  .defaultValue(false))
               .build()
         );
      this.webhookUrl = this.sgWebhook
         .add(
            ((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                           .name("webhook-url"))
                        .description("Discord webhook URL."))
                     .defaultValue(""))
                  .visible(this.enableWebhook::get))
               .build()
         );
      this.selfPing = this.sgWebhook
         .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("self-ping")).description("Ping yourself in the webhook message.")).defaultValue(false))
                  .visible(this.enableWebhook::get))
               .build()
         );
      this.discordId = this.sgWebhook
         .add(
            ((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                           .name("discord-id"))
                        .description("Your Discord user ID for pinging."))
                     .defaultValue(""))
                  .visible(() -> (Boolean)this.enableWebhook.get() && (Boolean)this.selfPing.get()))
               .build()
         );
   }

   public void onActivate() {
      this.detectedPlayers.clear();
   }

   public void onDeactivate() {
      this.detectedPlayers.clear();
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         HashSet<String> currentPlayers = new HashSet<>();
         String currentPlayerName = this.mc.player.getGameProfile().name();
         HashSet<String> fullWhitelist = new HashSet<>(PERMANENT_WHITELIST);
         fullWhitelist.addAll((Collection<? extends String>)this.userWhitelist.get());

         for (Player player : this.mc.level.players()) {
            String playerName = player.getGameProfile().name();
            if (player != this.mc.player && !playerName.equals(currentPlayerName) && !fullWhitelist.contains(playerName)) {
               currentPlayers.add(playerName);
            }
         }

         if (!currentPlayers.isEmpty() && !currentPlayers.equals(this.detectedPlayers)) {
            this.detectedPlayers.clear();
            this.detectedPlayers.addAll(currentPlayers);
            this.handlePlayerDetection(currentPlayers);
         } else if (currentPlayers.isEmpty()) {
            this.detectedPlayers.clear();
         }
      }
   }

   private void handlePlayerDetection(Set<String> players) {
      String playerList = String.join(", ", players);
      if ((Boolean)this.notifications.get()) {
         this.info("Player(s) detected: (highlight)%s", new Object[]{playerList});
      }

      ChatUtils.sendPlayerMsg("#stop");

      for (Module m : this.modulesToToggle.get()) {
         m.toggle();
         if ((Boolean)this.notifications.get()) {
            this.info("Toggled module: (highlight)%s", new Object[]{m.title});
         }
      }

      if ((Boolean)this.enableWebhook.get()) {
         this.sendWebhookNotification(players);
      }

      if ((Boolean)this.toggleOnPlayer.get()) {
         this.toggle();
      }

      if ((Boolean)this.enableDisconnect.get()) {
         CompletableFuture.runAsync(() -> {
            try {
               Thread.sleep(500L);
               this.disconnectFromServer(playerList);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         });
      }
   }

   private void sendWebhookNotification(Set<String> players) {
      String url = ((String)this.webhookUrl.get()).trim();
      if (!url.isEmpty()) {
         CompletableFuture.runAsync(
            () -> {
               try {
                  String playerList = String.join(", ", players);
                  String serverInfo = this.mc.getConnection() != null ? this.mc.getConnection().serverBrand() : "Unknown Server";
                  String messageContent = "";
                  if ((Boolean)this.selfPing.get() && !((String)this.discordId.get()).trim().isEmpty()) {
                     messageContent = String.format("<@%s>", ((String)this.discordId.get()).trim());
                  }

                  String jsonPayload = String.format(
                     "{\"content\":\"%s\",\"embeds\":[{\"title\":\"Player Detection Alert\",\"description\":\"Player(s) detected on server!\",\"color\":15158332,\"fields\":[{\"name\":\"Players\",\"value\":\"%s\",\"inline\":false},{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true},{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}],\"footer\":{\"text\":\"Sent by Venomous\"}}]}",
                     messageContent.replace("\"", "\\\""),
                     playerList.replace("\"", "\\\""),
                     serverInfo.replace("\"", "\\\""),
                     System.currentTimeMillis() / 1000L
                  );
                  HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create(url))
                     .header("Content-Type", "application/json")
                     .POST(BodyPublishers.ofString(jsonPayload))
                     .timeout(Duration.ofSeconds(30L))
                     .build();
                  this.httpClient.send(request, BodyHandlers.ofString());
               } catch (Exception e) {
                  if ((Boolean)this.notifications.get()) {
                     this.error("Failed to send webhook: " + e.getMessage(), new Object[0]);
                  }
               }
            }
         );
      }
   }

   private void disconnectFromServer(String playerList) {
      if (this.mc.level != null && this.mc.player != null) {
         this.mc.disconnectFromWorld(Component.literal("Player(s) detected: " + playerList));
      }
   }

   public String getInfoString() {
      return this.detectedPlayers.isEmpty() ? null : String.valueOf(this.detectedPlayers.size());
   }

   public enum Mode {
      Chat,
      Toast,
      Both;
   }
}
