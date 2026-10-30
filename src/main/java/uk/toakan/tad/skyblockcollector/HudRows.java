package uk.toakan.tad.skyblockcollector;

import java.util.List;

/** How many rows of a HUD section are drawn, and how many are left over for its "+N more" line. */
public final class HudRows {
    public record Limited<T>(List<T> shown, int more) {}

    private HudRows() {}

    /** The first {@code max} rows, at least one. */
    public static <T> Limited<T> limit(List<T> rows, int max) {
        int shown = Math.min(rows.size(), Math.max(1, max));
        return new Limited<>(List.copyOf(rows.subList(0, shown)), rows.size() - shown);
    }
}
