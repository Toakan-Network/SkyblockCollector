package uk.toakan.tad.skyblockcollector;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Move HUD for the one panel: drag it to move it, a corner to scale it (keeps its shape), an edge to resize it. A
 * dragged panel snaps to the screen edges and centre (see {@link PanelSnap}); holding Shift places it freely.
 */
public final class CollectorMoveScreen extends Screen {
    private static final int HANDLE = 10;
    /** How close (GUI pixels) the mouse must be to an edge to grab it. */
    private static final int EDGE_GRAB = 3;
    private static final int GOLD = 0xFFFFAA00;
    private static final int SNAP_GUIDE = 0xFF55FFFF;
    private static final int BUTTON_WIDTH = 90;

    private enum Handle {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT;

        boolean isCorner() {
            return this == TOP_LEFT || this == TOP_RIGHT || this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
        boolean left() { return this == TOP_LEFT || this == BOTTOM_LEFT || this == LEFT; }
        boolean top() { return this == TOP_LEFT || this == TOP_RIGHT || this == TOP; }
        boolean horizontal() { return this == LEFT || this == RIGHT; }
    }

    private final Screen parent;
    /** Working copy: position in GUI pixels, size in HUD units. */
    private CollectorHud.Rect rect;
    private CollectorHud.Rect startRect;
    private boolean dragging;
    private Handle resizing = Handle.NONE;
    private int dragOffsetX, dragOffsetY;
    private int startMouseX, startMouseY;
    /** Drawn height last frame (GUI pixels), so snapping uses the bottom edge the player sees. */
    private int drawnHeight;
    /** The lines the dragged panel snapped to, drawn as guides; -1 when not snapped. */
    private int guideX = -1;
    private int guideY = -1;

