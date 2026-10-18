package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
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

        if ((Boolean)this.armorColor.get() && this.sameLeatherDye(player)) {
            return true;
        }

        return false;
    }

    /**
     * True when both players wear dyed leather of the same colour in the chest slot.
     *
     * <p>Undyed or non-leather chestplates return false rather than matching on "both undyed",
     * since a server that colours leather as a team marker always dyes it.</p>
     */
    private boolean sameLeatherDye(Player player) {
        ItemStack own = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack theirs = player.getItemBySlot(EquipmentSlot.CHEST);
        if (own == null || theirs == null) return false;

        // Leather dye lives on the DYED_COLOR data component rather than an Item subclass in
        // this Minecraft version, so it is compared as a component value.
        Object ownColor = own.get(DataComponents.DYED_COLOR);
        Object theirColor = theirs.get(DataComponents.DYED_COLOR);

        // Both undyed must not count as a match: a server using leather colour as a team
        // marker always dyes it, so undyed means "no marker".
        return ownColor != null && ownColor.equals(theirColor);
    }
}
