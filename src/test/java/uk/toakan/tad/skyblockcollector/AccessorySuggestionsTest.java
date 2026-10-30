package uk.toakan.tad.skyblockcollector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import skyblockdb.api.AccessoryItem;
import skyblockdb.api.Recipe;
import skyblockdb.api.ReferenceItem;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessorySuggestionsTest {
    private static final Map<String, Integer> POWER = Map.of("COMMON", 3, "UNCOMMON", 5, "RARE", 8, "EPIC", 12);
    private static final JsonObject MISC = JsonParser.parseString("""
        {"talisman_upgrades": {"A_TALISMAN": ["A_RING", "A_ARTIFACT"], "A_RING": ["A_ARTIFACT"]},
         "ignored_talisman": ["IGNORED"]}""").getAsJsonObject();
    private static final AccessoryFamilies FAMILIES = AccessoryFamilies.parse(MISC);

    private static ReferenceItem item(String id, String rarity) {
        return new ReferenceItem(id, id, rarity, "ACCESSORY", "repo", null);
    }

    private static Map<String, ReferenceItem> items(ReferenceItem... list) {
        Map<String, ReferenceItem> out = new LinkedHashMap<>();
        for (ReferenceItem i : list) out.put(i.sbId(), i);
        return out;
    }

    private static AccessoryItem bag(String id, boolean recomb) {
        return new AccessoryItem(id, "0.0", recomb);
    }

    private static AccessorySuggestions.Result build(List<AccessoryItem> bag, Map<String, ReferenceItem> items,
                                                     Map<String, Double> prices) {
        return build(bag, items, prices, Map.of(), AccessorySuggestions.Stock.NONE);
    }

    private static AccessorySuggestions.Result build(List<AccessoryItem> bag, Map<String, ReferenceItem> items,
                                                     Map<String, Double> prices, Map<String, Recipe> recipes,
                                                     AccessorySuggestions.Stock stock) {
        return AccessorySuggestions.build(bag, items, FAMILIES, POWER, id -> prices.containsKey(id)
            ? OptionalDouble.of(prices.get(id)) : OptionalDouble.empty(),
            id -> recipes.containsKey(id) ? List.of(recipes.get(id)) : List.of(), stock, Map.of("EVENT_NPC", "festival"));
    }

    @Test
    void forgeRecipesAreMarked() {
        var forged = new Recipe("forge", "B", 1, Map.of("IRON", 1), 0, 60, "");
        var r = build(List.of(), items(item("B", "RARE"), item("C", "RARE")), Map.of(),
            Map.of("B", forged, "C", recipe("C", Map.of("IRON", 1), 0)),
            new AccessorySuggestions.Stock(Map.of("IRON", 1L), 0, 0));
        assertTrue(r.canMake().stream().filter(s -> s.id().equals("B")).findFirst().orElseThrow().forge());
        assertFalse(r.canMake().stream().filter(s -> s.id().equals("C")).findFirst().orElseThrow().forge());
    }

    @Test
    void npcShopRecipesAreMarkedAsPurchases() {
        var shop = new Recipe("shop", "B", 1, Map.of("IRON", 1), 0, 0, "SOME_NPC");
        var r = build(List.of(), items(item("B", "RARE"), item("C", "RARE")), Map.of(),
            Map.of("B", shop, "C", recipe("C", Map.of("IRON", 1), 0)),
            new AccessorySuggestions.Stock(Map.of("IRON", 1L), 0, 0));
        assertTrue(r.canMake().stream().filter(s -> s.id().equals("B")).findFirst().orElseThrow().shop());
        assertFalse(r.canMake().stream().filter(s -> s.id().equals("C")).findFirst().orElseThrow().shop());
    }

    @Test
    void filtersKeepOnlyCraftOrBuy() {
        var shop = new Recipe("shop", "NPC_ITEM", 1, Map.of("IRON", 1), 0, 0, "SOME_NPC");
        var r = build(List.of(bag("OWNED", false)), items(item("OWNED", "COMMON"), item("NPC_ITEM", "RARE"), item("CRAFTED", "RARE"),
                item("AUCTION", "RARE"), item("DEAR", "RARE"), item("NO_PRICE", "RARE")),
            Map.of("AUCTION", 10.0, "DEAR", 1000.0),
            Map.of("NPC_ITEM", shop, "CRAFTED", recipe("CRAFTED", Map.of("IRON", 1), 0)),
            new AccessorySuggestions.Stock(Map.of("IRON", 1L), 100, 0));

        var craft = r.filtered(AccessorySuggestions.Filter.CRAFT);
        assertEquals(List.of("CRAFTED"), ids(craft.canMake()));
        assertTrue(craft.canBuy().isEmpty() && craft.priced().isEmpty() && craft.unpriced().isEmpty());
        assertEquals(1, craft.owned().size());

        var buy = r.filtered(AccessorySuggestions.Filter.BUY);
        assertEquals(List.of("NPC_ITEM"), ids(buy.canMake()));
        assertEquals(List.of("AUCTION"), ids(buy.canBuy()));
        assertTrue(buy.priced().isEmpty() && buy.unpriced().isEmpty());

        assertEquals(r, r.filtered(AccessorySuggestions.Filter.ALL));
        assertEquals(r, r.filtered(null));
    }

    @Test
    void eventShopIsNeverCanMake() {
        var shop = new Recipe("shop", "B", 1, Map.of("TREAT", 1), 0, 0, "EVENT_NPC");
        var r = build(List.of(), items(item("B", "RARE")), Map.of(), Map.of("B", shop),
            new AccessorySuggestions.Stock(Map.of("TREAT", 1L), 0, 0));
        assertTrue(r.canMake().isEmpty());
        assertEquals(List.of("B"), ids(r.unpriced()));
        assertEquals("festival", r.unpriced().getFirst().event());
    }

    @Test
    void sameFamilyAndGainIsOneRowKeepingTheEasiest() {
        var misc = JsonParser.parseString("{\"talisman_upgrades\": {\"CASE_A\": [\"CASE_B\", \"CASE_C\"]}}").getAsJsonObject();
        var r = AccessorySuggestions.build(List.of(),
            items(item("CASE_A", "RARE"), item("CASE_B", "RARE"), item("CASE_C", "RARE")),
            AccessoryFamilies.parse(misc), POWER, id -> id.equals("CASE_A") ? OptionalDouble.of(50) : OptionalDouble.empty(),
            id -> id.equals("CASE_B") ? List.of(recipe("CASE_B", Map.of("IRON", 1), 0)) : List.of(),
            new AccessorySuggestions.Stock(Map.of("IRON", 1L), 0, 0), Map.of());
        assertEquals(List.of("CASE_B"), ids(r.canMake()));
        assertTrue(r.priced().isEmpty());
        assertTrue(r.unpriced().isEmpty());
    }

    @Test
    void tiersOfAFamilyWithDifferentGainsAllStay() {
        var r = build(List.of(), items(item("A_TALISMAN", "COMMON"), item("A_RING", "UNCOMMON")),
            Map.of("A_TALISMAN", 1.0, "A_RING", 2.0));
        assertEquals(2, r.priced().size());
    }

    private static Recipe recipe(String output, Map<String, Integer> inputs, long coins) {
        return new Recipe("crafting", output, 1, inputs, coins, 0, "");
    }

    private static List<String> ids(List<AccessorySuggestions.Suggestion> list) {
        return list.stream().map(AccessorySuggestions.Suggestion::id).toList();
    }

    @Test
    void allInputsHeldCanBeMade() {
        var r = build(List.of(), items(item("B", "RARE")), Map.of("B", 800.0),
            Map.of("B", recipe("B", Map.of("IRON", 9), 0)),
            new AccessorySuggestions.Stock(Map.of("IRON", 9L), 0, 0));
        assertEquals(List.of("B"), ids(r.canMake()));
        assertTrue(r.priced().isEmpty());
    }

    @Test
    void oneInputShortCannotBeMade() {
        var r = build(List.of(), items(item("B", "RARE")), Map.of("B", 800.0),
            Map.of("B", recipe("B", Map.of("IRON", 9, "GOLD", 1), 0)),
            new AccessorySuggestions.Stock(Map.of("IRON", 9L), 0, 0));
        assertTrue(r.canMake().isEmpty());
        assertEquals(List.of("B"), ids(r.priced()));
    }

    @Test
    void upgradeCanUseTheLowerTierInTheBag() {
        var r = build(List.of(bag("A_TALISMAN", false)), items(item("A_TALISMAN", "COMMON"), item("A_RING", "UNCOMMON")),
            Map.of(), Map.of("A_RING", recipe("A_RING", Map.of("A_TALISMAN", 1), 0)),
            new AccessorySuggestions.Stock(Map.of("A_TALISMAN", 1L), 0, 0));
        assertEquals(List.of("A_RING"), ids(r.canMake()));
        assertEquals(2, r.canMake().getFirst().gain());
    }

    @Test
    void noGainIsLeftOutEvenWhenMakeable() {
        var r = build(List.of(bag("A_RING", false)), items(item("A_TALISMAN", "COMMON"), item("A_RING", "UNCOMMON")),
            Map.of(), Map.of("A_TALISMAN", recipe("A_TALISMAN", Map.of("IRON", 1), 0)),
            new AccessorySuggestions.Stock(Map.of("IRON", 1L), 0, 0));
        assertTrue(r.canMake().isEmpty());
    }

    @Test
    void canBuyOnlyWithinCoins() {
        var r = build(List.of(), items(item("CHEAP", "COMMON"), item("DEAR", "COMMON")),
            Map.of("CHEAP", 100.0, "DEAR", 101.0), Map.of(), new AccessorySuggestions.Stock(Map.of(), 100, 0));
        assertEquals(List.of("CHEAP"), ids(r.canBuy()));
        assertEquals(List.of("DEAR"), ids(r.priced()));
    }

    @Test
    void currencyInputs() {
        var coinsAndBits = recipe("B", Map.of("SKYBLOCK_COIN", 50, "SKYBLOCK_BIT", 10), 50);
        assertTrue(new AccessorySuggestions.Stock(Map.of(), 100, 10).canMake(coinsAndBits));
        assertFalse(new AccessorySuggestions.Stock(Map.of(), 99, 10).canMake(coinsAndBits));
        assertFalse(new AccessorySuggestions.Stock(Map.of(), 100, 9).canMake(coinsAndBits));
        // Currencies without a match are never held, even with plenty of everything else.
        assertFalse(new AccessorySuggestions.Stock(Map.of(), 1e9, 1e9).canMake(recipe("B", Map.of("SKYBLOCK_COPPER", 1), 0)));
    }

    @Test
    void onlyBestOfFamilyCounts() {
        var r = build(List.of(bag("A_TALISMAN", false), bag("A_RING", false)),
            items(item("A_TALISMAN", "COMMON"), item("A_RING", "UNCOMMON")), Map.of());
        assertEquals(5, r.totalPower());
        assertTrue(r.owned().getFirst().counted());
        assertFalse(r.owned().getLast().counted());
    }

    @Test
    void duplicatesDoNotStack() {
        var r = build(List.of(bag("B", false), bag("B", false)), items(item("B", "RARE")), Map.of());
        assertEquals(8, r.totalPower());
    }

    @Test
    void recombobulatedCountsOneRarityHigher() {
        var r = build(List.of(bag("B", true)), items(item("B", "RARE")), Map.of());
        assertEquals(12, r.totalPower());
        assertEquals("VERY_SPECIAL", AccessorySuggestions.effectiveRarity("Special", true));
        assertEquals("DIVINE", AccessorySuggestions.effectiveRarity("DIVINE", true));
    }

    @Test
    void upgradeGainIsAboveCurrentFamilyBest() {
        var r = build(List.of(bag("A_RING", false)),
            items(item("A_TALISMAN", "COMMON"), item("A_RING", "UNCOMMON"), item("A_ARTIFACT", "EPIC")),
            Map.of("A_ARTIFACT", 700.0, "A_TALISMAN", 1.0));
        assertEquals(1, r.priced().size());
        assertEquals("A_ARTIFACT", r.priced().getFirst().id());
        assertEquals(7, r.priced().getFirst().gain());
        assertEquals(100.0, r.priced().getFirst().coinsPerPower());
    }

    @Test
    void rankedByCoinsPerPowerAndUnpricedApart() {
        var r = build(List.of(), items(item("DEAR", "COMMON"), item("CHEAP", "EPIC"), item("NO_PRICE", "RARE")),
            Map.of("DEAR", 300.0, "CHEAP", 120.0));
        assertEquals(List.of("CHEAP", "DEAR"), r.priced().stream().map(AccessorySuggestions.Suggestion::id).toList());
        assertEquals("NO_PRICE", r.unpriced().getFirst().id());
    }

    @Test
    void ignoredRetiredAndOtherCategoriesAreSkipped() {
        var retired = new ReferenceItem("OLD", "OLD", "EPIC", "ACCESSORY", "repo", Instant.EPOCH);
        var sword = new ReferenceItem("SWORD", "SWORD", "EPIC", "SWORD", "repo", null);
        var r = build(List.of(), items(item("IGNORED", "EPIC"), retired, sword), Map.of());
        assertTrue(r.priced().isEmpty());
        assertTrue(r.unpriced().isEmpty());
    }

    @Test
    void emptyFamiliesTreatEachAccessoryAlone() {
        assertEquals("X", AccessoryFamilies.EMPTY.family("X"));
        assertFalse(AccessoryFamilies.EMPTY.ignored("IGNORED"));
    }
}
