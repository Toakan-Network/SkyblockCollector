package uk.toakan.tad.skyblockcollector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.api.TrophyProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Three tabs. Frogs and Fish show what is missing for the chosen goal, or every trophy with its counts. Accessories
 * shows what to get next by coins per power, or what is owned. Data is re-read once a second while open.
 */
public final class CollectorScreen extends Screen {
    private static final int ROW = 12;
    private static final int TOP = 48;
    private static final int LEFT = 12;
    private static final long REFRESH_MS = 1000;
    private static final int GOLD = 0xFFFFAA00;
    private static final int GREY = 0xFFAAAAAA;
    private static final int DARK_GREY = 0xFF777777;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final Map<String, Integer> TIER_COLORS = Map.of(TrophyGoals.BRONZE, 0xFFC97B42,
        TrophyGoals.SILVER, 0xFFC0C0C0, TrophyGoals.GOLD, 0xFFFFAA00, TrophyGoals.DIAMOND, 0xFF55FFFF);
    private static final int TIER_COLUMN = 44;
    private static final int HUD_BUTTON_WIDTH = 66;

    private enum Tab { FROGS, FISH, ACCESSORIES }

    /** One drawn line: text on the left, optional text on the right, or counts per tier for {@code trophy}. */
    private record Line(Component left, int leftColor, Component right, int rightColor, TrophyProgress trophy) {
        static Line text(Component left, int color) {
            return new Line(left, color, null, 0, null);
        }
    }

    /** Stays while the game runs. */
    private static Tab tab = Tab.FROGS;

    private final Screen parent;
    private List<Line> lines = List.of();
    private long refreshedAt;
    private int scroll;

    public CollectorScreen(Screen parent) {
        super(Component.translatable("skyblock-collector.screen.title"));
        this.parent = parent;
        CollectorData.forgetResources();
    }

