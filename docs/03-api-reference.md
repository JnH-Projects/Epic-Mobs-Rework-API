# 3. API reference

[Index](README.md) | Prev: [Core concepts](02-core-concepts.md) | Next: [Events](04-events.md)

Every method on every interface. Events have [their own page](04-events.md); the four
mutating calls have [theirs](05-mutation.md).

---

## `EpicMobsProvider`

A static holder, not the Bukkit services manager. The services manager would put the API
interface on both sides of a class loader boundary, and the whole point of shipping this
package as its own artifact is that you compile against it without the plugin.

| Method | Returns | Notes |
| --- | --- | --- |
| `EpicMobsProvider.get()` | `EpicMobsAPI` | throws `IllegalStateException` when Epic Mobs is absent, disabled, or still enabling |
| `EpicMobsProvider.find()` | `Optional<EpicMobsAPI>` | for a plugin that works either way |
| `EpicMobsProvider.isAvailable()` | `boolean` | a plain presence check |

`register` and `unregister` are public because the implementation lives in another package
of the plugin. They are not part of the contract. Calling them replaces the API for every
consumer on the server, which is a thing to do only if you are Epic Mobs.

```java
// A plugin that requires Epic Mobs
EpicMobsAPI api = EpicMobsProvider.get();

// A plugin that adapts
EpicMobsProvider.find().ifPresent(this::hook);
```

---

## `EpicMobsAPI`

### Identity

| Method | Returns | Notes |
| --- | --- | --- |
| `version()` | `String` | the plugin version from `plugin.yml`. Moves every release |
| `apiVersion()` | `int` | the contract version. Bumped only when the `api` package changes shape |
| `premium()` | `boolean` | whether this is the Premium build |
| `canMutate()` | `boolean` | whether the four mutating calls will do anything |

Gate on `apiVersion()`, not `version()`, when you need a capability:

```java
if (api.apiVersion() < 2) {
    getLogger().warning("Needs Epic Mobs API v2 or later.");
    return;
}
```

### Mobs in the world

| Method | Returns | Notes |
| --- | --- | --- |
| `isEpicMob(Entity)` | `boolean` | one hash lookup, safe in a hot listener |
| `getEpicMob(Entity)` | `Optional<EpicMobHandle>` | empty when the entity is not an Epic Mob |
| `getEpicMobs()` | `Collection<EpicMobHandle>` | every one in the world. A snapshot, safe to iterate |
| `getEpicMobs(String definitionKey)` | `Collection<EpicMobHandle>` | every one from a single definition |

```java
@EventHandler
public void onDamage(EntityDamageByEntityEvent event) {
    if (!api.isEpicMob(event.getEntity())) return;   // cheap, filter first
    // ...
}
```

### Definitions

| Method | Returns | Notes |
| --- | --- | --- |
| `getDefinitions()` | `Collection<EpicMobDefinitionView>` | every loaded definition. In Lite, what the definition ceiling allowed |
| `getDefinition(String nameOrKey)` | `Optional<EpicMobDefinitionView>` | case-insensitive |

