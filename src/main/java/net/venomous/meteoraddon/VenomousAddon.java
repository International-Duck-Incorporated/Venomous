package net.venomous.meteoraddon;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.venomous.meteoraddon.modules.ModuleRegistry;
import org.slf4j.Logger;

public class VenomousAddon extends MeteorAddon {
    public static final String VERSION = "7.0.0";
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = new Category("Venomous");
    public static final Category CATEGORY_OP = new Category("Venomous OP");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Venomous V7.0.0 - Optimized & Refined.");
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
