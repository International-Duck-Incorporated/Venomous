package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class HoleSnap extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Integer> range = this.sgGeneral.add(
        new IntSetting.Builder()
           .name("range")
           .description("Horizontal search radius for safe holes.")
           .defaultValue(4)
           .min(1)
           .sliderMax(6)
           .build()
    );

    private final Setting<Integer> downRange = this.sgGeneral.add(
        new IntSetting.Builder()
           .name("down-range")
           .description("Vertical downwards search radius for holes.")
           .defaultValue(3)
           .min(0)
           .sliderMax(5)
           .build()
    );

    private final Setting<Double> speed = this.sgGeneral.add(
        new DoubleSetting.Builder()
           .name("speed")
           .description("Speed at which you are pulled towards the hole center.")
           .defaultValue(0.35)
           .min(0.05)
           .sliderMax(1.0)
           .build()
    );

    private final Setting<Boolean> autoDisable = this.sgGeneral.add(
        new BoolSetting.Builder()
           .name("auto-disable")
           .description("Automatically disable the module once safely inside the hole.")
           .defaultValue(true)
           .build()
    );

    private final Setting<Boolean> jump = this.sgGeneral.add(
        new BoolSetting.Builder()
           .name("jump")
           .description("Jump automatically when moving over obstacles to reach the hole.")
           .defaultValue(false)
           .build()
    );

    private final Setting<Boolean> render = this.sgRender.add(
        new BoolSetting.Builder()
           .name("render")
           .description("Render the target hole in the world.")
           .defaultValue(true)
           .build()
    );

    private final Setting<ShapeMode> shapeMode = this.sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
           .name("shape-mode")
           .description("How the target hole is rendered.")
           .defaultValue(ShapeMode.Both)
           .visible(this.render::get)
           .build()
    );

    private final Setting<SettingColor> sideColor = this.sgRender.add(
        new ColorSetting.Builder()
           .name("side-color")
           .description("Color of the sides.")
           .defaultValue(new SettingColor(50, 220, 100, 35))
           .visible(() -> this.render.get() && this.shapeMode.get().sides())
           .build()
    );

    private final Setting<SettingColor> lineColor = this.sgRender.add(
        new ColorSetting.Builder()
           .name("line-color")
           .description("Color of the outline lines.")
           .defaultValue(new SettingColor(50, 220, 100, 220))
           .visible(() -> this.render.get() && this.shapeMode.get().lines())
           .build()
    );

    private BlockPos targetHole;

    public HoleSnap() {
        super(VenomousAddon.CATEGORY, "hole-snap", "Pulls and centers the player smoothly into the nearest safe hole.");
    }

    @Override
    public void onActivate() {
        this.targetHole = null;
    }

    @Override
    public void onDeactivate() {
        this.targetHole = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) return;

        this.targetHole = this.findNearestHole();
        if (this.targetHole == null) {
            if (this.autoDisable.get()) {
                this.toggle();
            }
            return;
        }

        double targetX = this.targetHole.getX() + 0.5;
        double targetZ = this.targetHole.getZ() + 0.5;

        double dx = targetX - this.mc.player.getX();
        double dz = targetZ - this.mc.player.getZ();
        double dist = Math.hypot(dx, dz);

        if (dist <= 0.12 && this.mc.player.getY() <= this.targetHole.getY() + 0.5) {
            Vec3 vel = this.mc.player.getDeltaMovement();
            this.mc.player.setDeltaMovement(0, vel.y, 0);
            if (this.autoDisable.get()) {
                this.toggle();
            }
            return;
        }

        double currentSpeed = Math.min(dist, this.speed.get());
        double motionX = (dx / dist) * currentSpeed;
        double motionZ = (dz / dist) * currentSpeed;

        Vec3 vel = this.mc.player.getDeltaMovement();
        this.mc.player.setDeltaMovement(motionX, vel.y, motionZ);

        if (this.jump.get() && this.mc.player.onGround() && dist > 0.4 && this.targetHole.getY() >= this.mc.player.getY()) {
            this.mc.player.jumpFromGround();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && this.targetHole != null) {
            event.renderer.box(this.targetHole, (Color) this.sideColor.get(), (Color) this.lineColor.get(), this.shapeMode.get(), 0);
        }
    }

    private BlockPos findNearestHole() {
        BlockPos playerPos = this.mc.player.blockPosition();
        int r = this.range.get();
        int d = this.downRange.get();

        BlockPos bestHole = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -d; y <= 1; y++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (this.isSafeHole(pos)) {
                        double distSq = pos.distToCenterSqr(this.mc.player.position());
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestHole = pos;
                        }
                    }
                }
            }
        }
        return bestHole;
    }

    private boolean isSafeHole(BlockPos pos) {
        if (!this.mc.level.getBlockState(pos).isAir()) return false;
        if (!this.mc.level.getBlockState(pos.above()).isAir()) return false;

        if (!this.isBlastResistant(this.mc.level.getBlockState(pos.below()))) return false;

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : directions) {
            if (!this.isBlastResistant(this.mc.level.getBlockState(pos.relative(dir)))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBlastResistant(BlockState state) {
        return state.is(Blocks.BEDROCK)
            || state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN)
            || state.is(Blocks.RESPAWN_ANCHOR)
            || state.is(Blocks.NETHERITE_BLOCK)
            || state.is(Blocks.ENDER_CHEST)
            || state.is(Blocks.ANVIL);
    }
}
