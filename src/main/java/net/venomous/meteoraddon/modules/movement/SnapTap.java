package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Input;
import net.venomous.meteoraddon.VenomousAddon;

public class SnapTap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> horizontal = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("horizontal")).description("Apply SnapTap to left/right keys.")).defaultValue(true)).build());
   private final Setting<Boolean> vertical = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("vertical")).description("Apply SnapTap to forward/back keys.")).defaultValue(true)).build());
   private long lastLeftPressTime = 0L;
   private long lastRightPressTime = 0L;
   private long lastForwardPressTime = 0L;
   private long lastBackPressTime = 0L;

   public SnapTap() {
      super(VenomousAddon.CATEGORY, "v-snap-tap", "Prioritize the last pressed movement key (SOCD).");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.player.input != null) {
         if (this.mc.options.keyLeft.isDown()) {
            this.lastLeftPressTime = System.nanoTime();
         }

         if (this.mc.options.keyRight.isDown()) {
            this.lastRightPressTime = System.nanoTime();
         }

         if (this.mc.options.keyUp.isDown()) {
            this.lastForwardPressTime = System.nanoTime();
         }

         if (this.mc.options.keyDown.isDown()) {
            this.lastBackPressTime = System.nanoTime();
         }

         boolean holdingLeft = this.mc.options.keyLeft.isDown();
         boolean holdingRight = this.mc.options.keyRight.isDown();
         boolean holdingForward = this.mc.options.keyUp.isDown();
         boolean holdingBack = this.mc.options.keyDown.isDown();
         Input keyPresses = this.mc.player.input.keyPresses;
         boolean inputForward = keyPresses.forward();
         boolean inputBackward = keyPresses.backward();
         boolean inputLeft = keyPresses.left();
         boolean inputRight = keyPresses.right();
         if ((Boolean)this.horizontal.get() && holdingLeft && holdingRight) {
            boolean rightIsLast = this.lastRightPressTime >= this.lastLeftPressTime;
            inputLeft = !rightIsLast;
            inputRight = rightIsLast;
         }

         if ((Boolean)this.vertical.get() && holdingForward && holdingBack) {
            boolean forwardIsLast = this.lastForwardPressTime >= this.lastBackPressTime;
            inputBackward = !forwardIsLast;
            inputForward = forwardIsLast;
         }

         this.mc.player.input.keyPresses = new Input(
            inputForward, inputBackward, inputLeft, inputRight, keyPresses.jump(), keyPresses.shift(), keyPresses.sprint()
         );
      }
   }
}
