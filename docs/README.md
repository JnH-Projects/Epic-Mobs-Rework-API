# Documentation

The Epic Mobs Rework developer API, in reading order.

| | Page | Covers |
| --- | --- | --- |
| 1 | [Getting started](01-getting-started.md) | requirements, the dependency, `plugin.yml`, resolving the API, a first listener, troubleshooting |
| 2 | [Core concepts](02-core-concepts.md) | the package boundary, the four views, keys, editions, reloads, threading |
| 3 | [API reference](03-api-reference.md) | every method on `EpicMobsProvider`, `EpicMobsAPI`, `EpicMobHandle`, `EpicMobDefinitionView`, `RaidView` |
| 4 | [Events](04-events.md) | all 11 events, when each fires, what you can change |
| 5 | [Mutation](05-mutation.md) | `spawn`, `remove`, `startRaid`, `stopRaid`, and the edition gate |
| 6 | [Best practices](06-best-practices.md) | the five hard rules, versioning, performance, a ship checklist |

Then read the [examples](../examples/README.md): seven complete plugins, smallest first.

## Quick answers

| I want to | Read |
| --- | --- |
| add the jar to my build | [Getting started, step 1](01-getting-started.md#step-1-add-the-dependency) |
| know if an entity is an Epic Mob | [`isEpicMob`](03-api-reference.md#mobs-in-the-world) |
| stop a mob spawning somewhere | [`EpicMobPreSpawnEvent`](04-events.md#epicmobprespawnevent) |
| change or block damage | [`EpicMobDamageEvent`](04-events.md#epicmobdamageevent) |
| add or remove drops | [`EpicMobLootDropEvent`](04-events.md#epicmoblootdropevent) |
| reward everyone who fought a boss | [`damageByPlayer`](04-events.md#epicmobdeathevent) |
| track raids | [`RaidView`](03-api-reference.md#raidview) and the [raid events](04-events.md#raidstartevent) |
| spawn a mob from my own code | [Mutation](05-mutation.md#spawnstring-definitionkey-location-where) |
| know why my call did nothing | [The edition gate](05-mutation.md#the-edition-gate) |
| avoid breaking on the next release | [Versioning](06-best-practices.md#versioning) |
