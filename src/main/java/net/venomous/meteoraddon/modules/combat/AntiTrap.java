package net.venomous.meteoraddon.modules.combat;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiTrap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> notifications;
   private final Setting<Boolean> armorStands;
   private final Setting<Boolean> chestMinecarts;
   private final List<Entity> hiddenEntities = new ArrayList<>();

   public AntiTrap() {
      super(VenomousAddon.CATEGORY, "anti-trap", "Hides armor stands and chest minecarts that trap you.");
      this.notifications = this.sgGeneral
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("notifications")).description("Notify in chat when a trap entity is hidden.")).defaultValue(true))
               .build()
         );
      this.armorStands = this.sgGeneral
         .add(((Builder)((Builder)((Builder)new Builder().name("armor-stands")).description("Hide armor stands near you.")).defaultValue(true)).build());
      this.chestMinecarts = this.sgGeneral
         .add(((Builder)((Builder)((Builder)new Builder().name("chest-minecarts")).description("Hide chest minecarts near you.")).defaultValue(true)).build());
   }

   public void onDeactivate() {
      this.hiddenEntities.clear();
   }

   @EventHandler
   private void onEntityAdded(EntityAddedEvent event) {
      if (this.mc.player != null) {
         Entity entity = event.entity;
         if (this.isTrapEntity(entity) && entity.distanceTo(this.mc.player) < 5.0) {
            this.hideEntity(entity);
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.level != null && this.mc.player != null) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (this.isTrapEntity(entity) && entity.distanceTo(this.mc.player) < 5.0) {
               this.hideEntity(entity);
            }
         }
      }
   }

   private void hideEntity(Entity entity) {
      if (!this.hiddenEntities.contains(entity)) {
         this.hiddenEntities.add(entity);
         this.mc.level.removeEntity(entity.getId(), RemovalReason.DISCARDED);
         if ((Boolean)this.notifications.get()) {
            this.info("Hid trap entity: " + entity.getType().getDescription().getString(), new Object[0]);
         }
      }
   }

   private boolean isTrapEntity(Entity entity) {
      return this.armorStands.get() && entity instanceof ArmorStand
         ? true
         : (Boolean)this.chestMinecarts.get() && (entity instanceof MinecartChest || entity instanceof MinecartHopper);
   }
}