    /** @param parent screen to return to on Done, or null to close. */
    public CollectorMoveScreen(Screen parent) {
        super(Component.translatable("skyblock-collector.move_hud"));
        this.parent = parent;
        this.rect = CollectorHud.rect();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("skyblock-collector.move_hud.reset"), button -> {
            Settings.resetHudRect();
            rect = CollectorHud.rect();
        }).bounds(width - 2 * BUTTON_WIDTH - 8, 1, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width - BUTTON_WIDTH - 4, 1, BUTTON_WIDTH, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // See-through, so the panel can be placed against the game behind it; dimmed a little so the handles stand out.
        context.fill(0, 0, width, height, 0x40000000);
        context.fill(0, 0, width, 22, 0xCC000000);
        context.text(font, title, 8, 7, GOLD, false);
        int hintX = 8 + font.width(title) + 12;
        context.text(font, font.plainSubstrByWidth(Component.translatable("skyblock-collector.move_hud.hint").getString(),
            Math.max(0, width - 2 * BUTTON_WIDTH - 16 - hintX)), hintX, 7, 0xFFBBBBBB, false);

        float scale = rect.factor();
        int w = Math.round(rect.width() * scale);
        int h = Math.round(rect.height() * scale);
        // Faint backing marks the panel's full max height; the panel itself shows as it will on the HUD.
        context.fill(rect.x(), rect.y(), rect.x() + w, rect.y() + h, 0x30000000);
        int drawn = CollectorHud.draw(context, rect.x(), rect.y(), rect.width(), rect.height(), scale, true);
        drawnHeight = Math.min(h, Math.round(drawn * scale));
        boolean busy = dragging || resizing != Handle.NONE;
        context.outline(rect.x(), rect.y(), w, h, busy ? 0xFFFFDD00 : 0x88FFFFFF);
        // White grips mid-edge (resize), gold squares on the corners (scale).
        int midX = rect.x() + w / 2;
        int midY = rect.y() + h / 2;
        context.fill(midX - HANDLE, rect.y() - 1, midX + HANDLE, rect.y() + 2, 0xFFFFFFFF);
        context.fill(midX - HANDLE, rect.y() + h - 2, midX + HANDLE, rect.y() + h + 1, 0xFFFFFFFF);
        context.fill(rect.x() - 1, midY - HANDLE, rect.x() + 2, midY + HANDLE, 0xFFFFFFFF);
        context.fill(rect.x() + w - 2, midY - HANDLE, rect.x() + w + 1, midY + HANDLE, 0xFFFFFFFF);
        for (int[] c : new int[][] {{rect.x(), rect.y()}, {rect.x() + w, rect.y()}, {rect.x(), rect.y() + h}, {rect.x() + w, rect.y() + h}}) {
            context.fill(c[0] - HANDLE / 2, c[1] - HANDLE / 2, c[0] + HANDLE / 2, c[1] + HANDLE / 2, GOLD);
        }
        if (dragging) {
            if (guideX >= 0) context.fill(guideX, 0, guideX + 1, height, SNAP_GUIDE);
            if (guideY >= 0) context.fill(0, guideY, width, guideY + 1, SNAP_GUIDE);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ctx, boolean doubleClick) {
        if (super.mouseClicked(ctx, doubleClick)) return true;
        int mouseX = (int) ctx.x();
        int mouseY = (int) ctx.y();
        float scale = rect.factor();
        int w = Math.round(rect.width() * scale);
        int h = Math.round(rect.height() * scale);
        Handle handle = handleAt(mouseX, mouseY, rect.x(), rect.y(), w, h);
        boolean inside = mouseX >= rect.x() && mouseX <= rect.x() + w && mouseY >= rect.y() && mouseY <= rect.y() + h;
        if (handle == Handle.NONE && !inside) return false;
        resizing = handle;
        dragging = handle == Handle.NONE;
        dragOffsetX = mouseX - rect.x();
        dragOffsetY = mouseY - rect.y();
        startMouseX = mouseX;
        startMouseY = mouseY;
        startRect = rect;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent ctx, double deltaX, double deltaY) {
        if (!dragging && resizing == Handle.NONE) return super.mouseDragged(ctx, deltaX, deltaY);
        int mouseX = (int) ctx.x();
        int mouseY = (int) ctx.y();
        if (dragging) {
            int x = mouseX - dragOffsetX;
            int y = mouseY - dragOffsetY;
            guideX = -1;
            guideY = -1;
            if (!ctx.hasShiftDown()) {
                PanelSnap.Result snapX = PanelSnap.snap(x, Math.round(rect.width() * rect.factor()), new int[] {0, width / 2, width});
                PanelSnap.Result snapY = PanelSnap.snap(y, drawnHeight, new int[] {0, height / 2, height});
                x = snapX.pos();
                y = snapY.pos();
                guideX = snapX.line();
                guideY = snapY.line();
            }
            rect = new CollectorHud.Rect(Math.max(0, Math.min(width - 20, x)), Math.max(0, Math.min(height - 20, y)),
                rect.width(), rect.height(), rect.scale());
            return true;
        }
        float startScale = startRect.factor();
        int startW = Math.max(1, Math.round(startRect.width() * startScale));
        int startH = Math.max(1, Math.round(startRect.height() * startScale));
        boolean left = resizing.left();
        boolean top = resizing.top();
        // Mouse movement in GUI pixels, positive = outward from the panel.
        int pixelDx = (mouseX - startMouseX) * (left ? -1 : 1);
        int pixelDy = (mouseY - startMouseY) * (top ? -1 : 1);
        int newWidth = startRect.width();
        int newHeight = startRect.height();
        int newScale = startRect.scale();
        if (resizing.isCorner()) {
            // Scale: grow by how far the corner moved on both axes, averaged, so the panel keeps its shape.
            float ratio = ((startW + pixelDx) / (float) startW + (startH + pixelDy) / (float) startH) / 2f;
            newScale = CollectorHud.clampScale(Math.round(startRect.scale() * ratio));
        } else if (resizing.horizontal()) {
            newWidth = Math.max(CollectorHud.MIN_WIDTH, startRect.width() + Math.round(pixelDx / startScale));
        } else {
            newHeight = Math.max(CollectorHud.MIN_HEIGHT, startRect.height() + Math.round(pixelDy / startScale));
        }
        // Dragging a left/top handle moves that side, keeping the opposite one in place.
        float scale = newScale / 100f;
        int x = left ? Math.max(0, startRect.x() + startW - Math.round(newWidth * scale)) : startRect.x();
        int y = top ? Math.max(0, startRect.y() + startH - Math.round(newHeight * scale)) : startRect.y();
        rect = new CollectorHud.Rect(x, y, newWidth, newHeight, newScale);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ctx) {
        if (dragging || resizing != Handle.NONE) {
            CollectorHud.setRect(rect);
            rect = CollectorHud.rect();
            dragging = false;
            resizing = Handle.NONE;
            guideX = -1;
            guideY = -1;
            return true;
        }
        return super.mouseReleased(ctx);
    }

    /** Corners win over edges; an edge can be grabbed anywhere along it. */
    private static Handle handleAt(int mouseX, int mouseY, int x, int y, int w, int h) {
        if (near(mouseX, mouseY, x, y)) return Handle.TOP_LEFT;
        if (near(mouseX, mouseY, x + w, y)) return Handle.TOP_RIGHT;
        if (near(mouseX, mouseY, x, y + h)) return Handle.BOTTOM_LEFT;
        if (near(mouseX, mouseY, x + w, y + h)) return Handle.BOTTOM_RIGHT;
        boolean inX = mouseX >= x && mouseX <= x + w;
        boolean inY = mouseY >= y && mouseY <= y + h;
        if (inX && Math.abs(mouseY - y) <= EDGE_GRAB) return Handle.TOP;
        if (inX && Math.abs(mouseY - (y + h)) <= EDGE_GRAB) return Handle.BOTTOM;
        if (inY && Math.abs(mouseX - x) <= EDGE_GRAB) return Handle.LEFT;
        if (inY && Math.abs(mouseX - (x + w)) <= EDGE_GRAB) return Handle.RIGHT;
        return Handle.NONE;
    }

    private static boolean near(int mouseX, int mouseY, int cx, int cy) {
        return Math.abs(mouseX - cx) <= HANDLE / 2 && Math.abs(mouseY - cy) <= HANDLE / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
