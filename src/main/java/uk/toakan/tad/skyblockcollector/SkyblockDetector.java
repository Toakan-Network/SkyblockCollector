package uk.toakan.tad.skyblockcollector;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Tells whether the player is on a Hypixel SkyBlock server, from the official Hypixel Mod API's location event.
 * The key and command are off everywhere else. If the Mod API is somehow missing, nothing counts as SkyBlock.
 */
public final class SkyblockDetector {
    private static final String MOD_API_ID = "hypixel-mod-api";

    private static volatile boolean onSkyblock = false;

    private SkyblockDetector() {}

    public static void register() {
        if (FabricLoader.getInstance().isModLoaded(MOD_API_ID)) {
            HypixelLocationListener.register();
        } else {
            SkyblockCollector.LOGGER.warn("Hypixel Mod API not found; SkyBlock detection unavailable, so the key and command stay off");
        }
        ClientTickEvents.START_CLIENT_TICK.register(SkyblockDetector::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onSkyblock = false);
    }

    public static boolean isOnSkyblock() {
        return onSkyblock;
    }

    /** Called by {@link HypixelLocationListener} on every server change. */
    static void onModApiLocation(boolean skyblock, String mode) {
        onSkyblock = skyblock;
    }

    private static void tick(Minecraft client) {
        // The dev client runs in singleplayer, where the Mod API sends nothing.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            onSkyblock = client.level != null;
        }
    }
}
