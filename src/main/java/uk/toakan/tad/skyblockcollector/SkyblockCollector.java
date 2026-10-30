package uk.toakan.tad.skyblockcollector;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class SkyblockCollector {
    public static final String MOD_ID = "skyblock-collector";
    public static final String NAME = "SkyblockCollector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** config/skyblock-collector/: settings.json and accessory_families.json. */
    public static final File DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).toFile();

    private SkyblockCollector() {}
}
