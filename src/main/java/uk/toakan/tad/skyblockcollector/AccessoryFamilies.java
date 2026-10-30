package uk.toakan.tad.skyblockcollector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Which accessories are upgrades of each other, and which never count, from NotEnoughUpdates' repository
 * ({@code constants/misc.json}). Only those two lists are kept in {@code accessory_families.json}, and it is
 * downloaded at most once a day, the first time the accessories tab opens or the HUD's accessories section draws.
 */
public final class AccessoryFamilies {
    public static final AccessoryFamilies EMPTY = parse(new JsonObject());
    private static final URI URL = URI.create(
        "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/misc.json");
    private static final long MAX_AGE_MS = Duration.ofDays(1).toMillis();
    private static final String UPGRADES = "talisman_upgrades";
    private static final String IGNORED = "ignored_talisman";
    private static final String FETCHED_AT = "fetchedAt";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static volatile AccessoryFamilies current;

    private final Map<String, String> roots;
    private final Set<String> ignored;

    private AccessoryFamilies(Map<String, String> roots, Set<String> ignored) {
        this.roots = roots;
        this.ignored = ignored;
    }

    /** The loaded lists, or null until they are there. */
    public static AccessoryFamilies current() {
        return current;
    }

    /** Starts loading once per session, off the client thread. */
    public static void ensureLoaded() {
        if (STARTED.compareAndSet(false, true)) CompletableFuture.runAsync(AccessoryFamilies::load);
    }

    private static void load() {
        File file = new File(SkyblockCollector.DIR, "accessory_families.json");
        JsonObject cached = JsonFiles.read(file, JsonObject.class);
        if (cached != null) {
            current = parse(cached);
            JsonElement at = cached.get(FETCHED_AT);
            if (at != null && at.isJsonPrimitive() && System.currentTimeMillis() - at.getAsLong() < MAX_AGE_MS) return;
        }
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpRequest request = HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                SkyblockCollector.LOGGER.warn("Accessory families download failed: HTTP {}", response.statusCode());
                return;
            }
            JsonObject misc = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!misc.has(UPGRADES)) {
                SkyblockCollector.LOGGER.warn("Accessory families download has no {}", UPGRADES);
                return;
            }
            JsonObject kept = new JsonObject();
            kept.addProperty(FETCHED_AT, System.currentTimeMillis());
            kept.add(UPGRADES, misc.get(UPGRADES));
            if (misc.has(IGNORED)) kept.add(IGNORED, misc.get(IGNORED));
            current = parse(kept);
            JsonFiles.writeAsync(file, () -> kept);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            SkyblockCollector.LOGGER.warn("Accessory families download failed: {}", e.toString());
        }
    }

    /** Reads the repository's lists. {@code talisman_upgrades} maps an ID to the IDs it upgrades into. */
    static AccessoryFamilies parse(JsonObject json) {
        Map<String, String> parents = new HashMap<>();
        if (json.has(UPGRADES) && json.get(UPGRADES).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject(UPGRADES).entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                for (JsonElement upgrade : e.getValue().getAsJsonArray()) {
                    String a = find(parents, e.getKey());
                    String b = find(parents, upgrade.getAsString());
                    if (!a.equals(b)) parents.put(b, a);
                }
            }
        }
        Map<String, String> roots = new HashMap<>();
        for (String id : parents.keySet()) roots.put(id, find(parents, id));
        Set<String> ignored = new HashSet<>();
        if (json.has(IGNORED) && json.get(IGNORED).isJsonArray()) {
            JsonArray list = json.getAsJsonArray(IGNORED);
            for (JsonElement id : list) ignored.add(id.getAsString());
        }
        return new AccessoryFamilies(roots, ignored);
    }

    private static String find(Map<String, String> parents, String id) {
        String at = id;
        while (parents.containsKey(at)) at = parents.get(at);
        return at;
    }

    /** One name for the whole upgrade line; an accessory in no line is its own family. */
    public String family(String id) {
        return roots.getOrDefault(id, id);
    }

    public boolean ignored(String id) {
        return ignored.contains(id);
    }
}
