package uk.toakan.tad.skyblockcollector;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import skyblockdb.api.Currency;
import skyblockdb.api.PriceSource;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.api.TrophyProgress;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

/** What the collector screen and the HUD panel both read, so the two can't disagree. Client thread. */
public final class CollectorData {
    private static final int WHITE = 0xFFFFFFFF;
    private static final Map<String, Integer> RARITY_COLORS = Map.of(
        "COMMON", 0xFFFFFFFF, "UNCOMMON", 0xFF55FF55, "RARE", 0xFF5555FF, "EPIC", 0xFFAA00AA,
        "LEGENDARY", 0xFFFFAA00, "MYTHIC", 0xFFFF55FF, "DIVINE", 0xFF55FFFF, "SPECIAL", 0xFFFF5555,
        "VERY_SPECIAL", 0xFFFF5555);
    private static Map<String, Integer> power;
    private static Map<String, String> eventNpcs;

    private CollectorData() {}

    public static AccessorySuggestions.Result accessories(SkyblockDbApi db) {
        AccessoryFamilies families = AccessoryFamilies.current();
        return AccessorySuggestions.build(db.accessories(), db.referenceItems(),
            families == null ? AccessoryFamilies.EMPTY : families, power(), id -> price(db, id), db::recipes, stock(db),
            eventNpcs());
    }

    /** The event's name for a row, or the event's ID when no lang file names it. */
    public static Component eventName(String event) {
        String key = "skyblock-collector.event." + event;
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(event);
    }

    public static Component tierName(String tier) {
        return Component.translatable(switch (tier) {
            case TrophyGoals.SILVER -> "skyblock-collector.tier.silver";
            case TrophyGoals.GOLD -> "skyblock-collector.tier.gold";
            case TrophyGoals.DIAMOND -> "skyblock-collector.tier.diamond";
            default -> "skyblock-collector.tier.bronze";
        });
    }

    /** Trophy items are named ID_TIER in the item data, each tier with the trophy's rarity. */
    public static int trophyColor(SkyblockDbApi db, TrophyProgress t) {
        return db.item(t.id() + "_" + TrophyGoals.BRONZE).map(i -> rarityColor(i.rarity())).orElse(WHITE);
    }

    public static int rarityColor(String rarity) {
        return RARITY_COLORS.getOrDefault(rarity, WHITE);
    }

    /** 1234567 as "1.23M", 12345 as "12.3k". */
    public static String coins(double value) {
        if (value >= 1e9) return String.format("%.2fB", value / 1e9);
        if (value >= 1e6) return String.format("%.2fM", value / 1e6);
        if (value >= 1e4) return String.format("%.1fk", value / 1e3);
        return String.format("%,.0f", value);
    }

    /** What it costs to buy now: lowest BIN, then the bazaar. Empty for what can't be bought. */
    private static OptionalDouble price(SkyblockDbApi db, String id) {
        OptionalDouble bin = db.price(id, PriceSource.LOWEST_BIN);
        return bin.isPresent() && bin.getAsDouble() > 0 ? bin : db.price(id, PriceSource.BAZAAR_BUY);
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

    /** Read again on the next use, so opening the screen picks up a resource pack's change. */
    public static void forgetResources() {
        power = null;
        eventNpcs = null;
    }

    /** Power per rarity, from {@code accessory_power.json}, so a resource pack can change it. */
    private static Map<String, Integer> power() {
        if (power == null) power = readMap("accessory_power.json", JsonElement::getAsInt);
        return power;
    }

    /** Shop NPC to the event it only sells during, from {@code accessory_events.json}. */
    private static Map<String, String> eventNpcs() {
        if (eventNpcs == null) eventNpcs = readMap("accessory_events.json", JsonElement::getAsString);
        return eventNpcs;
    }

    private static <T> Map<String, T> readMap(String file, Function<JsonElement, T> value) {
        Map<String, T> read = new HashMap<>();
        Identifier id = Identifier.fromNamespaceAndPath(SkyblockCollector.MOD_ID, file);
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(id)) {
            for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                read.put(e.getKey(), value.apply(e.getValue()));
            }
        } catch (IOException | RuntimeException e) {
            SkyblockCollector.LOGGER.warn("Could not read {}: {}", id, e.toString());
        }
        return read;
    }
}
