package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.PreRotate;

public class VAutoCity extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> targetRange = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("target-range")
            .description("Maximum distance to find an enemy target.")
            .defaultValue(6.0)
            .min(1.0)
            .sliderMax(10.0)
            .build()
    );

    private final Setting<Double> mineRange = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("mine-range")
            .description("Maximum reach distance to mine the surround block.")
            .defaultValue(4.5)
            .min(1.0)
            .sliderMax(6.0)
            .build()
    );

    private final Setting<Boolean> autoSwitch = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-switch")
            .description("Automatically swap to the best pickaxe in your hotbar.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> support = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("support")
            .description("Automatically place a block beneath floating city blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> rotate = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate towards the target block when breaking.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> swing = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("swing")
            .description("Swing your hand visually when mining.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> ignoreFriends = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Never target surrounding blocks of friends.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> render = this.sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Render the targeted city block in the world.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = this.sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the target block outline is rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(this.render::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("Color of the sides.")
            .defaultValue(new SettingColor(255, 60, 60, 35))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
    );

    private final Setting<SettingColor> lineColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("Color of the outline lines.")
            .defaultValue(new SettingColor(255, 60, 60, 220))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
    );

    private BlockPos targetBlock;
    private Player targetPlayer;

    public VAutoCity() {
        super(VenomousAddon.CATEGORY, "v-auto-city", "Automatically destroys enemy obsidian and surround blocks.");
    }

    @Override
    public void onActivate() {
        this.targetBlock = null;
        this.targetPlayer = null;
    }

    @Override
    public void onDeactivate() {
        if (this.targetBlock != null && this.mc.player != null && this.mc.player.connection != null) {
            this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, this.targetBlock, Direction.UP));
        }
        this.targetBlock = null;
        this.targetPlayer = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.level == null || this.mc.player == null) return;

        this.targetPlayer = this.findTarget();
        if (this.targetPlayer == null) {
            this.targetBlock = null;
            return;
        }

        this.targetBlock = this.findCityBlock(this.targetPlayer);
        if (this.targetBlock == null) return;

        if (this.support.get()) {
            BlockPos below = this.targetBlock.below();
            if (this.mc.level.getBlockState(below).isAir() && BlockUtils.canPlace(below)) {
                FindItemResult obi = InvUtils.findInHotbar(Items.OBSIDIAN);
                if (obi.found()) {
                    BlockUtils.place(below, obi, this.rotate.get(), 10, this.swing.get(), true);
                }
            }
        }

        FindItemResult pickaxe = InvUtils.findInHotbar(Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE);
        if (this.autoSwitch.get() && pickaxe.found()) {
            InvUtils.swap(pickaxe.slot(), false);
        }

        if (this.rotate.get()) {
            PreRotate.rotateThen(Rotations.getYaw(this.targetBlock), Rotations.getPitch(this.targetBlock), () -> this.mine(this.targetBlock));
        } else {
            this.mine(this.targetBlock);
        }
    }

    private void mine(BlockPos pos) {
        if (this.mc.player == null || this.mc.player.connection == null) return;

        this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP));
        if (this.swing.get()) {
            this.mc.player.swing(InteractionHand.MAIN_HAND);
        }
        this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && this.targetBlock != null) {
            event.renderer.box(this.targetBlock, (Color) this.sideColor.get(), (Color) this.lineColor.get(), this.shapeMode.get(), 0);
        }
    }

    private Player findTarget() {
        Player closest = null;
        double maxDistSq = this.targetRange.get() * this.targetRange.get();

        for (Player p : this.mc.level.players()) {
            if (p == this.mc.player || p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            if (this.ignoreFriends.get() && !Friends.get().shouldAttack(p)) continue;

            double distSq = p.distanceToSqr(this.mc.player);
            if (distSq <= maxDistSq) {
                maxDistSq = distSq;
                closest = p;
            }
        }
        return closest;
    }

    private BlockPos findCityBlock(Player player) {
        BlockPos feet = player.blockPosition();
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction dir : directions) {
            BlockPos pos = feet.relative(dir);
            BlockState state = this.mc.level.getBlockState(pos);

            if (this.isMineableSurroundBlock(state) && PlayerUtils.isWithin(Vec3.atCenterOf(pos), this.mineRange.get())) {
                double distSq = pos.distToCenterSqr(this.mc.player.getEyePosition());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPos = pos;
                }
            }
        }
        return bestPos;
    }

    public static boolean isMineableSurroundBlock(BlockState state) {
        return state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN)
            || state.is(Blocks.ENDER_CHEST)
            || state.is(Blocks.RESPAWN_ANCHOR)
            || state.is(Blocks.ANVIL)
            || state.is(Blocks.CHIPPED_ANVIL)
            || state.is(Blocks.DAMAGED_ANVIL)
            || state.is(Blocks.NETHERITE_BLOCK);
    }
}
