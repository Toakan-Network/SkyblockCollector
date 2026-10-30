package uk.toakan.tad.skyblockcollector;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import skyblockdb.api.Currency;
import skyblockdb.api.PriceSource;
import skyblockdb.api.ReferenceItem;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.api.TrophyProgress;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

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
    private static final Map<String, Integer> RARITY_COLORS = Map.of(
        "COMMON", 0xFFFFFFFF, "UNCOMMON", 0xFF55FF55, "RARE", 0xFF5555FF, "EPIC", 0xFFAA00AA,
        "LEGENDARY", 0xFFFFAA00, "MYTHIC", 0xFFFF55FF, "DIVINE", 0xFF55FFFF, "SPECIAL", 0xFFFF5555,
        "VERY_SPECIAL", 0xFFFF5555);
    private static final Map<String, Integer> TIER_COLORS = Map.of(TrophyGoals.BRONZE, 0xFFC97B42,
        TrophyGoals.SILVER, 0xFFC0C0C0, TrophyGoals.GOLD, 0xFFFFAA00, TrophyGoals.DIAMOND, 0xFF55FFFF);
    private static final int TIER_COLUMN = 44;

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
    private Map<String, Integer> power;

    public CollectorScreen(Screen parent) {
        super(Component.translatable("skyblock-collector.screen.title"));
        this.parent = parent;
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
            addRenderableWidget(Button.builder(Component.translatable("skyblock-collector.screen.goal", tierName(goal())), b -> {
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
        if (tab == Tab.ACCESSORIES) AccessoryFamilies.ensureLoaded();
    }

    private void changed() {
        refreshedAt = 0;
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

    private static Component tierName(String tier) {
        return Component.translatable(switch (tier) {
            case TrophyGoals.SILVER -> "skyblock-collector.tier.silver";
            case TrophyGoals.GOLD -> "skyblock-collector.tier.gold";
            case TrophyGoals.DIAMOND -> "skyblock-collector.tier.diamond";
            default -> "skyblock-collector.tier.bronze";
        });
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
            trophies.size() - missing.size(), trophies.size(), tierName(goal)), GOLD));
        boolean nothingRecorded = trophies.stream().allMatch(t -> t.caught().values().stream().allMatch(n -> n == 0));
        if (nothingRecorded) out.add(Line.text(Component.translatable("skyblock-collector.trophy.empty"), GREY));
        else if (missing.isEmpty()) out.add(Line.text(Component.translatable("skyblock-collector.trophy.complete"), GREEN));
        if (Settings.get().showAll) {
            for (TrophyProgress t : trophies) {
                out.add(new Line(Component.literal(t.name()), trophyColor(db, t), null, 0, t));
            }
        } else {
            for (TrophyProgress t : missing) {
                String hint = hintKey(t);
                out.add(new Line(Component.literal(t.name()), trophyColor(db, t),
                    Language.getInstance().has(hint) ? Component.translatable(hint) : null, GREY, null));
            }
        }
        return out;
    }

    private static String hintKey(TrophyProgress t) {
        return "skyblock-collector.hint." + t.id().toLowerCase(Locale.ROOT);
    }

    /** Trophy items are named ID_TIER in the item data, each tier with the trophy's rarity. */
    private static int trophyColor(SkyblockDbApi db, TrophyProgress t) {
        return db.item(t.id() + "_" + TrophyGoals.BRONZE).map(i -> rarityColor(i.rarity())).orElse(WHITE);
    }

    private static int rarityColor(String rarity) {
        return RARITY_COLORS.getOrDefault(rarity, WHITE);
    }

    private List<Line> accessoryLines(SkyblockDbApi db) {
        AccessoryFamilies families = AccessoryFamilies.current();
        Map<String, ReferenceItem> items = db.referenceItems();
        AccessorySuggestions.Result result = AccessorySuggestions.build(db.accessories(), items,
            families == null ? AccessoryFamilies.EMPTY : families, power(), id -> price(db, id), db::recipes, stock(db));
        List<Line> out = new ArrayList<>();
        out.add(Line.text(Component.translatable("skyblock-collector.accessory.total",
            result.owned().size(), result.totalPower()), GOLD));
        if (result.owned().isEmpty()) out.add(Line.text(Component.translatable("skyblock-collector.accessory.empty"), GREY));
        if (Settings.get().showAll) {
            for (AccessorySuggestions.Owned o : result.owned()) {
                out.add(new Line(Component.literal(o.name()), rarityColor(o.rarity()), o.counted()
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
            out.add(Line.text(Component.translatable("skyblock-collector.accessory.no_suggestions"), GREEN));
            return out;
        }
        out.add(Line.text(Component.translatable("skyblock-collector.accessory.estimate"), DARK_GREY));
        addGroup(db, out, "skyblock-collector.accessory.can_make", result.canMake(), true);
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
            if (showRequirements) {
                List<String> needs = db.requirements(s.id());
                if (!needs.isEmpty()) {
                    name = name.copy().append(" ").append(Component.translatable(
                        "skyblock-collector.accessory.needs", String.join(", ", needs)));
                }
            }
            out.add(new Line(name, rarityColor(s.rarity()), Double.isNaN(s.price())
                ? Component.translatable("skyblock-collector.accessory.no_price")
                : Component.translatable("skyblock-collector.accessory.per_power", coins(s.price()), coins(s.coinsPerPower())),
                Double.isNaN(s.price()) ? GREY : GOLD, null));
        }
    }

    private static Component suggestionName(AccessorySuggestions.Suggestion s) {
        return Component.literal(s.name() + " ").append(Component.translatable("skyblock-collector.accessory.gain", s.gain()));
    }

    private static AccessorySuggestions.Stock stock(SkyblockDbApi db) {
        Map<String, Currency> currencies = db.currencies();
        return new AccessorySuggestions.Stock(db.totals(),
            currency(currencies, "purse") + currency(currencies, "bank"), currency(currencies, "bits"));
    }

    private static double currency(Map<String, Currency> currencies, String name) {
        Currency currency = currencies.get(name);
        return currency == null ? 0 : currency.value();
    }

    /** What it costs to buy now: lowest BIN, then the bazaar. Empty for what can't be bought. */
    private static OptionalDouble price(SkyblockDbApi db, String id) {
        OptionalDouble bin = db.price(id, PriceSource.LOWEST_BIN);
        return bin.isPresent() && bin.getAsDouble() > 0 ? bin : db.price(id, PriceSource.BAZAAR_BUY);
    }

    /** 1234567 as "1.23M", 12345 as "12.3k". */
    private static String coins(double value) {
        if (value >= 1e9) return String.format("%.2fB", value / 1e9);
        if (value >= 1e6) return String.format("%.2fM", value / 1e6);
        if (value >= 1e4) return String.format("%.1fk", value / 1e3);
        return String.format("%,.0f", value);
    }

    /** Power per rarity, from {@code accessory_power.json}, so a resource pack can change it. */
    private Map<String, Integer> power() {
        if (power == null) {
            Map<String, Integer> read = new HashMap<>();
            Identifier id = Identifier.fromNamespaceAndPath(SkyblockCollector.MOD_ID, "accessory_power.json");
            try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(id)) {
                for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                    read.put(e.getKey(), e.getValue().getAsInt());
                }
            } catch (IOException | RuntimeException e) {
                SkyblockCollector.LOGGER.warn("Could not read {}: {}", id, e.toString());
            }
            power = read;
        }
        return power;
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
