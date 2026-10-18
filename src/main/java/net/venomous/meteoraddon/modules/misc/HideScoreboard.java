package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Hides the scoreboard sidebar.
 *
 * <p>Previously carried a "hide-numbers" setting and a {@code shouldHideNumbers()} that nothing
 * called. {@code HudMixin} cancels {@code extractScoreboardSidebar} outright whenever the module
 * is on, so the sidebar is hidden in full regardless, and the setting only described behaviour
 * that did not exist. Removed rather than wired: the mixin does the whole job, and a control
 * that cannot change anything is worse than no control.</p>
 */
public class HideScoreboard extends Module {
    public HideScoreboard() {
        super(VenomousAddon.CATEGORY, "hide-scoreboard", "Hides the scoreboard sidebar from the HUD.");
    }
}
