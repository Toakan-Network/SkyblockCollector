package uk.toakan.tad.skyblockcollector;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.api.TrophyProgress;

import java.util.ArrayList;
import java.util.List;

/**
 * A panel in the style of Shopping List's HUD: what is missing for each trophy goal and which accessories to get next.
 * Rows are worked out at most once a second on the client tick; drawing only reads the finished list.
 */
public final class CollectorHud {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(SkyblockCollector.MOD_ID, "hud");
    public static final int MIN_WIDTH = 100;
    public static final int MIN_HEIGHT = 40;
    public static final int MIN_SCALE = 25;
    public static final int MAX_SCALE = 400;
    private static final int TITLE_BAR = 16;
    private static final int ROW = 10;
    private static final int PADDING = 5;
    private static final long REFRESH_MS = 1000;
    private static final int GREEN = 0xFF55FF55;
    private static final int GOLD = 0xFFFFAA00;
    private static final int GREY = 0xFFAAAAAA;

    /** One line: left text, optional right text ("" for none). */
    private record Row(String left, int leftColor, String right, int rightColor) {}

    /** Top-left in GUI pixels, width and max height in HUD units, scale in percent. */
    public record Rect(int x, int y, int width, int height, int scale) {
        float factor() {
            return scale / 100f;
        }
    }

    /** Where another mod has put the panel, as {@link Rect} but with the scale as a factor. */
    public record Placement(int x, int y, int width, int height, float scale, boolean hidden) {}

    /** Places the panel and draws its frame (Huddable, when installed). */
    public interface Placer {
        /** This frame's placement, or null to use the panel's own. */
        Placement place();

        void openEditor(Screen parent);

        /** Space to keep content off the frame the placer draws, or -1 when it draws none. */
        int frameInset();

        void drawFrame(GuiGraphicsExtractor context, int width, int height);
    }

    /** Set by {@code compat.HuddableCompat}; null without Huddable. */
    private static volatile Placer placer;
    private static volatile List<Row> rows = List.of();
    private static long refreshedAt;

    private CollectorHud() {}

