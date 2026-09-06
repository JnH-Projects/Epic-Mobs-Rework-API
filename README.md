# Epic Mobs Rework: Developer API

The public developer API for [Epic Mobs Rework](https://jhprojects.vercel.app/epic-mobs-rework),
a Spigot plugin for custom mobs, bosses, raids, arenas and companions.

This repository contains the API artifact, the documentation and runnable examples.
The plugin itself is closed source; everything you need to build against it is here.

```java
EpicMobsProvider.find().ifPresent(api -> {
    getLogger().info("Epic Mobs " + api.version()
            + " with " + api.getDefinitions().size() + " definitions");
});
```

## What the API gives you

| | |
| --- | --- |
| **Queries** | live mobs, definitions, raids, arenas, per-player kill and codex counts |
| **Events** | 11 Bukkit events covering spawn, damage, death, abilities, loot, raids and companions |
| **Mutation** | spawn a mob, remove a mob, start a raid, stop a raid (Premium only) |

Everything lives in one package, `me.jaymar921.epicmobs.api`. That package is the whole
contract: nothing in it names a class outside it apart from Bukkit and the JDK, and it ships
as its own thin artifact, so you compile against a handful of interfaces rather than against
the plugin.

## Start here

1. **[Getting started](docs/01-getting-started.md)** - add the dependency, hook the API, write your first listener.
2. **[Core concepts](docs/02-core-concepts.md)** - views, keys, editions, reloads, threading.
3. **[API reference](docs/03-api-reference.md)** - every method on every interface.
4. **[Events](docs/04-events.md)** - all 11 events, when they fire and what you can change.
5. **[Mutation](docs/05-mutation.md)** - the four calls that change the world.
6. **[Best practices](docs/06-best-practices.md)** - the rules that keep your plugin correct.

## Examples

Seven complete, compilable plugins in [`examples/`](examples/), smallest first:

| Example | Shows |
| --- | --- |
| [QuickStartPlugin](examples/src/main/java/com/example/epicmobs/QuickStartPlugin.java) | resolving the API, one listener, graceful absence |
| [MobInspectorPlugin](examples/src/main/java/com/example/epicmobs/MobInspectorPlugin.java) | queries: handles, definitions, damage tables, a `/mobinfo` command |
| [SpawnControlPlugin](examples/src/main/java/com/example/epicmobs/SpawnControlPlugin.java) | refusing and relocating spawns with `EpicMobPreSpawnEvent` |
| [BountyPlugin](examples/src/main/java/com/example/epicmobs/BountyPlugin.java) | paying rewards on death, first-kill bonuses, kill counts |
| [LootAugmentPlugin](examples/src/main/java/com/example/epicmobs/LootAugmentPlugin.java) | editing the drop list, participation drops, permission-gated luck |
| [RaidAnnouncerPlugin](examples/src/main/java/com/example/epicmobs/RaidAnnouncerPlugin.java) | raid start, wave and end events plus a live progress bar |
| [CompanionPerksPlugin](examples/src/main/java/com/example/epicmobs/CompanionPerksPlugin.java) | companion summon and death, owner-scoped effects |

See [examples/README.md](examples/README.md) for how to build and run them.

## Downloads

| Version | Artifact |
| --- | --- |
| 1.0-RC1 | [`EpicMobsRework-api.jar`](releases/1.0-RC1/EpicMobsRework-api.jar) |

The API jar contains interfaces and events only. It has no dependencies of its own beyond
the Bukkit API you already have, and it is always `provided` scope: the real implementation
comes from the plugin at runtime.

## Requirements

| | |
| --- | --- |
| Java | 21 |
| Server | Spigot or Paper |
| Plugin | Epic Mobs Rework installed on the server at runtime |

## License

The documentation in [`docs/`](docs/) and the example code in [`examples/`](examples/) are
[MIT](LICENSE). Copy them into your own plugin, change them, ship them, no attribution
needed.

`EpicMobsRework-api.jar` in [`releases/`](releases/) is built from the Epic Mobs Rework
source and is covered by that project's own license, not by this one.

## Support

| | |
| --- | --- |
| Plugin page | <https://jhprojects.vercel.app/epic-mobs-rework> |
| Issues with this API | [open an issue](../../issues) |
| API changes by version | [CHANGELOG.md](CHANGELOG.md) |
