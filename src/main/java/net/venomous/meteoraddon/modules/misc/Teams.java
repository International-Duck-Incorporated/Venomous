package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class Teams extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> scoreboardTeam = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("scoreboard-team").description("Check if player is on your scoreboard team.").defaultValue(true)
                .build()
        );
    private final Setting<Boolean> nameColor = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("name-color").description("Check if player has the same name color as you.").defaultValue(true)
                .build()
        );
    private final Setting<Boolean> prefix = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("prefix").description("Check if player has the same name prefix as you (e.g. [Team]).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> armorColor = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("armor-color").description("Check if player has the same leather armor color as you.")
                .defaultValue(false)
                .build()
        );

    public Teams() {
        super(VenomousAddon.CATEGORY, "teams", "Prevents attacking teammates by checking teams, name color, or prefix.");
    }

    public boolean isTeammate(Player player) {
        if (!this.isActive() || this.mc.player == null) {
            return false;
        }

        if ((Boolean)this.scoreboardTeam.get() && this.mc.player.isAlliedTo(player)) {
            return true;
        }

        if ((Boolean)this.nameColor.get()) {
            TextColor ownColor = this.mc.player.getDisplayName() != null ? this.mc.player.getDisplayName().getStyle().getColor() : null;
            TextColor otherColor = player.getDisplayName() != null ? player.getDisplayName().getStyle().getColor() : null;
            if (ownColor != null && otherColor != null && ownColor.equals(otherColor)) {
                return true;
            }
        }

        if ((Boolean)this.prefix.get()) {
            String ownName = this.mc.player.getDisplayName() != null ? this.mc.player.getDisplayName().getString() : "";
            String otherName = player.getDisplayName() != null ? player.getDisplayName().getString() : "";
            String[] ownSplit = ownName.split(" ");
            String[] otherSplit = otherName.split(" ");
            if (ownSplit.length > 1 && otherSplit.length > 1 && ownSplit[0].equals(otherSplit[0])) {
                return true;
            }
        }

        return false;
    }
}