    public static void register() {
        HudElementRegistry.addLast(LAYER, (context, tickCounter) -> {
            if (!visible()) return;
            Placer current = placer;
            Placement placed = current == null ? null : current.place();
            if (placed != null) {
                if (!placed.hidden()) draw(context, placed.x(), placed.y(), placed.width(), placed.height(), placed.scale(), false);
                return;
            }
            Rect r = rect();
            draw(context, r.x(), r.y(), r.width(), r.height(), r.factor(), false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static void setPlacer(Placer newPlacer) {
        placer = newPlacer;
    }

    /** Opens the editor that places the panel: Huddable's when it does, otherwise the Move HUD screen. */
    public static void openMove(Screen parent) {
        Placer current = placer;
        if (current != null) current.openEditor(parent);
        else Minecraft.getInstance().gui.setScreen(new CollectorMoveScreen(parent));
    }

    /** A setting changed: work the rows out again on the next tick. */
    public static void refresh() {
        refreshedAt = 0;
    }

    private static boolean visible() {
        return Settings.get().hud && SkyblockDetector.isOnSkyblock();
    }

    private static void tick() {
        if (!visible()) {
            rows = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - refreshedAt < REFRESH_MS) return;
        refreshedAt = now;
        rows = SkyblockDbApi.get().map(CollectorHud::build).orElse(List.of());
    }

    private static List<Row> build(SkyblockDbApi db) {
        Settings s = Settings.get();
        List<Row> out = new ArrayList<>();
        if (s.hudFrogs) trophies(out, db, db.trophyFrogs(), s.frogGoal, "skyblock-collector.hud.frogs");
        if (s.hudFish) trophies(out, db, db.trophyFish(), s.fishGoal, "skyblock-collector.hud.fish");
        if (s.hudAccessories) accessories(out, db);
        return List.copyOf(out);
    }

    /** Left out when nothing is recorded (it would list every trophy) and when the goal is met. */
    private static void trophies(List<Row> out, SkyblockDbApi db, List<TrophyProgress> trophies, String goal, String heading) {
        if (TrophyGoals.nothingRecorded(trophies)) return;
        List<TrophyProgress> missing = TrophyGoals.missing(trophies, goal);
        if (missing.isEmpty()) return;
        out.add(new Row(I18n.get(heading, trophies.size() - missing.size(), trophies.size(),
            CollectorData.tierName(goal).getString()), Settings.get().hudTitleColor, "", 0));
        HudRows.Limited<TrophyProgress> limited = HudRows.limit(missing, Settings.get().hudRows);
        for (TrophyProgress t : limited.shown()) {
            out.add(new Row(t.name(), CollectorData.trophyColor(db, t), "", 0));
        }
        addMore(out, limited.more());
    }

    /** Left out until the bag has been seen and the upgrade lines are loaded. */
    private static void accessories(List<Row> out, SkyblockDbApi db) {
        AccessoryFamilies.ensureLoaded();
        if (AccessoryFamilies.current() == null) return;
        AccessorySuggestions.Result result = CollectorData.accessories(db);
        if (result.owned().isEmpty()) return;
        List<Row> group = new ArrayList<>();
        String canMake = I18n.get("skyblock-collector.hud.can_make");
        for (AccessorySuggestions.Suggestion s : result.canMake()) group.add(accessoryRow(s, canMake, GREEN));
        for (AccessorySuggestions.Suggestion s : result.canBuy()) group.add(accessoryRow(s, CollectorData.coins(s.price()), GOLD));
        for (AccessorySuggestions.Suggestion s : result.priced()) group.add(accessoryRow(s, CollectorData.coins(s.price()), GREY));
        for (AccessorySuggestions.Suggestion s : result.unpriced()) group.add(accessoryRow(s, "", 0));
        if (group.isEmpty()) return;
        out.add(new Row(I18n.get("skyblock-collector.hud.accessories", result.totalPower()), Settings.get().hudTitleColor, "", 0));
        HudRows.Limited<Row> limited = HudRows.limit(group, Settings.get().hudRows);
        out.addAll(limited.shown());
        addMore(out, limited.more());
    }

    private static Row accessoryRow(AccessorySuggestions.Suggestion s, String right, int rightColor) {
        return new Row(s.name() + " " + I18n.get("skyblock-collector.accessory.gain", s.gain()),
            CollectorData.rarityColor(s.rarity()), right, rightColor);
    }

    private static void addMore(List<Row> out, int more) {
        if (more > 0) out.add(new Row(I18n.get("skyblock-collector.hud.more", more), GREY, "", 0));
    }

    /**
     * Draws the panel with its top-left at (x, y) in GUI pixels; width and max height are HUD units. As tall as its
     * content up to the max height; anything below is cut off and marked with "…". Returns the drawn height in HUD
     * units (0 if nothing was drawn). The preview draws an empty panel too, so it can be placed.
     */
    public static int draw(GuiGraphicsExtractor context, int x, int y, int width, int maxHeight, float scale, boolean preview) {
        List<Row> lines = rows;
        if (lines.isEmpty() && !preview) return 0;
        Settings s = Settings.get();
        Font font = Minecraft.getInstance().font;
        // A Huddable frame set for this panel replaces the panel's own background.
        Placer current = placer;
        int frameInset = current == null ? -1 : current.frameInset();
        boolean ownFrame = frameInset < 0;
        int inset = Math.max(PADDING, frameInset);
        int shownRows = Math.max(1, lines.size());
        int natural = TITLE_BAR + 2 + shownRows * ROW + 2 + Math.max(0, frameInset);
        int height = Math.min(maxHeight, natural);
        boolean cutOff = natural > height;

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        // Its own stratum, so the panel isn't tested against everything vanilla and other mods drew before it.
        context.nextStratum();
        if (ownFrame) {
            RoundedBox.fill(context, 0, 0, width, height, s.hudRadius, s.hudBackground);
            RoundedBox.fill(context, 0, 0, width, TITLE_BAR, s.hudRadius, true, false, s.hudHeaderBackground);
        } else {
            current.drawFrame(context, width, height);
        }
        String title = font.plainSubstrByWidth(I18n.get("skyblock-collector.hud.title"), width - 2 * inset);
        context.text(font, title, inset, (TITLE_BAR - font.lineHeight) / 2 + 1, s.hudTitleColor, true);

        int bottom = height - Math.max(0, frameInset) - (cutOff ? ROW : 0);
        context.enableScissor(0, TITLE_BAR, width, bottom);
        int rowY = TITLE_BAR + 2;
        if (lines.isEmpty()) {
            context.text(font, font.plainSubstrByWidth(I18n.get("skyblock-collector.hud.empty"), width - 2 * inset),
                inset, rowY, GREY, true);
        }
        for (Row row : lines) {
            drawRow(context, font, row, inset, width, rowY);
            rowY += ROW;
        }
        context.disableScissor();
        if (cutOff) {
            String more = "…";
            context.text(font, more, (width - font.width(more)) / 2, bottom, s.hudTextColor, true);
        }
        context.pose().popMatrix();
        return height;
    }

    private static void drawRow(GuiGraphicsExtractor context, Font font, Row row, int inset, int width, int y) {
        int room = width - 2 * inset;
        if (!row.right().isEmpty()) {
            int rightWidth = font.width(row.right());
            context.text(font, row.right(), width - inset - rightWidth, y, row.rightColor(), true);
            room -= rightWidth + 4;
        }
        context.text(font, font.plainSubstrByWidth(row.left(), Math.max(0, room)), inset, y, row.leftColor(), true);
    }

    /** The saved panel, moved with the screen size and kept on screen. */
    public static Rect rect() {
        Settings s = Settings.get();
        Window window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        int scale = clampScale(s.hudScale);
        int x = s.hudRefWidth > 0 ? Math.round(s.hudX * (float) screenW / s.hudRefWidth) : s.hudX;
        int y = s.hudRefHeight > 0 ? Math.round(s.hudY * (float) screenH / s.hudRefHeight) : s.hudY;
        int w = Math.round(s.hudWidth * scale / 100f);
        // Height is only a limit (the panel is often shorter), so just keep the top in view.
        x = Math.max(0, Math.min(x, screenW - Math.min(w, screenW)));
        y = Math.max(0, Math.min(y, screenH - 20));
        return new Rect(x, y, Math.max(MIN_WIDTH, s.hudWidth), Math.max(MIN_HEIGHT, s.hudHeight), scale);
    }

    public static void setRect(Rect rect) {
        Settings s = Settings.get();
        Window window = Minecraft.getInstance().getWindow();
        s.hudX = rect.x();
        s.hudY = rect.y();
        s.hudWidth = Math.max(MIN_WIDTH, rect.width());
        s.hudHeight = Math.max(MIN_HEIGHT, rect.height());
        s.hudScale = clampScale(rect.scale());
        s.hudRefWidth = window.getGuiScaledWidth();
        s.hudRefHeight = window.getGuiScaledHeight();
        Settings.save();
    }

    public static int clampScale(int percent) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, percent));
    }
}
