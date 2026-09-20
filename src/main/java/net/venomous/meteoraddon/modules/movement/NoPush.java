package net.venomous.meteoraddon.modules.movement;

import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Prevents the player from being pushed around.
 *
 * <p>Two things were wrong here. First, the {@code push-by} setting was a single-choice enum
 * dropdown, so you could only ever suppress one push source at a time. It is now a multi-select
 * list, which is what the setting always wanted to be.
 *
 * <p>Second — and more importantly — the enum value was never actually read. The tick handler only
 * implemented the anti-sinking branch, so selecting ENTITIES, BLOCKS, FISHING_ROD or LIQUIDS did
 * nothing whatsoever. Entity, block and fluid pushing all happen inside vanilla methods that a tick
 * handler cannot intercept, so those sources are now handled by dedicated mixins which call back
 * into {@link #suppresses(Source)}.
 */
public class NoPush extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<List<String>> pushBy = this.sgGeneral.add(
      new StringListSetting.Builder()
         .name("push-by")
         .description("Which push sources to block: entities, blocks, fishing-rod, liquids, sinking.")
         .defaultValue(List.of(Source.ENTITIES.key, Source.LIQUIDS.key, Source.SINKING.key))
         .build()
   );

   private final Setting<Boolean> onlyWhileSneaking = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("only-while-sneaking")
         .description("Only suppress pushing while you are sneaking.")
         .defaultValue(false)
         .build()
   );

   public NoPush() {
      super(VenomousAddon.CATEGORY, "no-push", "Disables being pushed by entities, liquids, blocks and fishing rods.");
   }

   /**
    * Whether {@code source} should currently be suppressed.
    *
    * <p>Static so the mixins can consult it without holding a module reference.
    */
   public static boolean blocks(Source source) {
      NoPush module = Modules.get().get(NoPush.class);
      return module != null && module.isActive() && module.suppresses(source);
   }

   public boolean suppresses(Source source) {
      if (this.onlyWhileSneaking.get() && this.mc.player != null && !this.mc.player.isShiftKeyDown()) {
         return false;
      }

      for (String entry : this.pushBy.get()) {
         if (entry != null && entry.trim().replace('_', '-').equalsIgnoreCase(source.key)) {
            return true;
         }
      }
      return false;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null) {
         return;
      }

      if (this.suppresses(Source.SINKING)
         && !this.mc.options.keyJump.isDown()
         && !this.mc.options.keyShift.isDown()
         && this.mc.player.isInWater()
         && this.mc.player.getDeltaMovement().y < 0.0) {
         this.mc.player.setDeltaMovement(
            this.mc.player.getDeltaMovement().x,
            0.0,
            this.mc.player.getDeltaMovement().z
         );
      }
   }

   @Override
   public String getInfoString() {
      return String.join(", ", this.pushBy.get());
   }

   /** A source of unwanted motion. */
   public enum Source {
      ENTITIES("entities"),
      BLOCKS("blocks"),
      FISHING_ROD("fishing-rod"),
      LIQUIDS("liquids"),
      SINKING("sinking");

      public final String key;

      Source(String key) {
         this.key = key;
      }
   }
}
