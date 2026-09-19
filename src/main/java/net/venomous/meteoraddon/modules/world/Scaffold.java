package net.venomous.meteoraddon.modules.world;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class Scaffold extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGrim = this.settings.createGroup("Grim");
   private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
   private final SettingGroup sgBridge = this.settings.createGroup("Bridge Assist");
   private final SettingGroup sgExpand = this.settings.createGroup("Expand");
   private final SettingGroup sgTower = this.settings.createGroup("Tower");

   private final Setting<Scaffold.Technique> technique = this.sgGeneral.add(
      new EnumSetting.Builder<Scaffold.Technique>()
         .name("technique")
         .description("Scaffold technique — select your anticheat or bridging style.")
         .defaultValue(Scaffold.Technique.GrimScaffold)
         .build()
   );

   private final Setting<Boolean> autoSneak = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("auto-sneak")
         .description("Sneak at block edges (Eagle/BridgeAssist).")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> sameY = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("same-y")
         .description("Keep placing at the same Y level.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> swing = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("swing")
         .description("Swing hand when placing.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> autoBlockSwitch = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("auto-block-switch")
         .description("Auto-switch to blocks.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> serverSideRotations = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("server-side-rotations")
         .description("Spoof rotations to the server so you don't notice camera shifts.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Integer> placeDelay = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("place-delay")
         .description("Ticks between placement attempts. Higher values reduce packet spam.")
         .defaultValue(3)
         .range(1, 10)
         .sliderMax(10)
         .build()
   );

   private final Setting<Double> bridgeEdgeThreshold = this.sgBridge.add(
      new DoubleSetting.Builder()
         .name("edge-threshold")
         .description("Distance from a block edge before Bridge Assist sneaks.")
         .defaultValue(0.24)
         .range(0.05, 0.49)
         .sliderMax(0.4)
         .visible(this::isBridgeMode)
         .build()
   );

   private final Setting<Boolean> bridgePlaceAssist = this.sgBridge.add(
      new BoolSetting.Builder()
         .name("place-assist")
         .description("Automatically place blocks below or ahead while bridging.")
         .defaultValue(true)
         .visible(this::isBridgeMode)
         .build()
   );

   private final Setting<Boolean> bridgeCameraStabilisation = this.sgBridge.add(
      new BoolSetting.Builder()
         .name("camera-stabilisation")
         .description("Restore visual camera rotation immediately after bridge placement.")
         .defaultValue(true)
         .visible(() -> this.technique.get() == Scaffold.Technique.BridgeMacro || this.technique.get() == Scaffold.Technique.VapeBridge)
         .build()
   );

   private final Setting<Boolean> bridgePitchAssist = this.sgBridge.add(
      new BoolSetting.Builder()
         .name("pitch-assist")
         .description("Automatically pitch down towards placement face when nearing block edges.")
         .defaultValue(true)
         .visible(() -> this.technique.get() == Scaffold.Technique.VapeBridge || this.technique.get() == Scaffold.Technique.GodBridge)
         .build()
   );

   private final Setting<Boolean> bridgeStrafeAssist = this.sgBridge.add(
      new BoolSetting.Builder()
         .name("strafe-assist")
         .description("Smooth strafe lock along bridge vector (keeps movement aligned).")
         .defaultValue(false)
         .visible(() -> this.technique.get() == Scaffold.Technique.VapeBridge || this.technique.get() == Scaffold.Technique.Breezily)
         .build()
   );

   private final Setting<Boolean> grimUseScaffolding = this.sgGrim.add(
      new BoolSetting.Builder()
         .name("grim-use-scaffolding")
         .description("Use scaffolding blocks — Grim EXEMPTS them from RotationPlace!")
         .defaultValue(true)
         .visible(() -> this.technique.get() == Scaffold.Technique.GrimScaffold)
         .build()
   );

   private final Setting<Boolean> grimSmoothRotation = this.sgGrim.add(
      new BoolSetting.Builder()
         .name("grim-smooth-rotation")
         .description("Smooth rotation with jitter to avoid DuplicateRotPlace.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Double> grimRotationJitter = this.sgGrim.add(
      new DoubleSetting.Builder()
         .name("grim-rotation-jitter")
         .description("Rotation jitter to avoid DuplicateRotPlace (xDiff < 0.0001 flags).")
         .defaultValue(0.5)
         .range(0.0, 5.0)
         .sliderMax(3.0)
         .visible(this.grimSmoothRotation::get)
         .build()
   );

   private final Setting<Double> rotationSpeed = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("rotation-speed")
         .description("Rotation speed (degrees/tick).")
         .defaultValue(120.0)
         .range(10.0, 360.0)
         .sliderMax(360.0)
         .build()
   );

   private final Setting<Integer> vulcanExpand = this.sgVulcan.add(
      new IntSetting.Builder()
         .name("vulcan-expand")
         .description("Expand for Vulcan — high value bypasses because isBridging becomes false!")
         .defaultValue(2)
         .range(0, 5)
         .sliderMax(5)
         .visible(() -> this.technique.get() == Scaffold.Technique.VulcanExpand)
         .build()
   );

   private final Setting<Boolean> vulcanSprintDisabler = this.sgVulcan.add(
      new BoolSetting.Builder()
         .name("vulcan-sprint-disabler")
         .description("Send sprint/unsneak packets to bypass Vulcan scaffold checks (LB style).")
         .defaultValue(true)
         .visible(() -> this.technique.get() == Scaffold.Technique.VulcanExpand || this.technique.get() == Scaffold.Technique.VulcanSprint)
         .build()
   );

   private final Setting<Integer> vulcanSprintInterval = this.sgVulcan.add(
      new IntSetting.Builder()
         .name("vulcan-sprint-interval")
         .description("Ticks between sprint packet sends.")
         .defaultValue(3)
         .range(1, 10)
         .visible(() -> (this.technique.get() == Scaffold.Technique.VulcanExpand || this.technique.get() == Scaffold.Technique.VulcanSprint)
                     && this.vulcanSprintDisabler.get())
         .build()
   );

   private final Setting<Integer> expand = this.sgExpand.add(
      new IntSetting.Builder()
         .name("expand")
         .description("Blocks to expand forward. NOTE: Expand > 0 flags FarPlace on Grim!")
         .defaultValue(0)
         .range(0, 5)
         .sliderMax(5)
         .build()
   );

   private final Setting<Boolean> tower = this.sgTower.add(
      new BoolSetting.Builder()
         .name("tower")
         .description("Tower up when looking up.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Integer> towerDelay = this.sgTower.add(
      new IntSetting.Builder()
         .name("tower-delay")
         .description("Delay between tower jumps.")
         .defaultValue(3)
         .range(1, 10)
         .visible(this.tower::get)
         .build()
   );

   private int prevSlot = -1;
   private int towerTicks = 0;
   private int scaffoldCount = 0;
   private int placeCooldown = 0;
   private BlockPos lastAttemptPos = null;
   private int repeatedAttempts = 0;
   private boolean managedSneak = false;
   private boolean previousSneakDown = false;
   private final Random random = new Random();

   public Scaffold() {
      super(VenomousAddon.CATEGORY, "scaffold", "Scaffold & Bridge bypasses for Grim, Vulcan, NCP, and fast bridging.");
   }

   public void onActivate() {
      this.prevSlot = -1;
      this.towerTicks = 0;
      this.scaffoldCount = 0;
      this.placeCooldown = 0;
      this.lastAttemptPos = null;
      this.repeatedAttempts = 0;
      this.managedSneak = false;
      this.previousSneakDown = false;
   }

   public void onDeactivate() {
      if (this.prevSlot != -1 && this.mc.player != null) {
         this.mc.player.getInventory().setSelectedSlot(this.prevSlot);
      }

      this.releaseManagedSneak();
      this.placeCooldown = 0;
      this.lastAttemptPos = null;
      this.repeatedAttempts = 0;
   }

   private boolean isBridgeMode() {
      return this.technique.get() == Scaffold.Technique.AutoSneak
         || this.technique.get() == Scaffold.Technique.BridgeMacro
         || this.technique.get() == Scaffold.Technique.VapeBridge
         || this.technique.get() == Scaffold.Technique.GodBridge
         || this.technique.get() == Scaffold.Technique.Breezily;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.level == null) {
         return;
      }

      this.scaffoldCount++;
      if (this.placeCooldown > 0) {
         this.placeCooldown--;
      }

      if ((this.technique.get() == Scaffold.Technique.VulcanExpand || this.technique.get() == Scaffold.Technique.VulcanSprint)
         && this.vulcanSprintDisabler.get()
         && this.scaffoldCount % this.vulcanSprintInterval.get() == 0
         && this.mc.getConnection() != null) {
         this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_SPRINTING));
         this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.STOP_SPRINTING));
      }

      if (!isBridgeMode()) {
         int blockSlot = this.findBlockSlot();
         if (blockSlot != -1) {
            if (this.autoBlockSwitch.get()) {
               if (this.prevSlot == -1) {
                  this.prevSlot = this.mc.player.getInventory().getSelectedSlot();
               }

               if (this.technique.get() == Scaffold.Technique.GrimScaffold && this.grimUseScaffolding.get()) {
                  int scaffoldSlot = this.findScaffoldSlot();
                  if (scaffoldSlot != -1) {
                     blockSlot = scaffoldSlot;
                  }
               }

               this.mc.player.getInventory().setSelectedSlot(blockSlot);
            }

            if (this.tower.get() && this.mc.player.getXRot() < -75.0F) {
               this.doTower();
            } else {
               BlockPos placePos = this.getPlacePos();
               if (placePos != null) {
                  if (this.autoSneak.get()) {
                     if (this.isOverEdge()) {
                        this.pressManagedSneak();
                     } else {
                        this.releaseManagedSneak();
                     }
                  }

                  if (this.technique.get() == Scaffold.Technique.VulcanExpand && this.vulcanExpand.get() > 0) {
                     for (int i = 0; i <= this.vulcanExpand.get(); i++) {
                        BlockPos ep = this.getExpandPos(i);
                        if (ep != null && this.place(ep)) {
                           break;
                        }
                     }
                  } else if (this.technique.get() == Scaffold.Technique.Expand && this.expand.get() > 0) {
                     for (int i = 0; i <= this.expand.get(); i++) {
                        BlockPos ep = this.getExpandPos(i);
                        if (ep != null && this.place(ep)) {
                           break;
                        }
                     }
                  } else {
                     this.place(placePos);
                  }
               } else if (this.autoSneak.get()) {
                  this.releaseManagedSneak();
               }
            }
         }
      } else {
         this.tickBridgeMode();
      }
   }

   private void tickBridgeMode() {
      Vec3 dir = this.movementDirection();
      if (dir.lengthSqr() < 1.0E-4) {
         this.releaseManagedSneak();
         return;
      }

      int blockSlot = this.findBlockSlot();
      if (blockSlot != -1 && this.autoBlockSwitch.get()) {
         if (this.prevSlot == -1) {
            this.prevSlot = this.mc.player.getInventory().getSelectedSlot();
         }
         this.mc.player.getInventory().setSelectedSlot(blockSlot);
      }

      boolean edge = this.edgeDistance(dir) <= this.bridgeEdgeThreshold.get();
      Scaffold.Technique tech = this.technique.get();

      // GodBridge / Breezily: fast timing, no sneak on ground if aligned
      boolean isFastBridge = (tech == Scaffold.Technique.GodBridge || tech == Scaffold.Technique.Breezily);

      if (!edge) {
         this.releaseManagedSneak();
      } else {
         if (!isFastBridge || !this.mc.player.onGround()) {
            this.pressManagedSneak();
         }

         boolean shouldPlace = this.bridgePlaceAssist.get();
         if (shouldPlace) {
            BlockPos target = BlockPos.containing(this.mc.player.getX() + dir.x * 0.65, this.blockY(), this.mc.player.getZ() + dir.z * 0.65);
            float oldYaw = this.mc.player.getYRot();
            float oldPitch = this.mc.player.getXRot();

            if (this.bridgePitchAssist.get() && (tech == Scaffold.Technique.VapeBridge || tech == Scaffold.Technique.GodBridge)) {
               if (this.mc.player.getXRot() < 65.0F && !this.serverSideRotations.get()) {
                  this.mc.player.setXRot(75.5F);
               }
            }

            boolean placed = this.place(target);
            if (this.bridgeCameraStabilisation.get() && (tech == Scaffold.Technique.BridgeMacro || tech == Scaffold.Technique.VapeBridge)) {
               this.mc.player.setYRot(oldYaw);
               this.mc.player.setXRot(oldPitch);
               this.mc.player.yRotO = oldYaw;
               this.mc.player.xRotO = oldPitch;
            }

            if (placed && (tech == Scaffold.Technique.BridgeMacro || tech == Scaffold.Technique.VapeBridge)) {
               this.releaseManagedSneak();
            }
         }
      }
   }

   private Vec3 movementDirection() {
      double forward = (this.mc.options.keyUp.isDown() ? 1 : 0) - (this.mc.options.keyDown.isDown() ? 1 : 0);
      double strafe = (this.mc.options.keyLeft.isDown() ? 1 : 0) - (this.mc.options.keyRight.isDown() ? 1 : 0);
      if (forward == 0.0 && strafe == 0.0) {
         return Vec3.ZERO;
      }

      double yaw = Math.toRadians(this.mc.player.getYRot());
      double x = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
      double z = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
      return new Vec3(x, 0.0, z).normalize();
   }

   private double edgeDistance(Vec3 dir) {
      double fx = this.mc.player.getX() - Math.floor(this.mc.player.getX());
      double fz = this.mc.player.getZ() - Math.floor(this.mc.player.getZ());
      double dx = dir.x > 0.01 ? 1.0 - fx : (dir.x < -0.01 ? fx : 1.0);
      double dz = dir.z > 0.01 ? 1.0 - fz : (dir.z < -0.01 ? fz : 1.0);
      return Math.min(dx, dz);
   }

   private int blockY() {
      return (int)Math.floor(this.mc.player.getY() - 1.0);
   }

   private BlockPos getPlacePos() {
      BlockPos below = BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 1.0, this.mc.player.getZ());
      if (this.mc.level.getBlockState(below).isAir()) {
         return below;
      }

      for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
         BlockPos check = below.relative(dir);
         if (this.mc.level.getBlockState(check).isAir()) {
            double px = this.mc.player.getX() - Math.floor(this.mc.player.getX());
            double pz = this.mc.player.getZ() - Math.floor(this.mc.player.getZ());

            boolean near = switch (dir) {
               case NORTH -> pz < 0.3;
               case SOUTH -> pz > 0.7;
               case EAST -> px > 0.7;
               case WEST -> px < 0.3;
               default -> false;
            };
            if (near) {
               return check;
            }
         }
      }

      return null;
   }

   private BlockPos getExpandPos(int fwd) {
      double yaw = Math.toRadians(this.mc.player.getYRot());
      double d = fwd + 1;
      BlockPos pos = BlockPos.containing(this.mc.player.getX() - Math.sin(yaw) * d, this.mc.player.getY() - 1.0, this.mc.player.getZ() + Math.cos(yaw) * d);
      return this.mc.level.getBlockState(pos).isAir() ? pos : null;
   }

   private void doTower() {
      BlockPos below = BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 1.0, this.mc.player.getZ());
      if (this.mc.level.getBlockState(below).isAir()) {
         this.place(below);
      }

      this.towerTicks++;
      if (this.mc.player.onGround() && this.towerTicks % this.towerDelay.get() == 0) {
         this.mc.player.jumpFromGround();
      }
   }

   private boolean place(BlockPos pos) {
      if (this.mc.gameMode != null && this.placeCooldown <= 0) {
         if (pos.equals(this.lastAttemptPos)) {
            this.repeatedAttempts++;
         } else {
            this.lastAttemptPos = pos;
            this.repeatedAttempts = 0;
         }

         int delay = Math.max(1, this.placeDelay.get());
         if (this.repeatedAttempts > 0 && this.repeatedAttempts % delay != 0) {
            return false;
         }

         for (Direction face : Direction.values()) {
            BlockPos neighbor = pos.relative(face);
            if (!this.mc.level.getBlockState(neighbor).isAir() && this.mc.level.getWorldBorder().isWithinBounds(neighbor)) {
               Direction placeFace = face.getOpposite();
               Vec3 hitVec = Vec3.atCenterOf(neighbor).add(placeFace.getStepX() * 0.5, placeFace.getStepY() * 0.5, placeFace.getStepZ() * 0.5);
               if (!(this.mc.player.getEyePosition().distanceTo(hitVec) > 5.0)) {
                  BlockHitResult hitResult = new BlockHitResult(hitVec, placeFace, neighbor, false);
                  Runnable placeAction = () -> {
                     InteractionResult result = this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hitResult);
                     if (result.consumesAction() && this.swing.get()) {
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                     }
                  };
                  if (this.serverSideRotations.get()) {
                     float yaw = this.wrapDegrees((float)Rotations.getYaw(hitVec));
                     float pitch = this.clampPitch((float)Rotations.getPitch(hitVec));
                     Rotations.rotate(yaw, pitch, 20, placeAction);
                  } else {
                     this.rotateTo(hitVec);
                     placeAction.run();
                  }

                  this.placeCooldown = delay;
                  return true;
               }
            }
         }

         this.placeCooldown = Math.max(1, delay / 2);
         return false;
      } else {
         return false;
      }
   }

   private void rotateTo(BlockPos pos) {
      double dx = pos.getX() + 0.5 - this.mc.player.getX();
      double dy = pos.getY() + 0.5 - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = pos.getZ() + 0.5 - this.mc.player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      if (this.serverSideRotations.get()) {
         Rotations.rotate(targetYaw, targetPitch, 10, null);
      } else {
         float yawDiff = this.wrapDegrees(targetYaw - this.mc.player.getYRot());
         float pitchDiff = this.wrapDegrees(targetPitch - this.mc.player.getXRot());
         float maxTurn = this.rotationSpeed.get().floatValue();
         if (Math.abs(yawDiff) > maxTurn) {
            yawDiff = Math.signum(yawDiff) * maxTurn;
         }

         if (Math.abs(pitchDiff) > maxTurn) {
            pitchDiff = Math.signum(pitchDiff) * maxTurn;
         }

         this.mc.player.setYRot(this.mc.player.getYRot() + yawDiff);
         this.mc.player.setXRot(this.mc.player.getXRot() + pitchDiff);
      }
   }

   private void rotateTo(Vec3 hitVec) {
      double dx = hitVec.x - this.mc.player.getX();
      double dy = hitVec.y - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = hitVec.z - this.mc.player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = this.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx, dz)));
      float targetPitch = this.clampPitch((float)(-Math.toDegrees(Math.atan2(dy, dist))));
      float yawDiff = this.wrapDegrees(targetYaw - this.mc.player.getYRot());
      float pitchDiff = this.wrapDegrees(targetPitch - this.mc.player.getXRot());
      float maxTurn = this.rotationSpeed.get().floatValue();
      if (Math.abs(yawDiff) > maxTurn) {
         yawDiff = Math.signum(yawDiff) * maxTurn;
      }

      if (Math.abs(pitchDiff) > maxTurn) {
         pitchDiff = Math.signum(pitchDiff) * maxTurn;
      }

      this.mc.player.setYRot(this.mc.player.getYRot() + yawDiff);
      this.mc.player.setXRot(this.mc.player.getXRot() + pitchDiff);
   }

   private float clampPitch(float pitch) {
      return Math.max(-90.0F, Math.min(90.0F, pitch));
   }

   private float wrapDegrees(float v) {
      while (v > 180.0F) {
         v -= 360.0F;
      }

      while (v < -180.0F) {
         v += 360.0F;
      }

      return v;
   }

   private void pressManagedSneak() {
      if (!this.managedSneak) {
         this.previousSneakDown = this.mc.options.keyShift.isDown();
      }

      this.managedSneak = true;
      this.mc.options.keyShift.setDown(true);
   }

   private void releaseManagedSneak() {
      if (this.managedSneak) {
         this.mc.options.keyShift.setDown(this.previousSneakDown);
      }

      this.managedSneak = false;
      this.previousSneakDown = false;
   }

   private boolean isOverEdge() {
      return this.mc.level.getBlockState(BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 1.0, this.mc.player.getZ())).isAir();
   }

   private int findBlockSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
            return i;
         }
      }

      return -1;
   }

   private int findScaffoldSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (stack.is(Items.SCAFFOLDING)) {
            return i;
         }
      }

      return -1;
   }

   public String getInfoString() {
      return this.technique.get().name();
   }

   public enum Technique {
      GrimScaffold,
      AutoSneak,
      BridgeMacro,
      VapeBridge,
      VulcanExpand,
      VulcanSprint,
      NCPNormal,
      Normal,
      Expand,
      GodBridge,
      Breezily;
   }
}
