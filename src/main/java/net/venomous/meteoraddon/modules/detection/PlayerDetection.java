package net.venomous.meteoraddon.modules.detection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class PlayerDetection extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgWhitelist = this.settings.createGroup("Whitelist");
   private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");
   private static final Set<String> PERMANENT_WHITELIST = Set.of("freecamera");
   private final Setting<Boolean> notifications;
   private final Setting<List<String>> userWhitelist;
   private final Setting<List<Module>> modulesToToggle;
   private final Setting<Boolean> enableWebhook;
   private final Setting<String> webhookUrl;
   private final Setting<Boolean> selfPing;
   private final Setting<String> discordId;
   private final Setting<Boolean> enableDisconnect;
   private final Setting<PlayerDetection.Mode> notificationMode;
   private final Setting<Boolean> toggleOnPlayer;
   private final Set<String> detectedPlayers = new HashSet<>();
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();

   public PlayerDetection() {
      super(VenomousAddon.CATEGORY, "player-detection", "Detects when players are in the world and reacts.");
      this.notifications = this.sgGeneral
         .add(new BoolSetting.Builder().name("notifications").description("Show player detection notifications.").defaultValue(true).build());
      this.userWhitelist = this.sgWhitelist
         .add(
            new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
               .name("user-whitelist")
               .description("List of player names to ignore.")
               .defaultValue(new ArrayList())
               .build()
         );
      this.modulesToToggle = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.ModuleListSetting.Builder()
               .name("modules-to-toggle")
               .description("Modules to toggle when a non-whitelisted player is detected.")
               .defaultValue(new ArrayList())
               .build()
         );
      this.enableDisconnect = this.sgGeneral
         .add(
            new BoolSetting.Builder().name("disconnect").description("Automatically disconnect when players are detected.")
               .defaultValue(true)
               .build()
         );
      this.notificationMode = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<PlayerDetection.Mode>()
               .name("notification-mode")
               .description("How to notify when players are detected.")
               .defaultValue(PlayerDetection.Mode.Both)
               .build()
         );
      this.toggleOnPlayer = this.sgGeneral
         .add(
            new BoolSetting.Builder().name("toggle-on-player").description("Automatically toggles this module when a player is detected.")
               .defaultValue(true)
               .build()
         );
      this.enableWebhook = this.sgWebhook
         .add(
            new BoolSetting.Builder().name("webhook").description("Send webhook notifications when players are detected.")
               .defaultValue(false)
               .build()
         );
      this.webhookUrl = this.sgWebhook
         .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
               .name("webhook-url")
               .description("Discord webhook URL.")
               .defaultValue("")
               .visible(this.enableWebhook::get)
               .build()
         );
      this.selfPing = this.sgWebhook
         .add(
            new BoolSetting.Builder().name("self-ping").description("Ping yourself in the webhook message.").defaultValue(false)
               .visible(this.enableWebhook::get)
               .build()
         );
      this.discordId = this.sgWebhook
         .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
               .name("discord-id")
               .description("Your Discord user ID for pinging.")
               .defaultValue("")
               .visible(() -> (Boolean)this.enableWebhook.get() && (Boolean)this.selfPing.get())
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
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.level == null) {
         this.detectedPlayers.clear();
         return;
      }

      Set<String> currentPlayers = new HashSet<>();
      String currentPlayerName = this.mc.player.getGameProfile().name();
      Set<String> fullWhitelist = new HashSet<>(PERMANENT_WHITELIST);
      for (String name : this.userWhitelist.get()) {
         fullWhitelist.add(name.toLowerCase(Locale.ROOT));
      }

      for (Player player : this.mc.level.players()) {
         String playerName = player.getGameProfile().name();
         if (player != this.mc.player
            && !playerName.equalsIgnoreCase(currentPlayerName)
            && !fullWhitelist.contains(playerName.toLowerCase(Locale.ROOT))) {
            currentPlayers.add(playerName);
         }
      }

      if (!currentPlayers.isEmpty() && !currentPlayers.equals(this.detectedPlayers)) {
         this.detectedPlayers.clear();
         this.detectedPlayers.addAll(currentPlayers);
         this.handlePlayerDetection(Set.copyOf(currentPlayers));
      } else if (currentPlayers.isEmpty()) {
         this.detectedPlayers.clear();
      }
   }

   private void handlePlayerDetection(Set<String> players) {
      String playerList = String.join(", ", players);
      if ((Boolean)this.notifications.get()) {
         switch (this.notificationMode.get()) {
            case Chat -> this.info("Player(s) detected: (highlight)%s", new Object[]{playerList});
            case Toast -> this.showDetectionToast(playerList);
            case Both -> {
               this.info("Player(s) detected: (highlight)%s", new Object[]{playerList});
               this.showDetectionToast(playerList);
            }
         }
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
         CompletableFuture.delayedExecutor(500L, TimeUnit.MILLISECONDS).execute(
            () -> this.mc.execute(() -> this.disconnectFromServer(playerList))
         );
      }
   }

   private void showDetectionToast(String playerList) {
      MeteorToast toast = new MeteorToast.Builder("Player Detection")
         .text(playerList)
         .build();
      this.mc.gui.toastManager().addToast(toast);
   }

   private void sendWebhookNotification(Set<String> players) {
      String url = this.webhookUrl.get().trim();
      if (url.isEmpty()) return;

      String playerList = String.join(", ", players);
      String serverInfo = this.mc.getConnection() != null ? this.mc.getConnection().serverBrand() : "Unknown Server";
      String discordUserId = this.discordId.get().trim();

      JsonObject payload = new JsonObject();
      payload.addProperty("content", (Boolean)this.selfPing.get() && !discordUserId.isEmpty() ? "<@" + discordUserId + ">" : "");

      JsonObject embed = new JsonObject();
      embed.addProperty("title", "Player Detection Alert");
      embed.addProperty("description", "Player(s) detected on server!");
      embed.addProperty("color", 15158332);

      JsonArray fields = new JsonArray();
      fields.add(webhookField("Players", playerList, false));
      fields.add(webhookField("Server", serverInfo, true));
      fields.add(webhookField("Time", "<t:" + System.currentTimeMillis() / 1000L + ":R>", true));
      embed.add("fields", fields);

      JsonObject footer = new JsonObject();
      footer.addProperty("text", "Sent by Venomous");
      embed.add("footer", footer);

      JsonArray embeds = new JsonArray();
      embeds.add(embed);
      payload.add("embeds", embeds);

      try {
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(payload.toString()))
            .timeout(Duration.ofSeconds(30L))
            .build();
         this.httpClient.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null) {
               this.reportWebhookError(error.getMessage());
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
               this.reportWebhookError("HTTP " + response.statusCode());
            }
         });
      } catch (IllegalArgumentException e) {
         this.reportWebhookError("invalid webhook URL");
      }
   }

   private static JsonObject webhookField(String name, String value, boolean inline) {
      JsonObject field = new JsonObject();
      field.addProperty("name", name);
      field.addProperty("value", value);
      field.addProperty("inline", inline);
      return field;
   }

   private void reportWebhookError(String message) {
      if ((Boolean)this.notifications.get()) {
         this.mc.execute(() -> this.error("Failed to send webhook: " + message, new Object[0]));
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
