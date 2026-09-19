package net.venomous.meteoraddon.modules.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.core.BlockPos;
import net.venomous.meteoraddon.VenomousAddon;

public class VCoordSnapper extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> chatFeedback = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-feedback").description("Show notification in chat.").defaultValue(true).build());
   private final Setting<Boolean> webhook = this.sgGeneral
      .add(new BoolSetting.Builder().name("webhook").description("Enable webhook notifications.").defaultValue(false).build());
   private final Setting<String> webhookUrl = this.sgGeneral
      .add(new StringSetting.Builder().name("webhook-url").description("Discord webhook URL for notifications.").defaultValue("").visible(this.webhook::get).build());
   private final Setting<Boolean> selfPing = this.sgGeneral
      .add(new BoolSetting.Builder().name("self-ping").description("Ping yourself in the webhook message.").defaultValue(false).visible(this.webhook::get).build());
   private final Setting<String> discordId = this.sgGeneral
      .add(new StringSetting.Builder().name("discord-id").description("Your Discord user ID for pinging.").defaultValue("").visible(() -> this.webhook.get() && this.selfPing.get()).build());

   public VCoordSnapper() {
      super(VenomousAddon.CATEGORY, "v-coord-snapper", "Copies your coordinates to clipboard and optionally sends them via webhook.");
   }

   @Override
   public void onActivate() {
      try {
         if (this.mc.player == null) {
            this.error("Player is null!", new Object[0]);
            this.toggle();
            return;
         }

         BlockPos pos = this.mc.player.blockPosition();
         int x = pos.getX();
         int y = pos.getY();
         int z = pos.getZ();
         String coords = String.format("%d %d %d", x, y, z);
         this.mc.keyboardHandler.setClipboard(coords);
         if (this.chatFeedback.get()) {
            this.info("Copied coordinates: " + coords, new Object[0]);
         }

         if (this.webhook.get() && !this.webhookUrl.get().isEmpty()) {
            this.sendWebhook(x, y, z);
         }
      } catch (Exception e) {
         this.error("Failed to copy/send coordinates: " + e.getMessage(), new Object[0]);
         return;
      } finally {
         this.toggle();
      }
   }

   private void sendWebhook(int x, int y, int z) {
      new Thread(() -> {
         try {
            URL url = URI.create(this.webhookUrl.get()).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            JsonObject json = new JsonObject();
            json.addProperty("username", "Venomous Webhook");
            String messageContent = "";
            if (this.selfPing.get() && !this.discordId.get().trim().isEmpty()) {
               messageContent = String.format("<@%s>", this.discordId.get().trim());
            }

            json.addProperty("content", messageContent);
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "Coordsnapper Coords");
            embed.addProperty("description", String.format("Coords: X: %d, Y: %d, Z: %d", x, y, z));
            embed.addProperty("color", 7733503);
            embed.addProperty("timestamp", Instant.now().toString());
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Sent by Venomous");
            embed.add("footer", footer);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            json.add("embeds", embeds);

            try (OutputStream os = connection.getOutputStream()) {
               byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
               os.write(input, 0, input.length);
            }

            connection.getInputStream().close();
         } catch (Exception e) {
            this.error("Webhook failed: " + e.getMessage(), new Object[0]);
         }
      }).start();
   }
}
