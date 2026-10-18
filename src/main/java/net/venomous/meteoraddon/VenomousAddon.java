package net.venomous.meteoraddon;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.venomous.meteoraddon.modules.ModuleRegistry;
import net.venomous.meteoraddon.utils.PreRotate;
import org.slf4j.Logger;

public class VenomousAddon extends MeteorAddon {
    /**
     * Reported at runtime and shown in the startup banner.
     *
     * <p>This used to be hardcoded independently of the build, and drifted: the jar shipped as
     * 7.0.1 while this constant still said 7.0.0, so the banner advertised the wrong version.
     * {@code AddonMetadataTest} now asserts it matches {@code libs.versions.toml}, which is the
     * single source of truth the build itself uses.</p>
     */
    public static final String VERSION = "7.1.0";
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = new Category("Venomous");
    public static final Category CATEGORY_OP = new Category("Venomous OP");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Venomous V" + VERSION + " - Optimized & Refined.");
        PreRotate.init();
        ModuleRegistry.register();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(CATEGORY_OP);
    }

    @Override
    public String getPackage() {
        return "net.venomous.meteoraddon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo(
            "International-Duck-Incorporated",
            "Venomous"
        );
    }
}
