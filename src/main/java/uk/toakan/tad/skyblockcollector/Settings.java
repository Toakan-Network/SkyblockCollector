package uk.toakan.tad.skyblockcollector;

import java.io.File;

/** Player settings, in settings.json. Changed on the collector screen. Client thread. */
public final class Settings {
    private static final File FILE = new File(SkyblockCollector.DIR, "settings.json");
    private static Settings instance;

    /** Goal tier for each trophy tab: BRONZE, SILVER, GOLD or DIAMOND. */
    public String frogGoal = TrophyGoals.BRONZE;
    public String fishGoal = TrophyGoals.BRONZE;
    /** Trophy tabs: every trophy with its counts instead of only what is missing. Accessories: owned instead of next. */
    public boolean showAll = false;

    public static Settings get() {
        if (instance == null) {
            Settings loaded = JsonFiles.read(FILE, Settings.class);
            instance = loaded != null ? loaded : new Settings();
        }
        return instance;
    }

    public static void save() {
        // Copied now, so the writer thread never reads something the client thread is changing.
        Settings copy = JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(get()), Settings.class);
        JsonFiles.writeAsync(FILE, () -> copy);
    }
}
