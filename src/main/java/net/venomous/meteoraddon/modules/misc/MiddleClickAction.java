package net.venomous.meteoraddon.modules.misc;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
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
import net.venomous.meteoraddon.utils.ClickTiming;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class MiddleClickAction extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> addFriend = this.sgGeneral
        .add(new BoolSetting.Builder().name("add-friend").description("Add player as friend on middle click.").defaultValue(true).build());
    private final Setting<Boolean> removeFriend = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("remove-friend").description("Remove player as friend on middle click if already friended.")
                .defaultValue(true)
                .build()
        );
    private final Setting<ClickTiming.Technique> technique = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ClickTiming.Technique>()
                .name("technique")
                .description("Click pattern used to pace the action after the press.")
                .defaultValue(ClickTiming.Technique.Stabilized)
                .build()
        );
    private final Setting<Integer> minCps = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-cps")
                .description("Minimum clicks per second.")
                .defaultValue(6)
                .range(1, 20)
                .sliderMax(15)
                .build()
        );
    private final Setting<Integer> maxCps = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-cps")
                .description("Maximum clicks per second.")
                .defaultValue(8)
                .range(1, 20)
                .sliderMax(15)
                .build()
        );
    private int ticksUntilClick = 0;
    private boolean wasMiddleDown = false;
    private boolean clickParity = false;
    private int pendingTargetId = -1;
    private final Random random = new Random();

    public MiddleClickAction() {
        super(VenomousAddon.CATEGORY, "middle-click-action", "Perform actions on middle click with humanlike timing.");
    }

    public void onActivate() {
        this.ticksUntilClick = 0;
        this.wasMiddleDown = false;
        this.pendingTargetId = -1;
    }

    public void onDeactivate() {
        // Clearing wasMiddleDown is what stops a held middle-click from being seen as a fresh
        // press the moment the module is switched back on. Dropping the pending target stops a
        // scheduled action firing at whoever happens to be standing there by then.
        this.ticksUntilClick = 0;
        this.wasMiddleDown = false;
        this.pendingTargetId = -1;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) return;

        boolean middleDown = this.mc.options.keyPickItem.isDown();

        if (middleDown && !this.wasMiddleDown) {
            // The target is resolved on the press, not when the action fires. Waiting a few
            // ticks and then re-reading the crosshair would act on whoever the player drifted
            // to look at instead, which is not what a click on that entity meant.
            this.pendingTargetId = this.targetUnderCrosshair();
            if (this.pendingTargetId != -1) {
                this.ticksUntilClick = ClickTiming.reactionDelayTicks(
                    this.technique.get(), this.minCps.get(), this.maxCps.get(), this.random, this.clickParity);
                this.clickParity = !this.clickParity;
            }
        }

        if (this.ticksUntilClick > 0 && --this.ticksUntilClick == 0 && this.pendingTargetId != -1) {
            this.handleClick(this.pendingTargetId);
            this.pendingTargetId = -1;
        }

        this.wasMiddleDown = middleDown;
    }

    /** Entity id under the crosshair, or -1 when the player is not aiming at an entity. */
    private int targetUnderCrosshair() {
        if (this.mc.hitResult == null || this.mc.hitResult.getType() != Type.ENTITY) {
            return -1;
        }

        return ((EntityHitResult)this.mc.hitResult).getEntity().getId();
    }

    private void handleClick(int entityId) {
        // Re-resolved from the level rather than holding the entity, so a player who logged
        // out during the delay is skipped instead of being actioned as a stale object.
        if (!(this.mc.level.getEntity(entityId) instanceof Player player)) {
            return;
        }

        if ((Boolean)this.addFriend.get() && !Friends.get().isFriend(player)) {
            Friends.get().add(new Friend(player));
            this.info("Added " + player.getName().getString() + " as friend.", new Object[0]);
        } else if ((Boolean)this.removeFriend.get() && Friends.get().isFriend(player)) {
            Friends.get().remove(new Friend(player));
            this.info("Removed " + player.getName().getString() + " from friends.", new Object[0]);
        }
    }
}
