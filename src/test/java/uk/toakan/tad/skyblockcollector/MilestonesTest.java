package uk.toakan.tad.skyblockcollector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MilestonesTest {
    @Test
    void readsTheLevelFromTheMenuItem() {
        assertEquals(4, Milestones.level(List.of("Your Milestone: IV", "", "Progress to Level V: 80.7%")));
        assertEquals(10, Milestones.level(List.of("Your Milestone: X")));
    }

    @Test
    void noLevelIsZero() {
        assertEquals(0, Milestones.level(List.of("Progress to Level V: 80.7%")));
        assertEquals(0, Milestones.level(List.of()));
    }
}