Do not cache the results across `/ep reload`. See
[Reload rebuilds definitions](02-core-concepts.md#5-reload-rebuilds-definitions).

### Raids and arenas

| Method | Returns | Notes |
| --- | --- | --- |
| `getActiveRaid()` | `Optional<RaidView>` | at most one raid runs at a time, which is why this is not a collection |
| `getRunningArenas()` | `Collection<String>` | arena names with a party inside. Always empty in Lite |

### Player statistics

| Method | Returns | Notes |
| --- | --- | --- |
| `getKillCount(UUID player, String definitionKey)` | `int` | how many of one definition a player has ever killed. Counted in both editions |
| `getDiscoveredCount(UUID player)` | `int` | how many distinct definitions a player has killed at least one of |

```java
int wardens = api.getKillCount(player.getUniqueId(), "crypt warden");
int codex = api.getDiscoveredCount(player.getUniqueId());
int total = api.getDefinitions().size();

player.sendMessage("Codex " + codex + "/" + total);
```

### Mutation

Four calls, all Premium. Full detail in [Mutation](05-mutation.md).

| Method | Returns | In Lite |
| --- | --- | --- |
| `spawn(String definitionKey, Location where)` | `Optional<EpicMobHandle>` | empty |
| `remove(Entity entity)` | `boolean` | false |
| `startRaid(String raidName)` | `boolean` | false |
| `stopRaid()` | `boolean` | false |

---

## `EpicMobHandle`

One Epic Mob currently in the world. A live view: every getter answers for right now.

| Method | Returns | Notes |
| --- | --- | --- |
| `id()` | `UUID` | the entity UUID. The only identity Epic Mobs uses |
| `entity()` | `LivingEntity` | never null, but may be dead or invalid |
| `definitionKey()` | `String` | lower case. Stable across renames |
| `displayName()` | `String` | the name the owner wrote, without colour codes |
| `tier()` | `int` | 1 to 6 |
| `boss()` | `boolean` | whether it spawns on the boss schedule |
| `alive()` | `boolean` | whether it is still worth acting on |
| `location()` | `Location` | where it is now |
| `health()` | `double` | tracked health. Under the default damage model, the entity's own |
| `maxHealth()` | `double` | after player count scaling and companion level |
| `damage()` | `double` | the damage it deals, after the same scaling |
| `phase()` | `int` | boss phase counting from zero, or `-1` when it has none |
| `level()` | `int` | 1 for everything that is not a levelled companion |
| `owner()` | `UUID` | the player it belongs to, or null. Non-null only for a companion |
| `damageByPlayer()` | `Map<UUID, Double>` | how much each player has dealt to it. A copy, safe to keep |

```java
double percent = handle.health() / handle.maxHealth() * 100;

UUID topDamager = handle.damageByPlayer().entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
```

`entity()` never returns null but the entity may be dead or removed. Check `alive()` before
acting on it, or check the entity itself with `isValid()`.

---

## `EpicMobDefinitionView`

One mob definition, before anything the world does to it.

| Method | Returns | Notes |
| --- | --- | --- |
| `key()` | `String` | lower case. What every other method takes as an argument |
| `displayName()` | `String` | the name the owner wrote, without colour codes |
| `entityType()` | `EntityType` | the Bukkit type it spawns as |
| `tier()` | `int` | 1 to 6 |
| `health()` | `double` | the file's number, before scaling |
| `damage()` | `double` | the file's number, before scaling |
| `resistance()` | `double` | percentage damage reduction, 0 to the server's configured ceiling |
| `abilities()` | `List<String>` | ability names in file order. Names, not definitions: abilities are owner data |
| `boss()` | `boolean` | whether it spawns on the boss schedule |
| `companionAllowed()` | `boolean` | whether a player may claim one. Always false in Lite |
| `phased()` | `boolean` | whether it has a `phases:` block. Always false in Lite |

The loot table is deliberately absent. See
[What is deliberately not exposed](02-core-concepts.md#what-is-deliberately-not-exposed).

```java
List<EpicMobDefinitionView> bosses = api.getDefinitions().stream()
        .filter(EpicMobDefinitionView::boss)
        .sorted(Comparator.comparingInt(EpicMobDefinitionView::tier).reversed())
        .toList();
```

---

## `RaidView`

The raid that is running. A live view, like a handle.

| Method | Returns | Notes |
| --- | --- | --- |
| `name()` | `String` | the name in the raid file, and what `startRaid` takes |
| `title()` | `String` | the display title, which may carry colour codes |
| `killGoal()` | `int` | how many kills win it |
| `killed()` | `int` | how many so far |
| `progress()` | `double` | 0 to 1. What the raid's tier gates unlock against |
| `centre()` | `Location` | where it is happening, or null when it follows the players about |
| `wave()` | `int` | which wave is next, counting from zero. 0 for a raid with no wave list |
| `ticksRemaining()` | `long` | ticks left before it is lost, or `-1` when it has no time limit |
| `bossUp()` | `boolean` | whether the final boss has landed. Always false in Lite |

```java
api.getActiveRaid().ifPresent(raid -> {
    long seconds = raid.ticksRemaining() < 0 ? -1 : raid.ticksRemaining() / 20;
    player.sendMessage(raid.title() + "  " + raid.killed() + "/" + raid.killGoal()
            + (seconds < 0 ? "" : "  " + seconds + "s left"));
});
```

`centre()` and `title()` are the two places a null or a colour code can surprise you. Guard
`centre()`, and strip colour from `title()` if you are writing it somewhere that is not chat.

---

Next: [Events](04-events.md)
