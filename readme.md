# SkyblockCollector

A Fabric client mod for Hypixel SkyBlock that tracks collections the in-game Collections menu doesn't cover: Trophy
Frogs, Trophy Fish and accessories. It is read-only and client-side: it never sends packets, chat or commands.

Open it with **J** (change it in Controls) or `/skyblockcollector`. Both only work on SkyBlock.

## Trophy Frogs and Trophy Fish
Pick a goal tier: Bronze, Silver, Gold or Diamond. Each tab keeps its own goal.
- **Missing only** lists each trophy you still need for the goal, with how to catch it.
- **Show all** lists every trophy with your catches per tier.

A catch at the goal tier or higher meets the goal, so a Gold Tree Frog counts towards a Silver goal.

Counts come from SkyblockAPI. If a tab says nothing is recorded, open the in-game trophy menu to update it.

## Accessories
Open your Accessory Bag once so the mod can see it.
- **What's next** lists missing accessories and upgrades, with the power each adds, ranked by coins per power.
  Items with no lowest BIN or bazaar price are listed after those.
- **Owned** lists your accessories with their power. Lower tiers of a family and copies show as not counted.

Only the best accessory of a family counts, and a recombobulated one counts one rarity higher.

## Known limitations
- Prices are the item's own price. An upgrade recipe that uses up the lower tier can cost less than shown.
- Suggestions include anything the item list calls an accessory, including ones you may not be able to get yet.
- The power total doesn't include Dungeon doubling.
- No item icons yet.

## Requirements
- Minecraft 26.2, Java 25
- Fabric Loader and Fabric API
- [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api)
- SkyblockDB 0.6.0 or newer

## Network use
On first opening the Accessories tab, at most once a day, the mod downloads `constants/misc.json` from the
NotEnoughUpdates repository on GitHub, for accessory upgrade lines. Only those lists are kept.

## Customising
- Catch hints and all text are in the lang file, so a resource pack can change or translate them.
- Power per rarity is `assets/skyblock-collector/accessory_power.json`, also replaceable by a pack.

## Building from source
Requires Java 25. `./gradlew build` puts the jar in `build/libs`.

## License
GPL-3.0-only (see `LICENSE`).
