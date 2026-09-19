package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
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
      .add(new BoolSetting.Builder().name("add-friend").description("Add player as friend on middle click.").defaultValue(true).build());
   private final Setting<Boolean> removeFriend = this.sgGeneral
      .add(new BoolSetting.Builder().name("remove-friend").description("Remove player as friend on middle click if already friended.").defaultValue(true).build());

   private boolean wasMiddleDown = false;

   public MiddleClickAction() {
      super(VenomousAddon.CATEGORY, "v-middle-click-action", "Perform friend management actions on middle click.");
   }

   @Override
   public void onActivate() {
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
         && ((EntityHitResult) this.mc.hitResult).getEntity() instanceof Player player) {
         if (this.addFriend.get() && !Friends.get().isFriend(player)) {
            Friends.get().add(new Friend(player));
            this.info("Added " + player.getName().getString() + " as friend.", new Object[0]);
         } else if (this.removeFriend.get() && Friends.get().isFriend(player)) {
            Friends.get().remove(new Friend(player));
            this.info("Removed " + player.getName().getString() + " from friends.", new Object[0]);
         }
      }
   }
}