    @Override
    protected void init() {
        int x = 8;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable(tabKey(t)), b -> {
                tab = t;
                scroll = 0;
                changed();
            }).bounds(x, 8, 70, 20).build();
            button.active = t != tab;
            addRenderableWidget(button);
            x += 74;
        }
        x += 8;
        if (tab != Tab.ACCESSORIES) {
            addRenderableWidget(Button.builder(Component.translatable("skyblock-collector.screen.goal", CollectorData.tierName(goal())), b -> {
                Settings s = Settings.get();
                if (tab == Tab.FROGS) s.frogGoal = TrophyGoals.next(s.frogGoal);
                else s.fishGoal = TrophyGoals.next(s.fishGoal);
                Settings.save();
                changed();
            }).bounds(x, 8, 90, 20).build());
            x += 94;
        }
        Settings s = Settings.get();
        String toggle = tab == Tab.ACCESSORIES
            ? (s.showAll ? "skyblock-collector.screen.show.owned" : "skyblock-collector.screen.show.next")
            : (s.showAll ? "skyblock-collector.screen.show.all" : "skyblock-collector.screen.show.missing");
        addRenderableWidget(Button.builder(Component.translatable(toggle), b -> {
            s.showAll = !s.showAll;
            Settings.save();
            changed();
        }).bounds(x, 8, 90, 20).build());
        if (tab == Tab.ACCESSORIES) {
            AccessoryFamilies.ensureLoaded();
            if (!s.showAll) {
                addRenderableWidget(Button.builder(Component.translatable(filterKey(s.accessoryFilter)), b -> {
                    s.accessoryFilter = s.accessoryFilter == null ? AccessorySuggestions.Filter.CRAFT : s.accessoryFilter.next();
                    Settings.save();
                    changed();
                }).bounds(x + 94, 8, 90, 20).build());
            }
        }
        addHudButtons(s);
    }

    private static String filterKey(AccessorySuggestions.Filter filter) {
        if (filter == AccessorySuggestions.Filter.CRAFT) return "skyblock-collector.screen.filter.craft";
        if (filter == AccessorySuggestions.Filter.BUY) return "skyblock-collector.screen.filter.buy";
        return "skyblock-collector.screen.filter.all";
    }

    /** Second row, on the right: the panel on or off, this tab's section on or off, and Move HUD. */
    private void addHudButtons(Settings s) {
        int x = width - 8 - HUD_BUTTON_WIDTH;
        addRenderableWidget(Button.builder(Component.translatable("skyblock-collector.move_hud"),
            b -> CollectorHud.openMove(this)).bounds(x, 30, HUD_BUTTON_WIDTH, 16).build());
        x -= HUD_BUTTON_WIDTH + 4;
        addRenderableWidget(Button.builder(Component.translatable(sectionOnHud(s)
            ? "skyblock-collector.screen.hud_section.on" : "skyblock-collector.screen.hud_section.off"), b -> {
            switch (tab) {
                case FROGS -> s.hudFrogs = !s.hudFrogs;
                case FISH -> s.hudFish = !s.hudFish;
                case ACCESSORIES -> s.hudAccessories = !s.hudAccessories;
            }
            Settings.save();
            changed();
        }).bounds(x, 30, HUD_BUTTON_WIDTH, 16).build());
        x -= HUD_BUTTON_WIDTH + 4;
        addRenderableWidget(Button.builder(Component.translatable(s.hud
            ? "skyblock-collector.screen.hud.on" : "skyblock-collector.screen.hud.off"), b -> {
            s.hud = !s.hud;
            Settings.save();
            changed();
        }).bounds(x, 30, HUD_BUTTON_WIDTH, 16).build());
    }

    private static boolean sectionOnHud(Settings s) {
        return switch (tab) {
            case FROGS -> s.hudFrogs;
            case FISH -> s.hudFish;
            case ACCESSORIES -> s.hudAccessories;
        };
    }

    private void changed() {
        refreshedAt = 0;
        CollectorHud.refresh();
        rebuildWidgets();
    }

    private static String tabKey(Tab t) {
        return switch (t) {
            case FROGS -> "skyblock-collector.screen.tab.frogs";
            case FISH -> "skyblock-collector.screen.tab.fish";
            case ACCESSORIES -> "skyblock-collector.screen.tab.accessories";
        };
    }

    private static String goal() {
        return tab == Tab.FROGS ? Settings.get().frogGoal : Settings.get().fishGoal;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        if (now - refreshedAt >= REFRESH_MS) {
            refreshedAt = now;
            SkyblockDbApi db = SkyblockDbApi.get().orElse(null);
            lines = db == null ? List.of() : tab == Tab.ACCESSORIES ? accessoryLines(db) : trophyLines(db);
        }
        int bottom = height - 8;
        int visible = Math.max(1, (bottom - TOP) / ROW);
        scroll = Math.max(0, Math.min(scroll, lines.size() - visible));
        int y = 34;
        if (!lines.isEmpty()) {
            Line header = lines.getFirst();
            context.text(font, header.left(), LEFT, y, header.leftColor(), false);
        }
        y = TOP;
        for (int i = scroll + 1; i < lines.size() && y + ROW <= bottom; i++, y += ROW) {
            drawLine(context, lines.get(i), y);
        }
    }

    private void drawLine(GuiGraphicsExtractor context, Line line, int y) {
        context.text(font, line.left(), LEFT, y, line.leftColor(), false);
        if (line.trophy() != null) {
            int x = width - 8 - TIER_COLUMN * TrophyGoals.TIERS.size();
            for (String tier : TrophyGoals.TIERS) {
                String count = String.format("%,d", line.trophy().caught(tier));
                context.text(font, count, x + TIER_COLUMN - font.width(count), y, TIER_COLORS.get(tier), false);
                x += TIER_COLUMN;
            }
        } else if (line.right() != null) {
            context.text(font, line.right(), width - 8 - font.width(line.right()), y, line.rightColor(), false);
        }
    }

    /** First line is the header, here and in {@link #accessoryLines}. */
    private List<Line> trophyLines(SkyblockDbApi db) {
        List<TrophyProgress> trophies = tab == Tab.FROGS ? db.trophyFrogs() : db.trophyFish();
        String goal = goal();
        List<TrophyProgress> missing = TrophyGoals.missing(trophies, goal);
        List<Line> out = new ArrayList<>();
        out.add(Line.text(Component.translatable("skyblock-collector.trophy.progress",
            trophies.size() - missing.size(), trophies.size(), CollectorData.tierName(goal)), GOLD));
        if (TrophyGoals.nothingRecorded(trophies)) out.add(Line.text(Component.translatable("skyblock-collector.trophy.empty"), GREY));
        else if (missing.isEmpty()) out.add(Line.text(Component.translatable("skyblock-collector.trophy.complete"), GREEN));
        if (Settings.get().showAll) {
            for (TrophyProgress t : trophies) {
                out.add(new Line(Component.literal(t.name()), CollectorData.trophyColor(db, t), null, 0, t));
            }
        } else {
            for (TrophyProgress t : missing) {
                String hint = hintKey(t);
                out.add(new Line(Component.literal(t.name()), CollectorData.trophyColor(db, t),
                    Language.getInstance().has(hint) ? Component.translatable(hint) : null, GREY, null));
            }
        }
        return out;
    }

    private static String hintKey(TrophyProgress t) {
        return "skyblock-collector.hint." + t.id().toLowerCase(Locale.ROOT);
    }

    private List<Line> accessoryLines(SkyblockDbApi db) {
        AccessoryFamilies families = AccessoryFamilies.current();
        AccessorySuggestions.Result result = CollectorData.accessories(db);
        List<Line> out = new ArrayList<>();
        out.add(Line.text(Component.translatable("skyblock-collector.accessory.total",
            result.owned().size(), result.totalPower()), GOLD));
        if (result.owned().isEmpty()) out.add(Line.text(Component.translatable("skyblock-collector.accessory.empty"), GREY));
        if (Settings.get().showAll) {
            for (AccessorySuggestions.Owned o : result.owned()) {
                out.add(new Line(Component.literal(o.name()), CollectorData.rarityColor(o.rarity()), o.counted()
                    ? Component.literal(Integer.toString(o.power()))
                    : Component.translatable("skyblock-collector.accessory.not_counted"), o.counted() ? WHITE : DARK_GREY, null));
            }
            return out;
        }
        if (families == null) {
            out.add(Line.text(Component.translatable("skyblock-collector.accessory.no_families"), GREY));
            return out;
        }
        if (result.canMake().isEmpty() && result.canBuy().isEmpty() && result.priced().isEmpty() && result.unpriced().isEmpty()) {
            boolean filtered = Settings.get().accessoryFilter != null && Settings.get().accessoryFilter != AccessorySuggestions.Filter.ALL;
            out.add(filtered ? Line.text(Component.translatable("skyblock-collector.accessory.filter_empty"), GREY)
                : Line.text(Component.translatable("skyblock-collector.accessory.no_suggestions"), GREEN));
            return out;
        }
        out.add(Line.text(Component.translatable("skyblock-collector.accessory.estimate"), DARK_GREY));
        addGroup(db, out, "skyblock-collector.accessory.can_make", result.canMake().stream().filter(s -> !s.shop()).toList(), true);
        addGroup(db, out, "skyblock-collector.accessory.can_buy_npc", result.canMake().stream().filter(s -> s.shop()).toList(), true);
        addGroup(db, out, "skyblock-collector.accessory.can_buy", result.canBuy(), false);
        List<AccessorySuggestions.Suggestion> rest = new ArrayList<>(result.priced());
        rest.addAll(result.unpriced());
        addGroup(db, out, "skyblock-collector.accessory.rest", rest, false);
        return out;
    }

    /** A heading and its rows; nothing for an empty group. Only making something depends on a recipe being unlocked. */
    private static void addGroup(SkyblockDbApi db, List<Line> out, String heading,
                                 List<AccessorySuggestions.Suggestion> group, boolean showRequirements) {
        if (group.isEmpty()) return;
        out.add(Line.text(Component.translatable(heading), GOLD));
        for (AccessorySuggestions.Suggestion s : group) {
            Component name = suggestionName(s);
            if (s.forge()) name = name.copy().append(" ").append(Component.translatable("skyblock-collector.accessory.forge_mark"));
            if (!s.event().isEmpty()) {
                name = name.copy().append(" ").append(Component.translatable(
                    "skyblock-collector.accessory.needs", CollectorData.eventName(s.event())));
            }
            if (showRequirements) {
                List<String> needs = db.requirements(s.id());
                if (!needs.isEmpty()) {
                    name = name.copy().append(" ").append(Component.translatable(
                        "skyblock-collector.accessory.needs", String.join(", ", needs)));
                }
            }
            out.add(new Line(name, CollectorData.rarityColor(s.rarity()), Double.isNaN(s.price())
                ? Component.translatable("skyblock-collector.accessory.no_price")
                : Component.translatable("skyblock-collector.accessory.per_power", CollectorData.coins(s.price()), CollectorData.coins(s.coinsPerPower())),
                Double.isNaN(s.price()) ? GREY : GOLD, null));
        }
    }

    private static Component suggestionName(AccessorySuggestions.Suggestion s) {
        return Component.literal(s.name() + " ").append(Component.translatable("skyblock-collector.accessory.gain", s.gain()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) Math.signum(verticalAmount) * 3);
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
