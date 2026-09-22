# Changelog

Changes to the **developer API** only. The plugin has its own changelog; this file tracks
`me.jaymar921.epicmobs.api` and the artifact published from it.

`apiVersion()` is bumped whenever anything in the API package changes shape. `version()`
moves with every plugin release and is not tracked here.

## Contract

| Change | Bumps `apiVersion()` | Breaks consumers |
| --- | --- | --- |
| a new method on an existing interface | yes | only if you implemented that interface |
| a new event | yes | no |
| a new field on an existing event | yes | no |
| a removed or renamed method | yes | yes, and it will be announced first |

You never implement these interfaces yourself, so a new method on `EpicMobsAPI` or
`EpicMobHandle` is additive for every real consumer.

---

## API v2 (plugin 1.1.0)

One new method, nothing removed or renamed. Code built against v1 keeps compiling and
keeps running.

**`EpicMobDefinitionView`**

- `mythicMob()`, returns `Optional<String>`. The MythicMobs mob type a definition is built
  on, or empty for a definition that spawns a plain Bukkit entity. `entityType()` is
  unchanged and still answers for every definition.

Calling `mythicMob()` on a server that runs 1.0.0 throws `NoSuchMethodError`, because the
method does not exist there. If your plugin uses it, either raise your minimum to v2 or
check `api.apiVersion() >= 2` first and fall back to `entityType()`.

Artifact: [`releases/1.1.0/EpicMobsRework-api.jar`](releases/1.1.0/EpicMobsRework-api.jar)

## API v1 (plugin 1.0-RC1, 1.0.0)

The first published contract. 1.0.0 shipped the same interfaces as the release candidate;
the jar was rebuilt, the contract did not move.

**Entry point**

- `EpicMobsProvider.get()`, `find()`, `isAvailable()`

**Interfaces**

- `EpicMobsAPI` with 4 identity methods, 10 queries and 4 mutating calls
- `EpicMobHandle`, a live view of one mob in the world
- `EpicMobDefinitionView`, a view of one mob file's numbers
- `RaidView`, a live view of the running raid

**Events**, all in `me.jaymar921.epicmobs.api.event`

- `EpicMobPreSpawnEvent`, `EpicMobSpawnEvent`
- `EpicMobDamageEvent`, `EpicMobDeathEvent`
- `EpicMobAbilityEvent`, `EpicMobLootDropEvent`
- `RaidStartEvent`, `RaidWaveEvent`, `RaidEndEvent`
- `CompanionSummonEvent`, `CompanionDeathEvent`
