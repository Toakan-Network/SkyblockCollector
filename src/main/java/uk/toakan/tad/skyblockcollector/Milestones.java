package uk.toakan.tad.skyblockcollector;

import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import skyblockdb.api.SkyblockDbApi;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NPC milestones such as Miria's, which SkyblockAPI doesn't keep: read from the "Miria's Milestones" item while that NPC's
 * menu is open, and remembered per profile in {@code milestones.json}. Reads the menu only. Client thread.
 */
public final class Milestones {
    private static final Type TYPE = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
    private static final Pattern ITEM_NAME = Pattern.compile("(\\w+)'s Milestones");
    private static final Pattern LEVEL = Pattern.compile("Your Milestone: ([IVXLC]+)");
    private static final int CHECK_TICKS = 20;
    private static final List<String> ROMAN = List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X");
    private static Map<String, Map<String, Integer>> levels;
    private static int ticks;

    private Milestones() {}

    public static void tick(Minecraft client) {
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (!SkyblockDetector.isOnSkyblock() || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        String profile = SkyblockDbApi.get().flatMap(SkyblockDbApi::currentProfile).orElse(null);
        if (profile == null) return;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher name = ITEM_NAME.matcher(plain(stack.getHoverName()));
            if (!name.matches()) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            List<String> lines = new ArrayList<>();
            for (Component line : lore.lines()) lines.add(plain(line));
            int level = level(lines);
            if (level > 0) remember(profile, name.group(1).toLowerCase(Locale.ROOT), level);
        }
    }

    /** The NPC's milestone on the current profile; 0 when it hasn't been seen yet. */
    public static int level(SkyblockDbApi db, String npc) {
        String profile = db.currentProfile().orElse(null);
        if (profile == null) return 0;
        return levels().getOrDefault(profile, Map.of()).getOrDefault(npc, 0);
    }

    /** The level named in a milestone item's lore; 0 when there is none. */
    static int level(List<String> lore) {
        for (String line : lore) {
            Matcher m = LEVEL.matcher(line);
            if (m.find()) return ROMAN.indexOf(m.group(1)) + 1;
        }
        return 0;
    }

    private static String plain(Component text) {
        String stripped = ChatFormatting.stripFormatting(text.getString());
        return stripped == null ? "" : stripped.trim();
    }

    private static void remember(String profile, String npc, int level) {
        Map<String, Integer> forProfile = levels().computeIfAbsent(profile, p -> new HashMap<>());
        Integer old = forProfile.put(npc, level);
        if (old != null && old == level) return;
        // Copied now, so the writer thread never reads something the client thread is changing.
        Map<String, Map<String, Integer>> copy = new HashMap<>();
        levels().forEach((p, npcs) -> copy.put(p, new HashMap<>(npcs)));
        JsonFiles.writeAsync(file(), () -> copy);
    }

    /** Not a constant: making it touches the mod loader, which unit tests of this class don't have. */
    private static File file() {
        return new File(SkyblockCollector.DIR, "milestones.json");
    }

    private static Map<String, Map<String, Integer>> levels() {
        if (levels == null) {
            Map<String, Map<String, Integer>> loaded = JsonFiles.read(file(), TYPE);
            levels = loaded != null ? loaded : new HashMap<>();
        }
        return levels;
    }
}
