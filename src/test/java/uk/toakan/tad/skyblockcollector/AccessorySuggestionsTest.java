package uk.toakan.tad.skyblockcollector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import skyblockdb.api.AccessoryItem;
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
        return AccessorySuggestions.build(bag, items, FAMILIES, POWER, id -> prices.containsKey(id)
            ? OptionalDouble.of(prices.get(id)) : OptionalDouble.empty());
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
