package net.venomous.meteoraddon.modules.misc;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Map;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class Chat extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Chat.Mode> mode = this.sgGeneral
      .add(
         new EnumSetting.Builder<Chat.Mode>()
            .name("mode")
            .description("The bypass technique to apply to outgoing chat.")
            .defaultValue(Chat.Mode.HOMOGLYPH)
            .build()
      );

   static final Map<Character, Character> HOMOGLYPHS = Map.ofEntries(
      Map.entry('a', 'а'),
      Map.entry('e', 'е'),
      Map.entry('o', 'о'),
      Map.entry('c', 'с'),
      Map.entry('p', 'р'),
      Map.entry('x', 'х'),
      Map.entry('y', 'у'),
      Map.entry('s', 'ѕ'),
      Map.entry('i', 'і'),
      Map.entry('n', 'п'),
      Map.entry('m', 'м')
   );

   public Chat() {
      super(VenomousAddon.CATEGORY, "chat-bypass", "Bypasses server chat filters using homoglyphs, zero-width characters, or leet speak.");
   }

   @EventHandler
   private void onSendMessage(SendMessageEvent event) {
      if (event.message == null || event.message.startsWith("/") || event.message.startsWith(".")) {
         return;
      }

      event.message = apply((Chat.Mode)this.mode.get(), event.message);
   }

   static String apply(Chat.Mode var0, String var1) {
      switch (var0) {
         case HOMOGLYPH:
            return homoglyph(var1);
         case ZERO_WIDTH:
            return zeroWidth(var1);
         case LEET:
            return leet(var1);
         case COMBO:
            return zeroWidth(homoglyph(var1));
         default:
            return var1;
      }
   }

   static String homoglyph(String var0) {
      StringBuilder var1 = new StringBuilder();
      for (char var5 : var0.toCharArray()) {
         var1.append(HOMOGLYPHS.getOrDefault(var5, var5));
      }
      return var1.toString();
   }

   static String zeroWidth(String var0) {
      StringBuilder var1 = new StringBuilder();
      for (char var5 : var0.toCharArray()) {
         var1.append(var5);
         var1.append('\u200b');
      }
      return var1.toString();
   }

   static String leet(String var0) {
      StringBuilder var1 = new StringBuilder();
      for (char var5 : var0.toCharArray()) {
         switch (var5) {
            case 'a': var1.append('4'); break;
            case 'e': var1.append('3'); break;
            case 'i': var1.append('1'); break;
            case 'o': var1.append('0'); break;
            case 's': var1.append('5'); break;
            case 't': var1.append('7'); break;
            default: var1.append(var5); break;
         }
      }
      return var1.toString();
   }

   public enum Mode {
      HOMOGLYPH,
      ZERO_WIDTH,
      LEET,
      COMBO;
   }
}
