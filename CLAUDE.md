# SkyblockCollector

Fabric client mod (mod id `skyblock-collector`, Java package `uk.toakan.tad.skyblockcollector`) for Hypixel SkyBlock:
tracks collections the game's Collections menu doesn't cover. Shared rules (commits, versioning, Hypixel rules,
resource light, 26.2 + 26.3 from one jar) are in the parent `Games\Minecraft\CLAUDE.md`. This file only has what
differs.

## Design
- All SkyBlock data comes from SkyblockDB's API (`libs/skyblock-db-api-<ver>.jar`). Never call SkyblockAPI from here:
  it would tie the jar to one Minecraft version. Missing data goes into SkyblockDB's API first.
- `trophyFrogs()`, `trophyFish()` and `accessories()` read live and are client thread only.
- `TrophyGoals`, `AccessorySuggestions` and `HudRows` are pure logic with unit tests; `CollectorScreen` and `CollectorHud`
  only draw, and read shared values through `CollectorData`.
- `CollectorHud` works rows out at most once a second on the client tick; drawing only reads the finished list.
- Huddable is optional: only `compat.HuddableCompat` may touch its API (stub in `src/huddableStub`, left out of the jar).
- A catch at the goal tier or higher meets a trophy goal.
- Accessory families come from NEU's `constants/misc.json`, at most once a day, on first opening the Accessories tab.
  Only `talisman_upgrades` and `ignored_talisman` are kept (`config/skyblock-collector/accessory_families.json`).
- Can make now checks direct recipe inputs against `totals()`, purse + bank and bits. Other currency inputs count as
  not held, and unlocks aren't checked (no collection levels in the API).
- Whether an event is running can't be read, so shops in `accessory_events.json` never count as can make.
- Pack-changeable data: catch hints are lang keys `skyblock-collector.hint.<trophy id>`, power per rarity is
  `assets/skyblock-collector/accessory_power.json`.
- No item icons: SkyblockDB's API is plain Java, with no `ItemStack`. Names use rarity colours.
