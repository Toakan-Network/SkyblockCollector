package uk.toakan.tad.skyblockcollector;

import skyblockdb.api.AccessoryItem;
import skyblockdb.api.Recipe;
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
    private static final String FORGE = "forge";
    private static final String SHOP = "shop";
    private static final String COIN_INPUT = "SKYBLOCK_COIN";
    private static final String BIT_INPUT = "SKYBLOCK_BIT";

    /** An owned accessory. {@code counted} is false when a better one of its family, or a copy, is what counts. */
    public record Owned(String id, String name, String rarity, int power, boolean counted) {}

    /**
     * Something to get. {@code price} is NaN when unknown. {@code forge}: it can be made, but only in the Forge.
     * {@code shop}: the same, but only by buying it from an NPC. {@code needs}: the event or rank that is the reason it
     * can't be got now, else empty.
     */
    public record Suggestion(String id, String name, String rarity, int gain, double price, boolean forge, boolean shop,
                             String needs) {
        public double coinsPerPower() {
            return price / gain;
        }
    }

    /**
     * What the player holds, as of when each container was last open. {@code coins} is purse plus bank. Counts come
     * from every container, the Accessory Bag included, so an upgrade can use the lower tier in the bag.
     */
    public record Stock(Map<String, Long> held, double coins, double bits) {
        public static final Stock NONE = new Stock(Map.of(), 0, 0);

        /** Each input is checked alone, so two suggestions can rely on the same items. */
        boolean canMake(Recipe recipe) {
            if (recipe.inputs().isEmpty()) return false;
            long coinCost = recipe.coins();
            for (Map.Entry<String, Integer> input : recipe.inputs().entrySet()) {
                switch (input.getKey()) {
                    case COIN_INPUT -> coinCost += input.getValue();
                    case BIT_INPUT -> {
                        if (bits < input.getValue()) return false;
                    }
                    // Other currencies have no reliable match here, so they count as not held.
                    default -> {
                        if (held.getOrDefault(input.getKey(), 0L) < input.getValue()) return false;
                    }
                }
            }
            return coins >= coinCost;
        }
    }

    /**
     * {@code canMake}: every input of a recipe is held. {@code canBuy}: has a price within the coins held.
     * {@code priced} and {@code unpriced} hold the rest. A suggestion is in one list only.
     */
    public record Result(int totalPower, List<Owned> owned, List<Suggestion> canMake, List<Suggestion> canBuy,
                         List<Suggestion> priced, List<Suggestion> unpriced) {
        /** Only what the filter asks for; what is owned stays. */
        public Result filtered(Filter filter) {
            if (filter == Filter.CRAFT) {
                return new Result(totalPower, owned, canMake.stream().filter(s -> !s.shop()).toList(), List.of(), List.of(), List.of());
            }
            if (filter == Filter.BUY) {
                return new Result(totalPower, owned, canMake.stream().filter(Suggestion::shop).toList(), canBuy, List.of(), List.of());
            }
            return this;
        }
    }

    /** Which suggestions to show: everything, what can be crafted now, or what can be bought now (NPC, auction, bazaar). */
    public enum Filter {
        ALL, CRAFT, BUY;

        public Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private AccessorySuggestions() {}

    public static Result build(List<AccessoryItem> bag, Map<String, ReferenceItem> items, AccessoryFamilies families,
                               Map<String, Integer> power, Function<String, OptionalDouble> price,
                               Function<String, List<Recipe>> recipes, Stock stock, Map<String, String> eventNpcs,
                               Map<String, String> locked) {
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

        List<Suggestion> canMake = new ArrayList<>();
        List<Suggestion> canBuy = new ArrayList<>();
        List<Suggestion> priced = new ArrayList<>();
        List<Suggestion> unpriced = new ArrayList<>();
        for (ReferenceItem item : items.values()) {
            if (!ACCESSORY.equals(item.category()) || item.retired()) continue;
            if (ownedIds.contains(item.sbId()) || families.ignored(item.sbId())) continue;
            String rarity = normalize(item.rarity());
            int gain = power.getOrDefault(rarity, 0) - best.getOrDefault(families.family(item.sbId()), 0);
            if (gain <= 0) continue;
            OptionalDouble cost = price.apply(item.sbId());
            boolean hasPrice = cost.isPresent() && cost.getAsDouble() > 0;
            // Whether an event is running can't be read, so a shop that only opens for one never counts as can make.
            List<Recipe> all = recipes.apply(item.sbId());
            // A locked accessory is a reward: its shop only replaces a lost one, so nothing about it can be bought yet.
            String lock = locked.get(item.sbId());
            List<Recipe> makeable = lock != null ? List.of()
                : all.stream().filter(r -> !eventNpcs.containsKey(r.npc())).filter(stock::canMake).toList();
            boolean forge = !makeable.isEmpty() && makeable.stream().allMatch(r -> FORGE.equals(r.type()));
            boolean shop = !makeable.isEmpty() && makeable.stream().allMatch(r -> SHOP.equals(r.type()));
            String needs = lock != null ? lock : !makeable.isEmpty() ? "" : all.stream()
                .map(r -> eventNpcs.getOrDefault(r.npc(), "")).filter(e -> !e.isEmpty()).findFirst().orElse("");
            Suggestion suggestion = new Suggestion(item.sbId(), item.name(), rarity, gain,
                hasPrice ? cost.getAsDouble() : Double.NaN, forge, shop, needs);
            if (!makeable.isEmpty()) canMake.add(suggestion);
            else if (lock == null && hasPrice && cost.getAsDouble() <= stock.coins()) canBuy.add(suggestion);
            else if (hasPrice) priced.add(suggestion);
            else unpriced.add(suggestion);
        }
        // Double.compare puts NaN last, so what has no price follows what has one.
        Comparator<Suggestion> byCoinsPerPower = Comparator.comparingDouble(Suggestion::coinsPerPower)
            .thenComparing(Suggestion::name);
        canMake.sort(byCoinsPerPower);
        canBuy.sort(byCoinsPerPower);
        priced.sort(byCoinsPerPower);
        unpriced.sort(Comparator.comparingInt(Suggestion::gain).reversed().thenComparing(Suggestion::name));
        // Best group first, so of the options that are worth the same the one kept is the easiest to get.
        Set<String> seen = new HashSet<>();
        return new Result(total, owned, firstOfEach(canMake, families, seen), firstOfEach(canBuy, families, seen),
            firstOfEach(priced, families, seen), firstOfEach(unpriced, families, seen));
    }

    /**
     * One row per family and power gain. Only one accessory of a family counts, so eight Abicases that each add the
     * same power are one choice, while the tiers of an upgrade line add different power and all stay.
     */
    private static List<Suggestion> firstOfEach(List<Suggestion> list, AccessoryFamilies families, Set<String> seen) {
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : list) {
            if (seen.add(families.family(s.id()) + "|" + s.gain())) out.add(s);
        }
        return out;
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
