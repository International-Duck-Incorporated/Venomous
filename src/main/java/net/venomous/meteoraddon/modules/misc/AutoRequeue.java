package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Runs the command behind a game's "play again" chat button for you, so the queue can be clicked
 * without it. Ported from Raven B4's {@code AutoRequeue} with the same shape - a message whose
 * text matches the trigger is scanned for a command click event, the command is held until the
 * delay has passed, and a fresh world join discards whatever was held, because requeueing belongs
 * to the game the message was sent in. Raven matched the button's literal wording ("Click here");
 * the port instead takes the first run-command click event wherever it sits in the component tree,
 * which covers every "play again", "requeue" and reminder button the trigger matches.
 */
public class AutoRequeue extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> triggerText = this.sgGeneral.add(
        new StringSetting.Builder().name("trigger-text")
            .description("Chat messages containing this text are searched for a command to run.")
            .defaultValue("play again")
            .build()
    );
    private final Setting<Double> delay = this.sgGeneral.add(
        new DoubleSetting.Builder().name("delay")
            .description("Seconds between the message and the click.")
            .defaultValue(0.5)
            .range(0.0, 5.0)
            .sliderMax(3.0)
            .build()
    );

    private String queuedCommand;
    private long queuedAt;

    public AutoRequeue() {
        super(VenomousAddon.CATEGORY, "auto-requeue", "Clicks the queue button in chat for you - holds the command behind a play-again message and runs it after the delay.");
    }

    public void onActivate() {
        this.queuedCommand = null;
    }

    @Override
    public void onDeactivate() {
        this.queuedCommand = null;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (this.mc.player == null) {
            return;
        }

        if (!event.getMessage().getString().contains(this.triggerText.get())) {
            return;
        }

        String command = this.findCommand(event.getMessage());
        if (command != null) {
            this.queuedCommand = command;
            this.queuedAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.queuedCommand == null) {
            return;
        }

        if (this.mc.player == null || this.mc.getConnection() == null) {
            this.queuedCommand = null;
            return;
        }

        if (System.currentTimeMillis() - this.queuedAt >= this.delay.get() * 1000.0) {
            String command = this.queuedCommand;
            if (command.startsWith("/")) {
                this.mc.player.connection.sendCommand(command.substring(1));
            } else {
                this.mc.player.connection.sendChat(command);
            }
            this.queuedCommand = null;
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        // the command belonged to the game just left; a clean queue never requeues blind
        this.queuedCommand = null;
    }

    /**
     * The first run-command click event in the component tree - the message's own style first, then
     * every sibling in order, which is how a chat button's command is placed.
     */
    private String findCommand(Component component) {
        if (component.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run
            && !run.command().isBlank()) {
            return run.command();
        }

        for (Component sibling : component.getSiblings()) {
            String command = this.findCommand(sibling);
            if (command != null) {
                return command;
            }
        }

        return null;
    }
}
