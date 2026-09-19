package net.venomous.meteoraddon.modules.detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundPingPacket;

public class ServerAnalyzer {
   private static final int MIN_SAMPLES = 6;
   private static final int MAX_SAMPLES = 20;
   private static final long EMIT_COOLDOWN_MS = 4000L;
   private final List<Integer> transactions = new ArrayList<>(20);
   private Consumer<String> listener;
   private boolean active;
   private boolean capturing;
   private boolean subscribed;
   private String lastDetection;
   private long lastEmitTime;

   public void enable() {
      if (!this.subscribed) {
         MeteorClient.EVENT_BUS.subscribe(this);
         this.subscribed = true;
      }

      this.active = true;
      this.reset();
   }

   public void disable() {
      this.active = false;
      this.listener = null;
      this.reset();
      if (this.subscribed) {
         MeteorClient.EVENT_BUS.unsubscribe(this);
         this.subscribed = false;
      }
   }

   public void setListener(Consumer<String> listener) {
      this.listener = listener;
   }

   private void reset() {
      this.transactions.clear();
      this.capturing = false;
      this.lastDetection = null;
      this.lastEmitTime = 0L;
   }

   @EventHandler
   private void onJoin(GameJoinedEvent event) {
      if (this.active) {
         this.reset();
         this.capturing = true;
      }
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (this.active && this.capturing && event.packet instanceof ClientboundPingPacket ping) {
         this.transactions.add(ping.getId());
         if (this.transactions.size() > 20) {
            this.transactions.remove(0);
         }

         if (this.transactions.size() >= 6) {
            this.analyzeSnapshot();
         }
      }
   }

   private void analyzeSnapshot() {
      String result = this.detect(this.transactions);
      if (result != null) {
         this.emit(result);
      }
   }

   private String detect(List<Integer> data) {
      HashMap<String, Double> scores = new HashMap<>();
      int first = data.get(0);
      ArrayList<Integer> diffs = new ArrayList<>(data.size() - 1);

      for (int i = 0; i < data.size() - 1; i++) {
         diffs.add(data.get(i + 1) - data.get(i));
      }

      if (diffs.isEmpty()) {
         return null;
      }

      String ip = this.getServerIp();
      if (ip != null && ip.toLowerCase(Locale.ROOT).endsWith("hypixel.net")) {
         this.addScore(scores, "Watchdog", 1.0);
      }

      if (diffs.stream().allMatch(d -> d.equals(diffs.get(0)))) {
         int diff = diffs.get(0);
         if (diff == 1) {
            if (this.inRange(first, -23772, -23762)) {
               this.addScore(scores, "Vulcan", 0.9);
            }

            if (this.inRange(first, 95, 105) || this.inRange(first, -20005, -19995)) {
               this.addScore(scores, "Matrix", 0.9);
            }

            if (this.inRange(first, -32773, -32762)) {
               this.addScore(scores, "Grizzly", 0.8);
            }

            this.addScore(scores, "Verus", 0.6);
         } else if (diff == -1) {
            if (this.inRange(first, -8287, -8280)) {
               this.addScore(scores, "Errata", 0.9);
            }

            if (first < -3000) {
               this.addScore(scores, "Intave", 0.7);
            }

            if (this.inRange(first, -5, 0)) {
               this.addScore(scores, "Grim", 0.8);
            }

            if (this.inRange(first, -3000, -2995)) {
               this.addScore(scores, "Karhu", 0.8);
            }

            this.addScore(scores, "Polar", 0.5);
         }
      }

      if (data.size() >= 3 && data.get(0).equals(data.get(1)) && this.isRamp(data)) {
         this.addScore(scores, "Verus", 0.85);
      }

      if (diffs.size() >= 2 && diffs.get(0) >= 100 && diffs.get(1) == -1 && this.polarRest(diffs)) {
         this.addScore(scores, "Polar", 0.9);
      }

      if (first < -3000 && data.contains(0)) {
         this.addScore(scores, "Intave", 0.75);
      }

      if (this.isOldVulcan(data)) {
         this.addScore(scores, "Old Vulcan", 0.95);
      }

      return this.best(scores);
   }

   private void addScore(Map<String, Double> scores, String key, double value) {
      scores.merge(key, value, Double::sum);
   }

   private String best(Map<String, Double> scores) {
      return scores.entrySet().stream().max(Entry.comparingByValue()).map(e -> "Likely " + e.getKey()).orElse(null);
   }

   private void emit(String result) {
      long now = System.currentTimeMillis();
      boolean same = result.equals(this.lastDetection);
      boolean cooldownPassed = now - this.lastEmitTime > 4000L;
      if (!same || cooldownPassed) {
         this.lastDetection = result;
         this.lastEmitTime = now;
         if (this.listener != null) {
            this.listener.accept(result);
         }
      }
   }

   private boolean isRamp(List<Integer> data) {
      for (int i = 2; i < data.size() - 1; i++) {
         if (data.get(i + 1) - data.get(i) != 1) {
            return false;
         }
      }

      return true;
   }

   private boolean polarRest(List<Integer> diffs) {
      for (int i = 2; i < diffs.size(); i++) {
         if (diffs.get(i) != -1) {
            return false;
         }
      }

      return true;
   }

   private boolean isOldVulcan(List<Integer> data) {
      if (data.size() < 3) {
         return false;
      }

      if (data.get(0) == -30767 && data.get(1) == -30766 && data.get(2) == -25767) {
         for (int i = 3; i < data.size() - 1; i++) {
            if (data.get(i + 1) - data.get(i) != 1) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private String getServerIp() {
      try {
         if (MeteorClient.mc != null && MeteorClient.mc.getCurrentServer() != null) {
            return MeteorClient.mc.getCurrentServer().ip;
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   private boolean inRange(int v, int min, int max) {
      return v >= min && v <= max;
   }
}
