package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.VenomousAddon;

public class CriticalsPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<CriticalsPlus.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode")).description("Criticals mode — select your anticheat."))
               .defaultValue(CriticalsPlus.Mode.GrimLegit))
            .build()
      );
   private final Setting<Double> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("delay"))
               .description("Delay between crit attempts in seconds."))
            .defaultValue(0.5)
            .range(0.0, 2.0)
            .sliderMax(1.5)
            .build()
      );
   private final Setting<Boolean> onlyOnGround = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-on-ground"))
                  .description("Only attempt crits when on ground."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> hurtTimeCheck = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("hurt-time-check"))
                  .description("Don't crit if target is in hurt time."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> grimMinFallTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("grim-min-fall-ticks"))
                     .description("Min ticks after jump before attacking (must be falling). Grim needs 2+."))
                  .defaultValue(2))
               .range(1, 10)
               .visible(() -> this.mode.get() == CriticalsPlus.Mode.GrimLegit || this.mode.get() == CriticalsPlus.Mode.GrimSlime))
            .build()
      );
   private final Setting<Boolean> grimWaitForAttackStrength = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("grim-wait-attack-strength"))
                     .description("Wait for attack cooldown before jumping (humanlike)."))
                  .defaultValue(true))
               .visible(() -> this.mode.get() == CriticalsPlus.Mode.GrimLegit || this.mode.get() == CriticalsPlus.Mode.GrimSlime))
            .build()
      );
   private final Setting<Integer> vulcanMinFallTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("vulcan-min-fall-ticks"))
                     .description("Min ticks after jump before attacking for Vulcan."))
                  .defaultValue(3))
               .range(1, 10)
               .visible(() -> this.mode.get() == CriticalsPlus.Mode.VulcanJump || this.mode.get() == CriticalsPlus.Mode.VulcanMini))
            .build()
      );
   private final Setting<Double> vulcanMiniHeight = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("vulcan-mini-height"))
                  .description("Mini-jump height for VulcanMini mode (0.1 = subtle)."))
               .defaultValue(0.12)
               .range(0.05, 0.42)
               .sliderMax(0.42)
               .visible(() -> this.mode.get() == CriticalsPlus.Mode.VulcanMini))
            .build()
      );
   private final Setting<Double> ncpMiniHeight = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("ncp-mini-height"))
                  .description("Mini-jump height for NCP mode."))
               .defaultValue(0.1)
               .range(0.05, 0.42)
               .sliderMax(0.42)
               .visible(() -> this.mode.get() == CriticalsPlus.Mode.NCPMini))
            .build()
      );
   private long lastCritTime = 0L;
   private int fallTicks = 0;
   private boolean waitingForCrit = false;
   private boolean hasAttackedThisJump = false;

   public CriticalsPlus() {
      super(VenomousAddon.CATEGORY, "v-lethality", "Criticals bypasses for Grim/Vulcan/NCP/Oxa/Negativity/LAC.");
   }

   public void onActivate() {
      this.lastCritTime = 0L;
      this.fallTicks = 0;
      this.waitingForCrit = false;
      this.hasAttackedThisJump = false;
   }

   @EventHandler
   private void onPacketSend(Send event) {
      if (this.mc.player != null) {
         Packet<?> packet = event.packet;
         if (packet instanceof ServerboundInteractPacket) {
            long now = System.currentTimeMillis();
            if (now - this.lastCritTime < (Double)this.delay.get() * 1000.0) {
               return;
            }

            if ((Boolean)this.onlyOnGround.get() && !this.mc.player.onGround() && !this.waitingForCrit) {
               return;
            }

            switch ((CriticalsPlus.Mode)this.mode.get()) {
               case GrimSlime:
               case GrimLegit:
                  if (!this.mc.player.onGround()
                     && this.mc.player.getDeltaMovement().y < 0.0
                     && !this.hasAttackedThisJump
                     && this.fallTicks >= (Integer)this.grimMinFallTicks.get()) {
                     this.lastCritTime = now;
                     this.hasAttackedThisJump = true;
                  } else if (this.mc.player.onGround()
                     && !this.waitingForCrit
                     && (!(Boolean)this.grimWaitForAttackStrength.get() || this.mc.player.getAttackStrengthScale(0.0F) > 0.9F)) {
                     this.mc.player.jumpFromGround();
                     this.waitingForCrit = true;
                     this.fallTicks = 0;
                     this.hasAttackedThisJump = false;
                  }
                  break;
               case VulcanJump:
                  if (!this.mc.player.onGround()
                     && this.mc.player.getDeltaMovement().y < 0.0
                     && !this.hasAttackedThisJump
                     && this.fallTicks >= (Integer)this.vulcanMinFallTicks.get()) {
                     this.lastCritTime = now;
                     this.hasAttackedThisJump = true;
                  } else if (this.mc.player.onGround() && !this.waitingForCrit) {
                     this.mc.player.jumpFromGround();
                     this.waitingForCrit = true;
                     this.fallTicks = 0;
                     this.hasAttackedThisJump = false;
                  }
                  break;
               case VulcanMini:
                  if (!this.mc.player.onGround()
                     && this.mc.player.getDeltaMovement().y < 0.0
                     && !this.hasAttackedThisJump
                     && this.fallTicks >= (Integer)this.vulcanMinFallTicks.get()) {
                     this.lastCritTime = now;
                     this.hasAttackedThisJump = true;
                  } else if (this.mc.player.onGround() && !this.waitingForCrit) {
                     this.mc
                        .player
                        .setDeltaMovement(this.mc.player.getDeltaMovement().x, (Double)this.vulcanMiniHeight.get(), this.mc.player.getDeltaMovement().z);
                     this.waitingForCrit = true;
                     this.fallTicks = 0;
                     this.hasAttackedThisJump = false;
                  }
                  break;
               case NCPMini:
                  if (this.mc.player.onGround()) {
                     this.mc
                        .player
                        .setDeltaMovement(this.mc.player.getDeltaMovement().x, (Double)this.ncpMiniHeight.get(), this.mc.player.getDeltaMovement().z);
                     this.lastCritTime = now;
                  }
                  break;
               case NCPJump:
               case Jump:
                  if (this.mc.player.onGround()) {
                     this.mc.player.jumpFromGround();
                     this.lastCritTime = now;
                  }
                  break;
               case Packet: {
                  double x = this.mc.player.getX();
                  double y = this.mc.player.getY();
                  double z = this.mc.player.getZ();
                  this.mc.player.connection.send(new Pos(x, y + 0.0625, z, false, false));
                  this.mc.player.connection.send(new Pos(x, y, z, false, false));
                  this.lastCritTime = now;
                  break;
               }
               case MiniJump:
                  if (this.mc.player.onGround()) {
                     this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.15, this.mc.player.getDeltaMovement().z);
                     this.lastCritTime = now;
                  }
                  break;
               case Blink: {
                  double x = this.mc.player.getX();
                  double y = this.mc.player.getY();
                  double z = this.mc.player.getZ();
                  this.mc.player.connection.send(new Pos(x, y + 0.2, z, false, false));
                  this.mc.player.connection.send(new Pos(x, y, z, true, false));
                  this.lastCritTime = now;
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         boolean isWaiting = this.waitingForCrit;
         if (isWaiting) {
            if (!this.mc.player.onGround()) {
               this.fallTicks++;
               if (this.mode.get() == CriticalsPlus.Mode.GrimSlime) {
                  this.checkSlimeProximity();
               }
               int minTicks = switch ((CriticalsPlus.Mode)this.mode.get()) {
                  case GrimSlime, GrimLegit -> this.grimMinFallTicks.get();
                  case VulcanJump, VulcanMini -> this.vulcanMinFallTicks.get();
                  default -> 1;
               };
               if (this.fallTicks >= minTicks && this.mc.player.getDeltaMovement().y < 0.0) {
                  this.waitingForCrit = false;
               }
            } else {
               this.waitingForCrit = false;
               this.hasAttackedThisJump = false;
            }
         }
      }
   }

   private void checkSlimeProximity() {
      if (this.mc.level != null && this.mc.player != null) {
         BlockPos pos = BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 1.0, this.mc.player.getZ());
         BlockState state = this.mc.level.getBlockState(pos);

         if (state.is(Blocks.SLIME_BLOCK)) {
            this.waitingForCrit = false;
            this.hasAttackedThisJump = false;
            this.fallTicks = 0;
         }
      }
   }

   public String getInfoString() {
      return ((CriticalsPlus.Mode)this.mode.get()).name();
   }

   public enum Mode {
      GrimSlime,
      GrimLegit,
      VulcanJump,
      VulcanMini,
      NCPMini,
      NCPJump,
      Packet,
      MiniJump,
      Blink,
      Jump;
   }
}
