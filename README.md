# Gauntlet of Hell (NeoForge 1.21.1)

Build: JDK 21, then `./gradlew build` (jar ends up in build/libs/), or let the GitHub Action build it.

Craft: obsidian, netherite ingots and a nether star (see data/hellgauntlet/recipe), or `/give @s hellgauntlet:gauntlet_of_hell`.

Stats: 10000 durability, 5 attack damage, 1.8 attack speed, mending/sharpness/unbreaking allowed,
fire resistant, dropped copies survive explosions.

Controls
- Right click: shoot the orb (or the single laser in laser mode)
- Hold right click: continuous laser (laser mode, "Continuous laser")
- Shift + right click: next mode
- X: laser mode on/off (rebind in Controls under "Gauntlet of Hell")

Durability per use: orb charge 3 to 10 (more the longer you hold), Entity only 5; laser 10; continuous laser 10 every 2.5 seconds.

Normal orb: hold right click to charge (full after 8 seconds, BlastMode.FULL_CHARGE_TICKS), release to fire. Radius grows with the hold time from 0 (only the thing it touches) to 8 (MAX_RADIUS). Mobs that must die instead of being deleted: 150+ max health, or any entity type in the tag data/hellgauntlet/tags/entity_type/kill_not_delete.json (wither and #c:bosses are in it; add modded bosses there). Loot from those kills is protected from the void for 13 seconds.

Continuous laser on something it can't delete (bedrock, portal frames and so on): the beam stays on and its tip
vibrates in and out with sparks, instead of making a new ball that fades away every few ticks. It still makes one
ball the first time to clear whatever it can around that spot. Tune it in LaserBeamEntity.java (VIBRATE_AMPLITUDE,
STUCK_TICKS).
Tweak numbers in BlastMode.java and LaserMode.java.

Hell Skull structure (all data files, no code), find it with `/locate structure hellgauntlet:hellskull`
- Where: Nether, in basalt deltas and soul sand valley. Edit data/hellgauntlet/tags/worldgen/biome/has_structure/hellskull.json to change the biomes.
- How often: worldgen/structure_set/hellskull.json. Spacing 57 means about 2x as many placement attempts as the woodland mansion (spacing 80). Density goes with 1/spacing^2, so a bigger spacing makes it rarer.
- Placement: `HellSkullStructure.java` scans the terrain for open ground (a floor with `clearance` blocks of air above, roughly flat around) between `search_min_y` and `search_max_y` in worldgen/structure/hellskull.json. If a chunk has no such spot, no skull is made there.
- Look: data/hellgauntlet/structure/hellskull.nbt. Replace that file to change the building.
- Free space: the template is padded with air (5 blocks around, 7 above, nothing below), so when it lands inside a basalt pillar it carves a bubble around itself instead of being buried. If you rebuild the template, keep a margin of air blocks around it for the same effect.

Continuous laser: when you let go, the back end leaves the hand and flies after the front like water running off a tap. The cut-off piece keeps flying until it hits something and splashes there (STREAM_SPEED, MAX_FLIGHT in LaserBeamEntity.java).
Mobs with 150+ max health die normally when hit by the void (loot and XP drop); everything else is deleted. Change VoidBlasts.KILL_HEALTH to adjust.
