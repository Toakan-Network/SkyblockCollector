package uk.toakan.tad.skyblockcollector.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import uk.toakan.tad.huddable.api.HuddableApi;
import uk.toakan.tad.huddable.api.HuddablePlugin;
import uk.toakan.tad.skyblockcollector.CollectorHud;
import uk.toakan.tad.skyblockcollector.Settings;
import uk.toakan.tad.skyblockcollector.SkyblockCollector;

/**
 * Hands the HUD panel to Huddable, so its editor moves, scales, resizes and hides it along with the rest of the HUD.
 * The panel starts from its own saved place. Loaded only by Huddable through the "huddable" entrypoint, so this is the
 * only class that may touch its API.
 */
public final class HuddableCompat implements HuddablePlugin {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(SkyblockCollector.MOD_ID, "panel");

    @Override
    public void register() {
        try {
            // Everything is only asked for when drawing: Huddable may call this before SkyblockCollector's own init.
            HuddableApi.Spec spec = HuddableApi.Spec.of((w, h) -> {
                    CollectorHud.Rect rect = CollectorHud.rect();
                    return new HuddableApi.Box(rect.x(), rect.y(), rect.width(), rect.height());
                })
                .withBaseScale(() -> CollectorHud.rect().scale() / 100.0)
                .resizable(CollectorHud.MIN_WIDTH, CollectorHud.MIN_HEIGHT)
                .listedWhen(() -> Settings.get().hud);
            HuddableApi.ignoreLayer(CollectorHud.LAYER);
            HuddableApi.register(ID, Component.translatable("skyblock-collector.hud.title"), spec);
            boolean frames = hasFrames();
            CollectorHud.setPlacer(new CollectorHud.Placer() {
                @Override
                public CollectorHud.Placement place() {
                    HuddableApi.Placement p = HuddableApi.placement(ID);
                    return p == null ? null : new CollectorHud.Placement(p.x(), p.y(), p.width(), p.height(), p.scale(), p.hidden());
                }

                @Override
                public void openEditor(Screen parent) {
                    HuddableApi.openEditor(parent);
                }

                @Override
                public int frameInset() {
                    return frames ? HuddableApi.frameInset(ID) : -1;
                }

                @Override
                public void drawFrame(GuiGraphicsExtractor context, int width, int height) {
                    if (frames) HuddableApi.drawFrame(context, ID, 0, 0, width, height);
                }
            });
        } catch (LinkageError e) {
            SkyblockCollector.LOGGER.warn("This Huddable version doesn't offer what SkyblockCollector needs; its own Move HUD places the panel", e);
        }
    }

    /** True when this Huddable draws frames (0.5.0 on); older ones only place the panel. */
    private static boolean hasFrames() {
        try {
            HuddableApi.frameInset(ID);
            return true;
        } catch (LinkageError e) {
            return false;
        }
    }
}
