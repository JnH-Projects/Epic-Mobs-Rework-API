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

## API v2, unchanged (plugin 1.3.0 to 1.6.0)

No method, event or field changed in these four releases, so `apiVersion()` stays 2 and
the 1.6.0 jar holds the same 19 classes as 1.2.0's. Two behaviours are worth knowing.

**From plugin 1.3.0, Folia.** The same plugin jar runs on Folia, which has no single main
thread. Call `spawn` from the region thread that owns the location, through Folia's region
scheduler. From any other thread it returns empty rather than throwing. Events are fired on
the thread that raised them, which on Folia is a region thread. See
[Core concepts](docs/02-core-concepts.md#on-folia) and
[Mutation](docs/05-mutation.md#on-folia).

**From plugin 1.6.0, `getKiller()` can name a player vanilla does not.** On
`EpicMobDeathEvent` and `EpicMobLootDropEvent`, `getKiller()` is still vanilla's killer
whenever vanilla names one. When it names nobody and
[Histoblock](https://www.jhprojects.dev/histoblock) 1.1.0 or newer is installed, it is the
player Histoblock credits: the one who lit the TNT, threw the potion, or knocked the mob
into lava a moment before it died. Loot, rewards, kill counts, raids and arenas are credited
to the same player, so `getKillCount(killer, ...)` still already includes the kill. Without
Histoblock, or with `integrations.histoblock.kill-credit: false`, nothing changes. A mob or
block behind the death still gives null. See
[Events](docs/04-events.md#epicmobdeathevent).

Artifact: [`releases/1.6.0/EpicMobsRework-api.jar`](releases/1.6.0/EpicMobsRework-api.jar)

## API v2, unchanged (plugin 1.2.0)

No method, event or field changed, so `apiVersion()` stays 2 and the jar holds the same
classes as 1.1.0's. One behaviour changed, and it is on `spawn`:

**A mob from `EpicMobsAPI.spawn` belongs to the caller.** It carries an
`epicmobsrework:held` persistent-data tag, and Epic Mobs never despawns it in the
population cleanup, never saves it when its chunk unloads or the server stops, never
brings a copy back later, and never clears it from an arena room. Up to 1.1.1 it did all
four, and a copy brought back is a new entity without whatever you tagged the original
with. Remove it yourself with `remove(Entity)` or `Entity.remove()`. See
[Mutation](docs/05-mutation.md#from-plugin-120-the-mob-is-yours).

The plugin is built against Spigot 26.3 from this release and runs from 1.16.5 up on
Spigot and Paper.

Artifact: [`releases/1.2.0/EpicMobsRework-api.jar`](releases/1.2.0/EpicMobsRework-api.jar)

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
