package net.venomous.meteoraddon.modules.misc;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.venomous.meteoraddon.VenomousAddon;

public class MiddleClickAction extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> addFriend = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("add-friend")).description("Add player as friend on middle click.")).defaultValue(true)).build());
   private final Setting<Boolean> removeFriend = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("remove-friend")).description("Remove player as friend on middle click if already friended."))
               .defaultValue(true))
            .build()
      );
   private final Setting<MiddleClickAction.Technique> technique = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("technique"))
                  .description("Click pattern for middle click (humanlike)."))
               .defaultValue(MiddleClickAction.Technique.Stabilized))
            .build()
      );
   private final Setting<Integer> minCps = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("min-cps"))
                  .description("Minimum clicks per second."))
               .defaultValue(6))
            .range(1, 20)
            .sliderMax(15)
            .build()
      );
   private final Setting<Integer> maxCps = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-cps"))
                  .description("Maximum clicks per second."))
               .defaultValue(8))
            .range(1, 20)
            .sliderMax(15)
            .build()
      );
   private int ticksUntilClick = 0;
   private boolean wasMiddleDown = false;
   private final Random random = new Random();

   public MiddleClickAction() {
      super(VenomousAddon.CATEGORY, "middle-click-action", "Perform actions on middle click with humanlike timing.");
   }

   public void onActivate() {
      this.ticksUntilClick = 0;
      this.wasMiddleDown = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         boolean middleDown = this.mc.options.keyPickItem.isDown();
         if (middleDown && !this.wasMiddleDown) {
            this.handleClick();
         }

         this.wasMiddleDown = middleDown;
      }
   }

   private void handleClick() {
      if (this.mc.hitResult != null
         && this.mc.hitResult.getType() == Type.ENTITY
         && this.mc.level.getEntity(((EntityHitResult)this.mc.hitResult).getEntity().getId()) instanceof Player player) {
         if ((Boolean)this.addFriend.get() && !Friends.get().isFriend(player)) {
            Friends.get().add(new Friend(player));
            this.info("Added " + player.getName().getString() + " as friend.", new Object[0]);
         } else if ((Boolean)this.removeFriend.get() && Friends.get().isFriend(player)) {
            Friends.get().remove(new Friend(player));
            this.info("Removed " + player.getName().getString() + " from friends.", new Object[0]);
         }
      }
   }

   public enum Technique {
      Stabilized,
      Butterfly,
      NormalDistribution;
   }
}
