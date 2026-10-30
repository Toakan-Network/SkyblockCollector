package uk.toakan.tad.skyblockcollector;

import skyblockdb.api.TrophyProgress;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What is still missing for a trophy goal. A catch at the goal tier or higher meets the goal. */
public final class TrophyGoals {
    public static final String BRONZE = "BRONZE";
    public static final String SILVER = "SILVER";
    public static final String GOLD = "GOLD";
    public static final String DIAMOND = "DIAMOND";
    public static final List<String> TIERS = List.of(BRONZE, SILVER, GOLD, DIAMOND);
    static final String NOVICE = "novice_trophy_fisher";
    static final String ADEPT = "adept_trophy_fisher";
    static final String EXPERT = "expert_trophy_fisher";
    static final String MASTER = "master_trophy_fisher";
    private static final int NOVICE_KINDS = 15;

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

    /**
     * Trophy Fisher ranks earned, as named in {@code accessory_rank_rewards.json}. Novice is read as 15 kinds of fish:
     * the wiki says "15 Bronze tier or above Trophy Fish" without saying kinds or catches, and kinds never over-promises.
     */
    public static Set<String> fisherRanks(List<TrophyProgress> fish) {
        Set<String> ranks = new HashSet<>();
        if (fish.isEmpty()) return ranks;
        if (fish.size() - missing(fish, BRONZE).size() >= NOVICE_KINDS) ranks.add(NOVICE);
        if (missing(fish, SILVER).isEmpty()) ranks.add(ADEPT);
        if (missing(fish, GOLD).isEmpty()) ranks.add(EXPERT);
        if (fish.stream().allMatch(t -> TIERS.stream().allMatch(tier -> t.caught(tier) > 0))) ranks.add(MASTER);
        return ranks;
    }

    /** The goal after {@code goal}, wrapping round. */
    public static String next(String goal) {
        return TIERS.get((Math.max(0, TIERS.indexOf(goal)) + 1) % TIERS.size());
    }
}
