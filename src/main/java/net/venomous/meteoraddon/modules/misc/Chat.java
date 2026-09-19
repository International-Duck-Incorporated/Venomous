package net.venomous.meteoraddon.modules.misc;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.venomous.meteoraddon.VenomousAddon;

public class Chat extends Module {
   static final Set<String> BLOCKED = new HashSet<>(Arrays.asList("badword", "anotherblockedword", "a blocked phrase"));
   static final List<Chat.Mode> AUTO_SEQUENCE = Arrays.asList(Chat.Mode.HOMOGLYPH, Chat.Mode.ZERO_WIDTH, Chat.Mode.LEET, Chat.Mode.COMBO);
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
   static final Map<Character, Character> HOMOGLYPHS_REV = Map.ofEntries(
      Map.entry('а', 'a'),
      Map.entry('е', 'e'),
      Map.entry('о', 'o'),
      Map.entry('с', 'c'),
      Map.entry('р', 'p'),
      Map.entry('х', 'x'),
      Map.entry('у', 'y'),
      Map.entry('ѕ', 's'),
      Map.entry('і', 'i'),
      Map.entry('п', 'n'),
      Map.entry('м', 'm')
   );

   static boolean naiveBlocked(String var0) {
      String var1 = var0.toLowerCase(Locale.ROOT);

      for (String var3 : BLOCKED) {
         if (var1.contains(var3.toLowerCase(Locale.ROOT))) {
            return true;
         }
      }

      return false;
   }

   static boolean hardenedBlocked(String var0) {
      return naiveBlocked(normalize(var0));
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
            case 'a':
               var1.append('4');
               break;
            case 'b':
            case 'c':
            case 'd':
            case 'f':
            case 'g':
            case 'h':
            case 'j':
            case 'k':
            case 'l':
            case 'm':
            case 'n':
            case 'p':
            case 'q':
            case 'r':
            default:
               var1.append(var5);
               break;
            case 'e':
               var1.append('3');
               break;
            case 'i':
               var1.append('1');
               break;
            case 'o':
               var1.append('0');
               break;
            case 's':
               var1.append('5');
               break;
            case 't':
               var1.append('7');
         }
      }

      return var1.toString();
   }

   static String normalize(String var0) {
      var0 = var0.replaceAll("[\u200b\u200c\u200d\ufeff\u2060\u00ad]", "");
      var0 = Normalizer.normalize(var0, Form.NFKD).replaceAll("\\p{M}", "");
      StringBuilder var1 = new StringBuilder();

      for (char var5 : var0.toCharArray()) {
         var1.append(HOMOGLYPHS_REV.getOrDefault(var5, var5));
      }

      var0 = var1.toString();
      return var0.replace("1", "i").replace("0", "o").replace("3", "e").replace("4", "a").replace("@", "a").replace("5", "s").replace("7", "t");
   }

   static Chat.SendResult sendWithRetry(String var0, boolean var1, int var2) {
      Chat.SendResult var3 = new Chat.SendResult();
      String var4 = var0;

      for (int var5 = 1; var5 <= var2 + 1; var5++) {
         boolean var6 = var1 ? hardenedBlocked(var4) : naiveBlocked(var4);
         if (!var6) {
            var3.delivered = true;
            var3.finalText = var4;
            var3.attempts = var5;
            return var3;
         }

         if (var5 == var2 + 1) {
            break;
         }

         Chat.Mode var7 = AUTO_SEQUENCE.get((var5 - 1) % AUTO_SEQUENCE.size());
         var4 = apply(var7, var0);
      }

      var3.delivered = false;
      var3.finalText = var4;
      var3.attempts = var2 + 1;
      return var3;
   }

   static String codePoints(String var0) {
      StringBuilder var1 = new StringBuilder();
      var0.codePoints().forEach(var1x -> var1.append("U+").append(Integer.toHexString(var1x).toUpperCase()).append(" "));
      return var1.toString().trim();
   }

   public Chat() {
      super(VenomousAddon.CATEGORY, "chat", "Educational chat filter bypass demonstration.");
   }

   @EventHandler
   private void onReceiveMessage(ReceiveMessageEvent var1) {
      Component var2 = var1.getMessage();
      if (var2 != null) {
         String var3 = var2.tryCollapseToString();
         if (var3 != null && var3.contains("Your message contains inappropriate language and was not sent.")) {
            String var4 = homoglyph("a blocked phrase");
            ChatUtils.sendPlayerMsg(var4);
         }
      }
   }

   enum Mode {
      HOMOGLYPH,
      ZERO_WIDTH,
      LEET,
      COMBO;
   }

   static class SendResult {
      boolean delivered;
      String finalText;
      int attempts;
   }
}
