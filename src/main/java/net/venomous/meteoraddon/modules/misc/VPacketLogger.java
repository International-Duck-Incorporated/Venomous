package net.venomous.meteoraddon.modules.misc;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class VPacketLogger extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> incoming = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("incoming")).description("Log incoming packets.")).defaultValue(false)).build());
   private final Setting<Boolean> outgoing = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("outgoing")).description("Log outgoing packets.")).defaultValue(true)).build());
   private final List<String> log = new ArrayList<>();

   public VPacketLogger() {
      super(VenomousAddon.CATEGORY, "v-packetlogger", "Log packets to a file in venom-output.");
   }

   public void onActivate() {
      this.log.clear();
   }

   public void onDeactivate() {
      if (!this.log.isEmpty()) {
         try {
            File dir = new File("venom-output");
            dir.mkdirs();
            File file = new File(dir, "packets_" + System.currentTimeMillis() + ".txt");
            FileWriter writer = new FileWriter(file);

            for (String line : this.log) {
               writer.write(line + "\n");
            }

            writer.close();
            this.info("Saved log to " + file.getName(), new Object[0]);
         } catch (Exception e) {
            this.error("Failed to save log: " + e.getMessage(), new Object[0]);
         }
      }
   }

   @EventHandler
   private void onSend(Send event) {
      if ((Boolean)this.outgoing.get()) {
         this.log.add("[OUT] " + event.packet.getClass().getSimpleName());
      }
   }

   @EventHandler
   private void onReceive(Receive event) {
      if ((Boolean)this.incoming.get()) {
         this.log.add("[IN] " + event.packet.getClass().getSimpleName());
      }
   }
}
