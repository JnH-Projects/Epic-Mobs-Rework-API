# Examples

Seven complete plugins, smallest first. Every one compiles against the API jar in
[`releases/`](../releases/), and every one is commented with the reasoning rather than only
the mechanics.

They are meant to be **read in order and then copied**. Each file's javadoc header carries
the `plugin.yml` that goes with it.

| | Example | Reads | Writes | Needs Premium |
| --- | --- | --- | --- | --- |
| 1 | [QuickStartPlugin](src/main/java/com/example/epicmobs/QuickStartPlugin.java) | the provider, spawn and death events | nothing | no |
| 2 | [MobInspectorPlugin](src/main/java/com/example/epicmobs/MobInspectorPlugin.java) | every query in the API | nothing | no |
| 3 | [SpawnControlPlugin](src/main/java/com/example/epicmobs/SpawnControlPlugin.java) | pre-spawn event | cancels and relocates spawns | no |
| 4 | [BountyPlugin](src/main/java/com/example/epicmobs/BountyPlugin.java) | death, damage, kill counts | damage amounts | no |
| 5 | [LootAugmentPlugin](src/main/java/com/example/epicmobs/LootAugmentPlugin.java) | loot event, damage tables | the drop list | no |
| 6 | [RaidAnnouncerPlugin](src/main/java/com/example/epicmobs/RaidAnnouncerPlugin.java) | all three raid events, `RaidView` | starts and stops raids | for the command only |
| 7 | [CompanionPerksPlugin](src/main/java/com/example/epicmobs/CompanionPerksPlugin.java) | companion events | player effects | to see it fire |

---

## What each one teaches

### 1. QuickStartPlugin

The smallest useful hook. `EpicMobsProvider.find()`, a logged line when Epic Mobs is absent,
two event handlers, one guarded API call. Everything else is a variation on this shape.

### 2. MobInspectorPlugin

`/mobinfo`, `/mobinfo list`, `/mobinfo raid`, `/mobinfo me`, `/mobinfo <name>`. Every query
the API offers, including the ones that are easy to miss: `damageByPlayer()`, `phase()`,
`owner()`, `getDiscoveredCount`. Also shows `depend` plus `EpicMobsProvider.get()`, the
right pattern for a plugin that is useless without Epic Mobs.

### 3. SpawnControlPlugin

Four spawn rules, from a blunt world blocklist to relocating a spawn away from a sleeping
player instead of refusing it. Needs no API instance at all: pure event handling, so it
behaves identically in both editions.

### 4. BountyPlugin

Paying by damage share rather than by last hit, using `damageByPlayer()`. Also covers
first-kill detection with `getKillCount`, a permission that raises damage through
`EpicMobDamageEvent`, and wrapping a handler so a failure costs only that payout.

### 5. LootAugmentPlugin

The drop list is live: adding a boss trophy, duplicating an existing drop for a lucky
player, sending participation items straight to inventories, and cancelling loot entirely in
one world.

### 6. RaidAnnouncerPlugin

Start, wave and end, plus a one-second repeating task that reads the live `RaidView` into a
boss bar and stops itself cleanly. `/raidcall` shows the edition check that turns
`canMutate()` into a message a server owner can act on.

### 7. CompanionPerksPlugin

Companion summon and death, and the one guard worth memorising: a companion death fires
`CompanionDeathEvent` **and** `EpicMobDeathEvent`, so filter with `handle.owner() != null`
to avoid counting it twice.

---

## Building them

The examples build as one project, which is how they stay honest: if the API changes shape,
this build breaks.

```bash
cd examples
mvn compile
```

That is a compile check only. It does not produce a usable plugin, because a real plugin has
one main class and one `plugin.yml`, and this project has seven of the former and none of
the latter.

## Using one

1. Copy the `.java` file into your own plugin, under your own package.
2. Copy the `plugin.yml` from its javadoc header, and merge it with yours.
3. Add the API dependency the way
   [Getting started](../docs/01-getting-started.md#step-1-add-the-dependency) shows, at
   `provided` scope.
4. Replace the placeholder bits: `pay()` in the bounty example is a log line, not an economy.

## Requirements

| | |
| --- | --- |
| Java | 21 |
| Spigot API | any reasonably recent version. The pom names the one Epic Mobs itself is built against |
| Epic Mobs Rework | at runtime only, not to compile |
