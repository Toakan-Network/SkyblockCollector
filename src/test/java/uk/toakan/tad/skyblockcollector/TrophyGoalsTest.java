package uk.toakan.tad.skyblockcollector;

import org.junit.jupiter.api.Test;
import skyblockdb.api.TrophyProgress;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophyGoalsTest {
    private static TrophyProgress trophy(Map<String, Integer> caught) {
        return new TrophyProgress("X", "X", caught);
    }

    private static List<TrophyProgress> fish(int count, Map<String, Integer> caught) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> new TrophyProgress("F" + i, "F" + i, caught)).toList();
    }

    @Test
    void fisherRanks() {
        assertTrue(TrophyGoals.fisherRanks(List.of()).isEmpty());
        assertTrue(TrophyGoals.fisherRanks(fish(14, Map.of("BRONZE", 1))).isEmpty());
        assertEquals(java.util.Set.of(TrophyGoals.NOVICE),
            TrophyGoals.fisherRanks(java.util.stream.Stream.concat(fish(16, Map.of("SILVER", 1)).stream(), fish(2, Map.of()).stream()).toList()));
        assertEquals(java.util.Set.of(TrophyGoals.NOVICE, TrophyGoals.ADEPT), TrophyGoals.fisherRanks(fish(18, Map.of("SILVER", 1))));
        assertEquals(java.util.Set.of(TrophyGoals.NOVICE, TrophyGoals.ADEPT, TrophyGoals.EXPERT, TrophyGoals.MASTER),
            TrophyGoals.fisherRanks(fish(18, Map.of("BRONZE", 1, "SILVER", 1, "GOLD", 1, "DIAMOND", 1))));
    }

    @Test
    void nothingRecordedOnlyWithoutCatches() {
        assertTrue(TrophyGoals.nothingRecorded(List.of(trophy(Map.of()), trophy(Map.of("GOLD", 0)))));
        assertFalse(TrophyGoals.nothingRecorded(List.of(trophy(Map.of()), trophy(Map.of("BRONZE", 1)))));
    }

    @Test
    void hudRowsLimitAndCountTheRest() {
        assertEquals(new HudRows.Limited<>(List.of(1, 2), 1), HudRows.limit(List.of(1, 2, 3), 2));
        assertEquals(new HudRows.Limited<>(List.of(1), 0), HudRows.limit(List.of(1), 5));
        assertEquals(new HudRows.Limited<>(List.of(1), 1), HudRows.limit(List.of(1, 2), 0));
        assertEquals(new HudRows.Limited<Integer>(List.of(), 0), HudRows.limit(List.<Integer>of(), 5));
    }

    @Test
    void higherTierMeetsLowerGoal() {
        assertTrue(TrophyGoals.met(trophy(Map.of("GOLD", 1)), TrophyGoals.SILVER));
    }

    @Test
    void lowerTierDoesNotMeetHigherGoal() {
        assertFalse(TrophyGoals.met(trophy(Map.of("BRONZE", 95)), TrophyGoals.SILVER));
    }

    @Test
    void zeroCountIsNotACatch() {
        assertFalse(TrophyGoals.met(trophy(Map.of("DIAMOND", 0)), TrophyGoals.BRONZE));
    }

    @Test
    void missingKeepsOnlyUnmet() {
        TrophyProgress met = new TrophyProgress("A", "A", Map.of("SILVER", 2));
        TrophyProgress unmet = new TrophyProgress("B", "B", Map.of());
        assertEquals(List.of(unmet), TrophyGoals.missing(List.of(met, unmet), TrophyGoals.SILVER));
    }

    @Test
    void nextWrapsRound() {
        assertEquals(TrophyGoals.BRONZE, TrophyGoals.next(TrophyGoals.DIAMOND));
    }
}
