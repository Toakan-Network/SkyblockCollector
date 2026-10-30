package uk.toakan.tad.skyblockcollector;

import skyblockdb.api.AccessoryItem;
import skyblockdb.api.ReferenceItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;

/**
 * Accessory power of the bag, and what to get next. Only the best accessory of a family counts, a copy adds nothing,
 * and a recombobulated accessory counts one rarity higher. Suggestions are ranked by coins per power gained; the price
 * is the item's own, so an upgrade recipe that uses up the lower tier can make it cheaper than shown.
 */
public final class AccessorySuggestions {
    /** Recombobulating moves an accessory one step along these. */
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE");
    private static final List<String> SPECIAL_RARITIES = List.of("SPECIAL", "VERY_SPECIAL");
    private static final String ACCESSORY = "ACCESSORY";

    /** An owned accessory. {@code counted} is false when a better one of its family, or a copy, is what counts. */
    public record Owned(String id, String name, String rarity, int power, boolean counted) {}

    /** Something to get. {@code price} is NaN when unknown. */
    public record Suggestion(String id, String name, String rarity, int gain, double price) {
        public double coinsPerPower() {
            return price / gain;
        }
    }

    public record Result(int totalPower, List<Owned> owned, List<Suggestion> priced, List<Suggestion> unpriced) {}

    private AccessorySuggestions() {}

    public static Result build(List<AccessoryItem> bag, Map<String, ReferenceItem> items, AccessoryFamilies families,
                               Map<String, Integer> power, Function<String, OptionalDouble> price) {
        List<Owned> owned = new ArrayList<>();
        Map<String, Integer> best = new HashMap<>();
        Set<String> ownedIds = new HashSet<>();
        for (AccessoryItem item : bag) {
            ReferenceItem info = items.get(item.sbId());
            String rarity = effectiveRarity(info == null ? "" : info.rarity(), item.recombobulated());
            int value = power.getOrDefault(rarity, 0);
            String family = families.family(item.sbId());
            if (!families.ignored(item.sbId())) best.merge(family, value, Math::max);
            ownedIds.add(item.sbId());
            owned.add(new Owned(item.sbId(), info == null ? item.sbId() : info.name(), rarity, value, false));
        }
        // Each family counts once, on its first accessory of the best power.
        Set<String> countedFamilies = new HashSet<>();
        int total = 0;
        for (int i = 0; i < owned.size(); i++) {
            Owned o = owned.get(i);
            String family = families.family(o.id());
            if (!families.ignored(o.id()) && o.power() == best.get(family) && countedFamilies.add(family)) {
                total += o.power();
                owned.set(i, new Owned(o.id(), o.name(), o.rarity(), o.power(), true));
            }
        }
        owned.sort(Comparator.comparingInt(Owned::power).reversed().thenComparing(Owned::name));

        List<Suggestion> priced = new ArrayList<>();
        List<Suggestion> unpriced = new ArrayList<>();
        for (ReferenceItem item : items.values()) {
            if (!ACCESSORY.equals(item.category()) || item.retired()) continue;
            if (ownedIds.contains(item.sbId()) || families.ignored(item.sbId())) continue;
            String rarity = normalize(item.rarity());
            int gain = power.getOrDefault(rarity, 0) - best.getOrDefault(families.family(item.sbId()), 0);
            if (gain <= 0) continue;
            OptionalDouble cost = price.apply(item.sbId());
            if (cost.isPresent() && cost.getAsDouble() > 0) {
                priced.add(new Suggestion(item.sbId(), item.name(), rarity, gain, cost.getAsDouble()));
            } else {
                unpriced.add(new Suggestion(item.sbId(), item.name(), rarity, gain, Double.NaN));
            }
        }
        priced.sort(Comparator.comparingDouble(Suggestion::coinsPerPower).thenComparing(Suggestion::name));
        unpriced.sort(Comparator.comparingInt(Suggestion::gain).reversed().thenComparing(Suggestion::name));
        return new Result(total, owned, priced, unpriced);
    }

    static String effectiveRarity(String rarity, boolean recombobulated) {
        String base = normalize(rarity);
        if (!recombobulated) return base;
        for (List<String> order : List.of(RARITIES, SPECIAL_RARITIES)) {
            int at = order.indexOf(base);
            if (at >= 0 && at + 1 < order.size()) return order.get(at + 1);
        }
        return base;
    }

    private static String normalize(String rarity) {
        return rarity.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
