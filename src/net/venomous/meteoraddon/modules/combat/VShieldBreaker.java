package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.AABB;
import net.venomous.meteoraddon.VenomousAddon;

public class VShieldBreaker extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range;
   private final Setting<Boolean> autoSwap;
   private final Setting<Boolean> swapBack;
   private final Setting<Integer> swapDelay;
   private final Setting<Boolean> requireWeapon;
   private VShieldBreaker.ShieldBreakerState state = VShieldBreaker.ShieldBreakerState.IDLE;
   private Player targetPlayer = null;
   private int originalSlot = -1;
   private int tickCounter = 0;

   public VShieldBreaker() {
      super(VenomousAddon.CATEGORY, "v-shield-breaker", "Automatically swaps to axe to break shields, then switches back.");
      this.range = this.sgGeneral
         .add(
            ((Builder)((Builder)new Builder().name("range")).description("Range to detect shielded players."))
               .defaultValue(5.0)
               .min(1.0)
               .max(10.0)
               .sliderMax(10.0)
               .build()
         );
      this.autoSwap = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("auto-swap"))
                     .description("Automatically swap to axe."))
                  .defaultValue(true))
               .build()
         );
      this.swapBack = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("swap-back"))
                     .description("Swap back to weapon after breaking shield."))
                  .defaultValue(true))
               .build()
         );
      this.swapDelay = null;
      this.requireWeapon = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("require-weapon"))
                     .description("Only activate when holding a weapon."))
                  .defaultValue(true))
               .build()
         );
   }

   public void onActivate() {
      this.resetState();
   }

   public void onDeactivate() {
      this.resetState();
   }

   private void resetState() {
      this.state = VShieldBreaker.ShieldBreakerState.IDLE;
      this.targetPlayer = null;
      this.originalSlot = -1;
      this.tickCounter = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.gameMode != null) {
         switch (this.state) {
            case IDLE:
               if (!this.mc.options.keyAttack.isDown()) {
                  return;
               }

               if ((Boolean)this.requireWeapon.get() && !this.mc.player.getMainHandItem().getItem().toString().toLowerCase().contains("sword")) {
                  return;
               }

               this.targetPlayer = this.findShieldedPlayer();
               if (this.targetPlayer != null) {
                  this.originalSlot = this.mc.player.getInventory().getSelectedSlot();
                  this.state = VShieldBreaker.ShieldBreakerState.SWITCHING_AXE;
                  this.tickCounter = 0;
               }
               break;
            case SWITCHING_AXE:
               int axeSlot = this.findAxeSlot();
               if (axeSlot == -1) {
                  this.state = VShieldBreaker.ShieldBreakerState.IDLE;
                  return;
               }

               InvUtils.swap(axeSlot, false);
               this.state = VShieldBreaker.ShieldBreakerState.BREAKING;
               this.tickCounter = 0;
               break;
            case BREAKING:
               if (this.targetPlayer != null && this.targetPlayer.isBlocking()) {
                  this.mc.gameMode.attack(this.mc.player, this.targetPlayer);
                  this.mc.player.swing(InteractionHand.MAIN_HAND);
                  if (!this.targetPlayer.isBlocking()) {
                     this.state = VShieldBreaker.ShieldBreakerState.KILLING;
                     this.tickCounter = 0;
                  }
                  break;
               }

               this.state = this.swapBack.get() ? VShieldBreaker.ShieldBreakerState.SWITCHING_BACK : VShieldBreaker.ShieldBreakerState.IDLE;
               return;
            case SWITCHING_BACK:
               if (this.originalSlot != -1) {
                  InvUtils.swap(this.originalSlot, false);
               }

               this.state = VShieldBreaker.ShieldBreakerState.IDLE;
               break;
            case KILLING:
               if ((Boolean)this.swapBack.get() && this.originalSlot != -1) {
                  InvUtils.swap(this.originalSlot, false);
               }

               this.state = VShieldBreaker.ShieldBreakerState.IDLE;
         }
      }
   }

   private Player findShieldedPlayer() {
      if (this.mc.level != null && this.mc.player != null) {
         for (Player player : this.mc
            .level
            .getEntitiesOfClass(
               Player.class,
               new AABB(
                  this.mc.player.getX() - (Double)this.range.get(),
                  this.mc.player.getY() - (Double)this.range.get(),
                  this.mc.player.getZ() - (Double)this.range.get(),
                  this.mc.player.getX() + (Double)this.range.get(),
                  this.mc.player.getY() + (Double)this.range.get(),
                  this.mc.player.getZ() + (Double)this.range.get()
               )
            )) {
            if (player != this.mc.player && player.isBlocking() && this.mc.player.distanceTo(player) <= (Double)this.range.get()) {
               return player;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private int findAxeSlot() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getItem(i).getItem() instanceof AxeItem) {
            return i;
         }
      }

      return -1;
   }

   public String getInfoString() {
      return switch (this.state) {
         case IDLE -> null;
         case SWITCHING_AXE -> "Switching to Axe";
         case BREAKING -> "Breaking Shield";
         case SWITCHING_BACK -> "Switching to Weapon";
         case KILLING -> "Executing Kill";
      };
   }

   private enum ShieldBreakerState {
      IDLE,
      SWITCHING_AXE,
      BREAKING,
      SWITCHING_BACK,
      KILLING;
   }
}
