package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class Spammer extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<String> message = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("message")).description("Message to spam.")).defaultValue("Venomous on top!")).build());
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("delay"))
                  .description("Delay in ticks between messages."))
               .defaultValue(60))
            .min(1)
            .max(6000)
            .build()
      );
   private int tickCounter = 0;

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         this.tickCounter++;
         if (this.tickCounter >= (Integer)this.delay.get()) {
            String msg = (String)this.message.get();
            if (msg.startsWith("/")) {
               this.mc.player.connection.sendCommand(msg.substring(1));
            } else {
               this.mc.player.connection.sendChat(msg);
            }

            this.tickCounter = 0;
         }
      }
   }

   public Spammer() {
      super(VenomousAddon.CATEGORY, "spammer", "Spam chat with messages.");
   }
}
