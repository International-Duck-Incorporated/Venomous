package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

public class TpAura extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<TpAura.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode")).description("TP mode. NONE work on Grim/Vulcan/Intave!")).defaultValue(TpAura.Mode.WeakAC))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("Teleport range."))
            .defaultValue(6.0)
            .min(1.0)
            .max(20.0)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("delay"))
                  .description("Delay between attacks."))
               .defaultValue(10))
            .min(1)
            .sliderMax(40)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Rotate to target."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> players = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("players"))
                  .description("Target players."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> mobs = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("mobs"))
                  .description("Target mobs."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> weakACInterval = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("weak-ac-interval"))
                     .description("Attack interval for WeakAC mode (must be > Oxa's 5-tick check window)."))
                  .defaultValue(6))
               .range(5, 20)
               .visible(() -> this.mode.get() == TpAura.Mode.WeakAC))
            .build()
      );
   private int tickCounter = 0;

   public TpAura() {
      super(VenomousAddon.CATEGORY, "v-phantom", "TP-aura for weak ACs (Oxa/Negativity/LAC). Does NOT work on Grim/Vulcan!");
   }

   public void onActivate() {
      this.tickCounter = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.player.connection != null) {
         this.tickCounter++;
         int effectiveDelay = this.mode.get() == TpAura.Mode.WeakAC ? (Integer)this.weakACInterval.get() : (Integer)this.delay.get();
         if (this.tickCounter >= effectiveDelay) {
            Entity target = this.findTarget();
            if (target != null) {
               double dx = target.getX() - this.mc.player.getX();
               double dz = target.getZ() - this.mc.player.getZ();
               if (!(Math.sqrt(dx * dx + dz * dz) > (Double)this.range.get())) {
                  double origX = this.mc.player.getX();
                  double origY = this.mc.player.getY();
                  double origZ = this.mc.player.getZ();
                  this.mc.player.connection.send(new Pos(target.getX(), target.getY(), target.getZ(), this.mc.player.onGround(), false));
                  if ((Boolean)this.rotate.get()) {
                     this.mc.player.setYRot((float)Math.toDegrees(Math.atan2(-dx, dz)));
                     this.mc.player.setXRot(70.0F);
                  }

                  this.mc.player.attack(target);
                  this.mc.player.swing(InteractionHand.MAIN_HAND);
                  this.mc.player.resetAttackStrengthTicker();
                  this.tickCounter = 0;
                  this.mc.player.connection.send(new Pos(origX, origY, origZ, this.mc.player.onGround(), false));
               }
            }
         }
      }
   }

   private Entity findTarget() {
      Entity closest = null;
      double closestDist = (Double)this.range.get() * (Double)this.range.get();

      for (Entity e : this.mc.level.entitiesForRendering()) {
         if (e != this.mc.player
            && e.isAlive()
            && !(
               e instanceof Player p
                  ? p.isSpectator() || !Friends.get().shouldAttack(p) || !(Boolean)this.players.get()
                  : !(e instanceof LivingEntity) || !(Boolean)this.mobs.get()
            )) {
            double d = this.mc.player.distanceToSqr(e);
            if (d < closestDist) {
               closestDist = d;
               closest = e;
            }
         }
      }

      return closest;
   }

   public String getInfoString() {
      return ((TpAura.Mode)this.mode.get()).name() + (this.mode.get() == TpAura.Mode.WeakAC ? " (Oxa/Neg/LAC)" : "");
   }

   public enum Mode {
      WeakAC,
      Vanilla;
   }
}
