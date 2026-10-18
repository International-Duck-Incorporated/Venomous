package net.venomous.meteoraddon.modules.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.core.BlockPos;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class CoordSnapper extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> chatFeedback = this.sgGeneral
        .add(new BoolSetting.Builder().name("chat-feedback").description("Show notification in chat.").defaultValue(true).build());
    private final Setting<Boolean> webhook = this.sgGeneral
        .add(new BoolSetting.Builder().name("webhook").description("Enable webhook notifications.").defaultValue(false).build());
    private final Setting<String> webhookUrl = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("webhook-url")
                .description("Discord webhook URL for notifications.")
                .defaultValue("")
                .visible(this.webhook::get)
                .build()
        );
    private final Setting<Boolean> selfPing = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("self-ping").description("Ping yourself in the webhook message.").defaultValue(false)
                .visible(this.webhook::get)
                .build()
        );
    private final Setting<String> discordId = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("discord-id")
                .description("Your Discord user ID for pinging.")
                .defaultValue("")
                .visible(() -> (Boolean)this.webhook.get() && (Boolean)this.selfPing.get())
                .build()
        );

    public CoordSnapper() {
        super(VenomousAddon.CATEGORY, "coord-snapper", "Copies your coordinates to clipboard and optionally sends them via webhook.");
    }

    public void onActivate() {
        try {
            if (this.mc.player == null) {
                this.error("Cannot copy coordinates before joining a world.", new Object[0]);
                return;
            }

            BlockPos pos = this.mc.player.blockPosition();
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            String coords = String.format("%d %d %d", x, y, z);
            this.mc.keyboardHandler.setClipboard(coords);
            if ((Boolean)this.chatFeedback.get()) {
                this.info("Copied coordinates: " + coords, new Object[0]);
            }

            if ((Boolean)this.webhook.get() && !this.webhookUrl.get().trim().isEmpty()) {
                this.sendWebhook(x, y, z);
            }
        } catch (Exception e) {
            this.error("Failed to copy/send coordinates: " + e.getMessage(), new Object[0]);
            return;
        } finally {
            // Defer the auto-off so we never re-enter Meteor's toggle logic from inside onActivate().
            this.mc.execute(() -> {
                if (this.isActive()) this.toggle();
            });
        }
    }

    private void sendWebhook(int x, int y, int z) {
        String url = this.webhookUrl.get().trim();
        String discordUserId = this.discordId.get().trim();
        boolean pingUser = (Boolean)this.selfPing.get() && !discordUserId.isEmpty();

        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException e) {
            this.error("Webhook URL is malformed.", new Object[0]);
            return;
        }

        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            this.error("Webhook URL must use https - refusing to send coordinates in plaintext.", new Object[0]);
            return;
        }

        Thread thread = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)parsed.toURL().openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(30_000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                JsonObject json = new JsonObject();
                json.addProperty("username", "Venomous Webhook");
                json.addProperty("content", pingUser ? "<@" + discordUserId + ">" : "");

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
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("HTTP " + status);
                }
            } catch (Exception e) {
                this.mc.execute(() -> this.error("Webhook failed: " + e.getMessage(), new Object[0]));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "Venomous CoordSnapper Webhook");
        thread.setDaemon(true);
        thread.start();
    }
}
