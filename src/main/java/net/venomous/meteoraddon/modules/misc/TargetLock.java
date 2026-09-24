package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

public class TargetLock extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(((Builder)((Builder)new Builder().name("range")).description("Maximum range to maintain lock.")).defaultValue(20.0).min(1.0).max(64.0).build());
   private final Setting<Integer> maxTime = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-time"))
                  .description("Maximum time in seconds to keep lock."))
               .defaultValue(30))
            .min(1)
            .sliderMax(120)
            .build()
      );
   private final Setting<Boolean> combatOnly = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("combat-only"))
                  .description("Only lock for combat modules."))
               .defaultValue(false))
            .build()
      );
   private Integer lockedEntityId = null;
   private long lockTime = 0L;

   public TargetLock() {
      super(VenomousAddon.CATEGORY, "targetlock", "Locks onto a target player when attacking.");
   }

   @Override
   public void onActivate() {
      this.lockedEntityId = null;
      this.lockTime = 0L;
   }

   @Override
   public void onDeactivate() {
      this.lockedEntityId = null;
      this.lockTime = 0L;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (event.entity instanceof Player target) {
         if (this.lockedEntityId == null || this.lockedEntityId != target.getId()) {
            this.info("Locked onto %s.", new Object[]{target.getName().getString()});
         }

         this.lockedEntityId = target.getId();
         this.lockTime = System.currentTimeMillis();
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.lockedEntityId != null) {
         Entity locked = this.mc.level.getEntity(this.lockedEntityId);
         if (locked != null && !locked.isRemoved()) {
            if (this.mc.player.distanceToSqr(locked) > (Double)this.range.get() * (Double)this.range.get()) {
               this.info("Target out of range.", new Object[0]);
               this.lockedEntityId = null;
            } else {
               long elapsed = (System.currentTimeMillis() - this.lockTime) / 1000L;
               if (elapsed >= ((Integer)this.maxTime.get()).intValue()) {
                  this.info("Lock expired.", new Object[0]);
                  this.lockedEntityId = null;
               } else if (this.mc.player.isDeadOrDying()) {
                  this.lockedEntityId = null;
               }
            }
         } else {
            this.info("Target lost.", new Object[0]);
            this.lockedEntityId = null;
         }
      }
   }

   public boolean isLocked(Entity entity) {
      return this.isActive() && this.lockedEntityId != null ? entity.getId() == this.lockedEntityId : false;
   }

   public boolean shouldAttack(Entity entity) {
      if (!this.isActive()) {
         return true;
      } else if (this.lockedEntityId == null) {
         return true;
      } else {
         return !(entity instanceof Player) ? true : entity.getId() == this.lockedEntityId;
      }
   }

   public String getInfoString() {
      if (this.lockedEntityId == null) {
         return null;
      }

      Entity e = this.mc.level != null ? this.mc.level.getEntity(this.lockedEntityId) : null;
      return e != null ? e.getName().getString() : null;
   }
}
