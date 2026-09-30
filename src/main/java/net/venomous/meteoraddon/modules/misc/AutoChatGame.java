package net.venomous.meteoraddon.modules.misc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class AutoChatGame extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> math = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("math").description("Solve simple math questions in chat (ChatGames + generic).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> scramble = this.sgGeneral
      .add(new BoolSetting.Builder().name("scramble").description("Unscramble words from a word list.").defaultValue(false).build());
   private final Setting<Boolean> unreverse = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("unreverse").description("Answer ChatGames 'unreverse the word' games.").defaultValue(true)
            .build()
      );
   private final Setting<Boolean> random = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("random").description("Answer ChatGames 'write out' random-string games.").defaultValue(true)
            .build()
      );
   private final Setting<Boolean> variable = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("variable")
            .description("Solve ChatGames 'solve for <symbol>' variable games (needs the 3 equation lines).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("delay")
            .description("Delay before answering in ticks.")
            .defaultValue(20)
            .min(1)
            .sliderMax(100)
            .build()
      );
   private final Setting<List<String>> customPatterns = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
            .name("custom-patterns")
            .description("Custom regex patterns to match questions. Use group 1 for the math expression.")
            .defaultValue(new ArrayList())
            .build()
      );
   private static final Pattern MATH_PATTERN = Pattern.compile("(?:what\\s+is\\s+|calculate\\s+|solve\\s+)?([\\d]+\\s*[+\\-*/]\\s*[\\d]+)\\s*[?=]?", 2);
   private static final Pattern SAME_SUM = Pattern.compile("([A-Za-z])(\\s*\\+\\s*\\1)*\\s*=\\s*(-?\\d+)");
   private static final Pattern FINAL_EQ = Pattern.compile("([A-Za-z]\\s*\\+\\s*)*([A-Za-z]|[+-]?\\d+)\\s*\\+\\s*([+-]?\\d+|[A-Za-z])\\s*=\\s*(-?\\d+)");
   private static final int RECENT_MAX = 8;
   private static final int VAR_WINDOW_TICKS = 60;
   private int answerDelay = 0;
   private String pendingAnswer = null;
   private final Deque<String> recentLines = new ArrayDeque<>();
   private String pendingVarSymbol = null;
   private int varWindowTicks = 0;

   public AutoChatGame() {
      super(VenomousAddon.CATEGORY, "autochatgame", "Auto-solve ChatGames 1.2.1 (math, unreverse, random, variable).");
   }

   public void onActivate() {
      this.pendingAnswer = null;
      this.answerDelay = 0;
      this.recentLines.clear();
      this.pendingVarSymbol = null;
      this.varWindowTicks = 0;
   }

   @Override
   public void onDeactivate() {
      this.pendingAnswer = null;
      this.answerDelay = 0;
      this.recentLines.clear();
      this.pendingVarSymbol = null;
      this.varWindowTicks = 0;
   }

   @EventHandler
   private void onReceiveMessage(ReceiveMessageEvent var1) {
      if (this.isActive() && this.pendingAnswer == null) {
         String var2 = var1.getMessage().getString().trim();
         this.pushRecent(strip(var2));
         AutoChatGame.GameType var3 = detect(var2);
         switch (var3) {
            case UNREVERSE:
               if ((Boolean)this.unreverse.get()) {
                  String var18 = backtickPayload(var2);
                  if (var18 != null) {
                     this.queueAnswer(solveUnreverse(var18), "unreverse");
                  }
               }

               return;
            case REACTION:
            case FILLOUT:
            default:
               if (this.pendingVarSymbol != null) {
                  String var15 = solveVariable(new ArrayList<>(this.recentLines), this.pendingVarSymbol);
                  if (var15 != null) {
                     this.queueAnswer(var15, "variable");
                     this.pendingVarSymbol = null;
                     this.varWindowTicks = 0;
                     return;
                  }
               }

               if ((Boolean)this.math.get()) {
                  Matcher var16 = MATH_PATTERN.matcher(var2);
                  if (var16.find()) {
                     String var19 = var16.group(1).replaceAll("\\s+", "");

                     try {
                        double var6 = this.evaluateExpression(var19);
                        this.queueAnswer(formatResult(var6), "math");
                        this.info("Detected math: %s = %s", new Object[]{var19, this.pendingAnswer});
                        return;
                     } catch (Exception ignored) {
                        // Not a solvable expression - fall through to the next strategy.
                     }
                  }
               }

               for (String var20 : this.customPatterns.get()) {
                  try {
                     Matcher var21 = Pattern.compile(var20).matcher(var2);
                     if (var21.find() && var21.groupCount() >= 1) {
                        String var7 = var21.group(1).replaceAll("\\s+", "");

                        try {
                           double var8 = this.evaluateExpression(var7);
                           this.queueAnswer(formatResult(var8), "custom-pattern");
                           this.info("Custom pattern match: %s = %s", new Object[]{var7, this.pendingAnswer});
                           return;
                        } catch (Exception var10) {
                           this.queueAnswer(var7, "custom-pattern");
                           return;
                        }
                     }
                  } catch (java.util.regex.PatternSyntaxException e) {
                     // A bad user-supplied pattern is a config error, not a parse miss - surface it.
                     this.error("Invalid custom pattern \"%s\": %s", var20, e.getDescription());
                  } catch (Exception ignored) {
                     // Pattern compiled but didn't apply cleanly - try the next one.
                  }
               }

               return;
            case RANDOM:
               if ((Boolean)this.random.get()) {
                  String var14 = backtickPayload(var2);
                  if (var14 != null) {
                     this.queueAnswer(solveRandom(var14), "random");
                  }
               }

               return;
            case MATH:
               if ((Boolean)this.math.get()) {
                  String var13 = backtickPayload(var2);
                  if (var13 != null) {
                     this.queueAnswer(solveMath(var13), "math");
                  }
               }

               return;
            case VARIABLE:
               if ((Boolean)this.variable.get()) {
                  String var4 = backtickPayload(var2);
                  if (var4 != null) {
                     String var5 = solveVariable(new ArrayList<>(this.recentLines), var4);
                     if (var5 != null) {
                        this.queueAnswer(var5, "variable");
                     } else {
                        this.pendingVarSymbol = var4;
                        this.varWindowTicks = 60;
                     }
                  }
               }
         }
      }
   }

   @EventHandler
   private void onTick(Pre var1) {
      if (this.varWindowTicks > 0) {
         this.varWindowTicks--;
         if (this.varWindowTicks == 0) {
            this.pendingVarSymbol = null;
         }
      }

      if (this.pendingAnswer != null && this.mc.player != null) {
         if (this.answerDelay > 0) {
            this.answerDelay--;
         } else {
            ChatUtils.sendPlayerMsg(this.pendingAnswer);
            this.pendingAnswer = null;
         }
      }
   }

   private void queueAnswer(String var1, String var2) {
      if (var1 != null && !var1.isEmpty() && !"<unable to solve>".equals(var1)) {
         this.pendingAnswer = var1;
         this.answerDelay = (Integer)this.delay.get();
         this.info("Detected %s: answer = %s", new Object[]{var2, var1});
      }
   }

   private static String strip(String var0) {
      return var0 == null ? "" : var0.replaceAll("&[0-9a-fk-orA-FK-OR]", "").replace("`", "").trim();
   }

   private static AutoChatGame.GameType detect(String var0) {
      String var1 = strip(var0).toLowerCase();
      if (var1.contains("to unscramble")) {
         return AutoChatGame.GameType.UNSCRAMBLE;
      } else if (var1.contains("to unreverse")) {
         return AutoChatGame.GameType.UNREVERSE;
      } else if (var1.contains("to write out the word")) {
         return AutoChatGame.GameType.REACTION;
      } else if (var1.contains("to fill in")) {
         return AutoChatGame.GameType.FILLOUT;
      } else if (var1.contains("to write out")) {
         return AutoChatGame.GameType.RANDOM;
      } else if (var1.contains("to solve for")) {
         return AutoChatGame.GameType.VARIABLE;
      } else if (var1.contains("to solve")) {
         return AutoChatGame.GameType.MATH;
      } else {
         return var1.contains("question:") ? AutoChatGame.GameType.TRIVIA : AutoChatGame.GameType.UNKNOWN;
      }
   }

   private static String backtickPayload(String var0) {
      int var1 = var0.lastIndexOf(96);
      if (var1 < 0) {
         return null;
      }

      int var2 = var0.lastIndexOf(96, var1 - 1);
      return var2 < 0 ? null : var0.substring(var2 + 1, var1);
   }

   private void pushRecent(String var1) {
      this.recentLines.addLast(var1);

      while (this.recentLines.size() > 8) {
         this.recentLines.pollFirst();
      }
   }

   private static String formatResult(double var0) {
      return var0 == (int)var0 ? String.valueOf((int)var0) : String.valueOf(Math.round(var0 * 100.0) / 100.0);
   }

   private static String solveMath(String var0) {
      try {
         String[] var1 = var0.trim().split("\\s+");
         if (var1.length < 3) {
            return "<unable to solve>";
         }

         int var2 = Integer.parseInt(var1[0]);

         for (byte var3 = 1; var3 + 1 < var1.length; var3 = (byte)(var3 + 2)) {
            String var4 = var1[var3];
            int var5 = Integer.parseInt(var1[var3 + 1]);
            switch (var4) {
               case "+":
                  var2 += var5;
                  break;
               case "-":
                  var2 -= var5;
                  break;
               case "*":
                  var2 *= var5;
                  break;
               case "/":
                  if (var5 == 0) {
                     return "<unable to solve>";
                  }

                  var2 /= var5;
                  break;
               default:
                  return "<unable to solve>";
            }
         }

         return Integer.toString(var2);
      } catch (Exception var8) {
         return "<unable to solve>";
      }
   }

   private static String solveUnreverse(String var0) {
      return new StringBuilder(var0).reverse().toString();
   }

   private static String solveRandom(String var0) {
      return var0;
   }

   private static String solveVariable(List<String> var0, String var1) {
      if (var0 != null && !var0.isEmpty() && var1 != null) {
         Integer var2 = null;
         Integer var3 = null;
         Integer var4 = null;

         for (String var6 : var0) {
            String var7 = strip(var6);
            if (!var7.isEmpty()) {
               Matcher var8 = SAME_SUM.matcher(var7);
               if (var8.find()) {
                  String var9 = var8.group(1);
                  if (!var9.equalsIgnoreCase(var1)) {
                     try {
                        int var10 = Integer.parseInt(var8.group(3));
                        int var11 = countOccurrences(var7, var9);
                        int var12 = var11 == 0 ? var10 : var10 / var11;
                        if (var2 == null) {
                           var2 = var12;
                        } else if (var3 == null) {
                           var3 = var12;
                        }
                     } catch (Exception ignored) {
                        // Operand wasn't numeric - skip this candidate.
                     }
                  }
               } else {
                  var8 = FINAL_EQ.matcher(var7);
                  if (var8.find()) {
                     try {
                        var4 = Integer.parseInt(var8.group(4));
                     } catch (NumberFormatException ignored) {
                        // Final-equation group wasn't an integer - leave the answer unset.
                     }
                  }
               }
            }
         }

         return var2 != null && var3 != null && var4 != null ? Integer.toString(var4 - var2 - var3) : null;
      } else {
         return null;
      }
   }

   private static int countOccurrences(String var0, String var1) {
      int var2 = 0;
      int var3 = 0;

      while ((var3 = var0.indexOf(var1, var3)) != -1) {
         var2++;
         var3 += var1.length();
      }

      return var2;
   }

   private double evaluateExpression(String var1) {
      if (var1.contains("+")) {
         String[] var5 = var1.split("\\+", 2);
         return Double.parseDouble(var5[0]) + Double.parseDouble(var5[1]);
      } else if (var1.contains("-")) {
         String[] var4 = var1.split("-", 2);
         return Double.parseDouble(var4[0]) - Double.parseDouble(var4[1]);
      } else if (var1.contains("*")) {
         String[] var3 = var1.split("\\*", 2);
         return Double.parseDouble(var3[0]) * Double.parseDouble(var3[1]);
      } else if (var1.contains("/")) {
         String[] var2 = var1.split("/", 2);
         return Double.parseDouble(var2[0]) / Double.parseDouble(var2[1]);
      } else {
         throw new IllegalArgumentException("Unknown operator");
      }
   }

   private enum GameType {
      UNSCRAMBLE,
      UNREVERSE,
      REACTION,
      FILLOUT,
      RANDOM,
      MATH,
      VARIABLE,
      TRIVIA,
      UNKNOWN;
   }
}
