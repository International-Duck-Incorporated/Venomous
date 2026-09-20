package net.venomous.meteoraddon.modules.misc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class PacketLogger extends Module {
   private static final int MAX_LOG_ENTRIES = 100_000;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> incoming = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("incoming")).description("Log incoming packets.")).defaultValue(false)).build());
   private final Setting<Boolean> outgoing = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("outgoing")).description("Log outgoing packets.")).defaultValue(true)).build());
   private final Deque<String> log = new ArrayDeque<>();
   private int droppedEntries;

   public PacketLogger() {
      super(VenomousAddon.CATEGORY, "packetlogger", "Log packets to a file in venom-output.");
   }

   public void onActivate() {
      this.log.clear();
      this.droppedEntries = 0;
   }

   public void onDeactivate() {
      if (this.log.isEmpty() && this.droppedEntries == 0) return;

      Path directory = Path.of("venom-output");
      Path file = directory.resolve("packets_" + System.currentTimeMillis() + ".txt");
      try {
         Files.createDirectories(directory);
         try (BufferedWriter writer = Files.newBufferedWriter(
            file,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
         )) {
            if (this.droppedEntries > 0) {
               writer.write("[Venomous] " + this.droppedEntries + " older packet entries were discarded to limit memory usage.");
               writer.newLine();
            }
            for (String line : this.log) {
               writer.write(line);
               writer.newLine();
            }
         }
         this.info("Saved log to " + file, new Object[0]);
      } catch (IOException e) {
         this.error("Failed to save log: " + e.getMessage(), new Object[0]);
      } finally {
         this.log.clear();
         this.droppedEntries = 0;
      }
   }

   @EventHandler
   private void onSend(Send event) {
      if ((Boolean)this.outgoing.get()) {
         this.addEntry("[OUT] " + event.packet.getClass().getSimpleName());
      }
   }

   @EventHandler
   private void onReceive(Receive event) {
      if ((Boolean)this.incoming.get()) {
         this.addEntry("[IN] " + event.packet.getClass().getSimpleName());
      }
   }

   private void addEntry(String entry) {
      if (this.log.size() == MAX_LOG_ENTRIES) {
         this.log.removeFirst();
         this.droppedEntries++;
      }
      this.log.addLast(entry);
   }
}
