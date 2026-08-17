package dev.b_p40lz.autopve;

import dev.b_p40lz.autopve.hud.PacketDisplay;
import dev.b_p40lz.autopve.modules.AutoPVE;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("AutoPVE");
    public static final HudGroup HUD_GROUP = new HudGroup("AutoPVE");

    @Override
    public void onInitialize() {
        LOG.info("Initializing AutoPVE");

        Modules.get().add(new AutoPVE());
        Hud.get().register(PacketDisplay.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.b_p40lz.autopve";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("bp40lZ", "AutoPVE-lblt-main");
    }
}
