package net.venomous.meteoraddon.modules.combat;

import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.PreRotate;

public class BedAura extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTargeting = this.settings.createGroup("Targeting");
    private final SettingGroup sgRotation = this.settings.createGroup("Rotation");
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> radius = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("radius")
            .description("Search radius for beds.")
            .defaultValue(5.0)
            .min(0.0)
            .max(15.0)
            .sliderMax(15.0)
            .build());

    private final Setting<Boolean> triggerOnClick = this.sgTargeting
        .add(new BoolSetting.Builder()
            .name("trigger-on-click")
            .description("Only break beds while the attack key is held down.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> requirePickaxe = this.sgTargeting
        .add(new BoolSetting.Builder()
            .name("require-pickaxe")
            .description("Only break beds while holding a pickaxe.")
            .defaultValue(true)
            .build());

    private final Setting<SettingColor> color = this.sgRender
        .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("color")
            .description("Render color for targeted bed.")
            .defaultValue(new SettingColor(0, 255, 255, 255))
            .build());

    private final Setting<Boolean> renderBox = this.sgRender
        .add(new BoolSetting.Builder()
            .name("render-box")
            .description("Render a box around the targeted bed.")
            .defaultValue(true)
            .build());

    private BlockPos currentBlockToBreak = null;
    private int breakStage = 0;

    public BedAura() {
        super(VenomousAddon.CATEGORY, "bed-aura", "Automatically breaks nearby beds.");
    }

    @Override
    public void onActivate() {
        this.currentBlockToBreak = null;
        this.breakStage = 0;
    }

    @Override
    public void onDeactivate() {
        this.currentBlockToBreak = null;
        this.breakStage = 0;
        PreRotate.cancelAll();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            this.currentBlockToBreak = null;
            this.breakStage = 0;
            return;
        }

        // Check if we need a new target
        if (this.currentBlockToBreak == null || !this.isValidTarget(this.currentBlockToBreak)) {
            this.currentBlockToBreak = this.findNextBed();
            this.breakStage = 0;
        }

        if (this.currentBlockToBreak != null) {
            // Check trigger on click
            if (this.triggerOnClick.get() && !this.mc.options.keyAttack.isDown()) {
                return;
            }

            // Check require pickaxe
            if (this.requirePickaxe.get() && !this.isHoldingPickaxe()) {
                return;
            }

            // Check range
            double range = this.radius.get() + 1.0;
            double rangeSqr = range * range;
            Vec3 playerPos = Vec3.atCenterOf(this.mc.player.blockPosition());
            Vec3 targetPos = Vec3.atCenterOf(this.currentBlockToBreak);

            if (playerPos.distanceToSqr(targetPos) > rangeSqr) {
                this.currentBlockToBreak = null;
                this.breakStage = 0;
                PreRotate.cancelAll();
                return;
            }

            // Calculate rotation
            double yaw = Rotations.getYaw(targetPos);
            double pitch = Rotations.getPitch(targetPos);

            // Use PreRotate to rotate then break
            PreRotate.rotateThen(this, yaw, pitch, () -> {
                if (this.currentBlockToBreak != null && this.isValidTarget(this.currentBlockToBreak)) {
                    this.breakBed(this.currentBlockToBreak);
                }
            });
        } else {
            PreRotate.cancelAll();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onRender(Render3DEvent event) {
        if (this.renderBox.get() && this.currentBlockToBreak != null && this.mc.player != null) {
            Vec3 playerPos = this.mc.player.getPosition(1.0F);
            AABB box = new AABB(this.currentBlockToBreak).move(-playerPos.x, -playerPos.y, -playerPos.z);
            event.renderer.box(box, this.color.get(), this.color.get(), meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
        }
    }

    private BlockPos findNextBed() {
        if (this.mc.player == null || this.mc.level == null) {
            return null;
        }

        BlockPos playerPos = this.mc.player.blockPosition();
        int rad = this.radius.get().intValue();

        // Scan from top to bottom, closer first
        for (int y = rad; y >= -rad; y--) {
            for (int x = -rad; x <= rad; x++) {
                for (int z = -rad; z <= rad; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (this.isBed(pos)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private boolean isValidTarget(BlockPos pos) {
        if (this.mc.level == null) {
            return false;
        }

        BlockState state = this.mc.level.getBlockState(pos);
        if (!this.isBed(state.getBlock())) {
            return false;
        }

        // Check if bed is fully broken (air)
        if (state.isAir()) {
            return false;
        }

        return true;
    }

    private boolean isBed(Block block) {
        return block instanceof BedBlock;
    }

    private boolean isBed(BlockPos pos) {
        if (this.mc.level == null) {
            return false;
        }
        return this.isBed(this.mc.level.getBlockState(pos).getBlock());
    }

    private boolean isHoldingPickaxe() {
        if (this.mc.player == null) {
            return false;
        }
        Item item = this.mc.player.getMainHandItem().getItem();
        return item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE
            || item == Items.IRON_PICKAXE || item == Items.STONE_PICKAXE
            || item == Items.GOLDEN_PICKAXE || item == Items.WOODEN_PICKAXE;
    }

    private void breakBed(BlockPos pos) {
        if (this.mc.player == null || this.mc.player.connection == null) {
            return;
        }

        // Send start destroy block packet
        this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, Direction.NORTH));
        // Send stop destroy block packet
        this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.NORTH));
        // Swing hand
        this.mc.player.swing(InteractionHand.MAIN_HAND);
        this.breakStage++;
        if (this.breakStage >= 2) {
            // Bed should be broken after 2 stages
            this.currentBlockToBreak = null;
            this.breakStage = 0;
        }
    }

    public enum Mode {
        Off,
        Silent,
        Normal;
    }
}