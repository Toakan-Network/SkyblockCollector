package uk.toakan.tad.skyblockcollector;

import skyblockdb.api.TrophyProgress;

import java.util.ArrayList;
import java.util.List;

/** What is still missing for a trophy goal. A catch at the goal tier or higher meets the goal. */
public final class TrophyGoals {
    public static final String BRONZE = "BRONZE";
    public static final String SILVER = "SILVER";
    public static final String GOLD = "GOLD";
    public static final String DIAMOND = "DIAMOND";
    public static final List<String> TIERS = List.of(BRONZE, SILVER, GOLD, DIAMOND);

    private TrophyGoals() {}

    public static boolean met(TrophyProgress trophy, String goal) {
        int from = Math.max(0, TIERS.indexOf(goal));
        for (String tier : TIERS.subList(from, TIERS.size())) {
            if (trophy.caught(tier) > 0) return true;
        }
        return false;
    }

    public static List<TrophyProgress> missing(List<TrophyProgress> trophies, String goal) {
        List<TrophyProgress> out = new ArrayList<>();
        for (TrophyProgress trophy : trophies) {
            if (!met(trophy, goal)) out.add(trophy);
        }
        return out;
    }

    /** True when no trophy has a catch at any tier, which usually means its menu hasn't been opened yet. */
    public static boolean nothingRecorded(List<TrophyProgress> trophies) {
        return trophies.stream().allMatch(t -> t.caught().values().stream().allMatch(n -> n == 0));
    }

    /** The goal after {@code goal}, wrapping round. */
    public static String next(String goal) {
        return TIERS.get((Math.max(0, TIERS.indexOf(goal)) + 1) % TIERS.size());
    }
}
