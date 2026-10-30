package uk.toakan.tad.skyblockcollector;

import java.io.File;

/** Player settings, in settings.json. Changed on the collector screen. Client thread. */
public final class Settings {
    private static final File FILE = new File(SkyblockCollector.DIR, "settings.json");
    private static final int DEFAULT_HUD_X = 270;
    private static final int DEFAULT_HUD_Y = 40;
    private static final int DEFAULT_HUD_WIDTH = 200;
    private static final int DEFAULT_HUD_HEIGHT = 200;
    private static Settings instance;

    /** Goal tier for each trophy tab: BRONZE, SILVER, GOLD or DIAMOND. */
    public String frogGoal = TrophyGoals.BRONZE;
    public String fishGoal = TrophyGoals.BRONZE;
    /** Trophy tabs: every trophy with its counts instead of only what is missing. Accessories: owned instead of next. */
    public boolean showAll = false;
    /** Accessory suggestions on the screen and the HUD. Null (a value this version doesn't know) shows everything. */
    public AccessorySuggestions.Filter accessoryFilter = AccessorySuggestions.Filter.ALL;

    /** The HUD panel, and which sections it shows. */
    public boolean hud = true;
    public boolean hudFrogs = true;
    public boolean hudFish = true;
    public boolean hudAccessories = true;
    /** Rows per section before "+N more". */
    public int hudRows = 5;
    /** Panel position in GUI pixels, size in HUD units, scale in percent; see {@link CollectorHud#rect()}. */
    public int hudX = DEFAULT_HUD_X;
    public int hudY = DEFAULT_HUD_Y;
    public int hudWidth = DEFAULT_HUD_WIDTH;
    public int hudHeight = DEFAULT_HUD_HEIGHT;
    public int hudScale = 100;
    /** GUI-scaled screen size the position was saved at (0 = not recorded yet); the position follows the screen. */
    public int hudRefWidth = 0;
    public int hudRefHeight = 0;
    /** Panel look; the defaults match Shopping List's panel. */
    public int hudRadius = 0;
    public int hudBackground = 0x99271910;
    public int hudHeaderBackground = 0xCC2C4A1B;
    public int hudTitleColor = 0xFFFFB728;
    public int hudTextColor = 0xFFFFFFFF;

    public static Settings get() {
        if (instance == null) {
            Settings loaded = JsonFiles.read(FILE, Settings.class);
            instance = loaded != null ? loaded : new Settings();
        }
        return instance;
    }

    /** Move HUD's Reset: back to the starting place and size. */
    public static void resetHudRect() {
        Settings s = get();
        s.hudX = DEFAULT_HUD_X;
        s.hudY = DEFAULT_HUD_Y;
        s.hudWidth = DEFAULT_HUD_WIDTH;
        s.hudHeight = DEFAULT_HUD_HEIGHT;
        s.hudScale = 100;
        s.hudRefWidth = 0;
        s.hudRefHeight = 0;
        save();
    }

    public static void save() {
        // Copied now, so the writer thread never reads something the client thread is changing.
        Settings copy = JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(get()), Settings.class);
        JsonFiles.writeAsync(FILE, () -> copy);
    }
}
