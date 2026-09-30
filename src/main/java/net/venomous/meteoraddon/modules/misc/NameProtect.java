package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class NameProtect extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> protectSelf = this.sgGeneral
      .add(new BoolSetting.Builder().name("protect-self").description("Replace your own username.").defaultValue(true).build());
   private final Setting<Boolean> protectOthers = this.sgGeneral
      .add(new BoolSetting.Builder().name("protect-others").description("Replace other player usernames.").defaultValue(false).build());
   private final Setting<String> replacement = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("replacement")
            .description("Text to replace usernames with.")
            .defaultValue("Protected")
            .build()
      );

   public NameProtect() {
      super(VenomousAddon.CATEGORY, "nameprotect", "Replaces player names in chat messages for privacy.");
   }

   @EventHandler
   private void onReceiveMessage(ReceiveMessageEvent event) {
      if (this.mc.player != null) {
         String text = event.getMessage().getString();
         String newText = this.replaceNames(text);
         if (!text.equals(newText)) {
            event.setCancelled(true);
            this.info(newText, new Object[0]);
         }
      }
   }

   public String replaceNames(String text) {
      if (this.isActive() && this.mc.player != null) {
         if ((Boolean)this.protectSelf.get()) {
            String name = this.mc.player.getName().getString();
            if (text.contains(name)) {
               text = text.replace(name, (CharSequence)this.replacement.get());
            }
         }

         if ((Boolean)this.protectOthers.get() && this.mc.level != null) {
            for (Player p : this.mc.level.players()) {
               if (p != this.mc.player) {
                  String name = p.getName().getString();
                  if (text.contains(name)) {
                     text = text.replace(name, (CharSequence)this.replacement.get());
                  }
               }
            }
         }
      }

      return text;
   }
}
