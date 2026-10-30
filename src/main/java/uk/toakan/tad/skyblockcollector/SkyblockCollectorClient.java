package uk.toakan.tad.skyblockcollector;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkyblockCollectorClient implements ClientModInitializer {
    /** Own section in Options > Controls > Key Binds; label from key.category.skyblock-collector.main. */
    private static final KeyMapping.Category KEY_CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyblockCollector.MOD_ID, "main"));
    private static final Map<String, Integer> INPUT_CODES = new ConcurrentHashMap<>();
    private static KeyMapping openKey;

    /**
     * Reads an InputConstants code at runtime. The codes differ between Minecraft versions (26.3 moved
     * from GLFW to SDL), and javac would inline a direct reference, so one jar works on both this way.
     */
    public static int inputCode(String name) {
        // Looked up once per name: callers ask on every click.
        return INPUT_CODES.computeIfAbsent(name, n -> {
            try {
                return InputConstants.class.getField(n).getInt(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Missing InputConstants." + n, e);
            }
        });
    }

    @Override
    public void onInitializeClient() {
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.skyblock-collector.open", inputCode("KEY_J"), KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(Milestones::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // The key only acts on SkyBlock; presses are still consumed so they don't queue up.
            if (openKey.consumeClick() && SkyblockDetector.isOnSkyblock() && client.gui.screen() == null) {
                client.gui.setScreen(new CollectorScreen(null));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommands.literal("skyblockcollector").executes(context -> {
                if (!SkyblockDetector.isOnSkyblock()) {
                    context.getSource().sendError(Component.translatable("skyblock-collector.command.not_on_skyblock"));
                    return 0;
                }
                // Next tick: the chat screen closes itself after running the command.
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> client.gui.setScreen(new CollectorScreen(null)));
                return 1;
            })));

        SkyblockDetector.register();
        CollectorHud.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> JsonFiles.flush());
    }
}
