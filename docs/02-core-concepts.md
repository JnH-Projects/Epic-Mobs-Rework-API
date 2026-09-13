# 2. Core concepts

[Index](README.md) | Prev: [Getting started](01-getting-started.md) | Next: [API reference](03-api-reference.md)

Six ideas. Everything else in this documentation follows from them.

---

## 1. One package is the whole contract

Everything public lives in `me.jaymar921.epicmobs.api` and `me.jaymar921.epicmobs.api.event`.

```
me.jaymar921.epicmobs.api
├── EpicMobsProvider           how you get hold of the API
├── EpicMobsAPI                the entry point
├── EpicMobHandle              one mob in the world
├── EpicMobDefinitionView      one mob file's numbers
├── RaidView                   the running raid
└── event
    ├── EpicMobsEvent          the base class of all 11
    ├── EpicMob*Event          spawn, damage, death, ability, loot
    ├── Raid*Event             start, wave, end
    └── Companion*Event        summon, death
```

Nothing in that package names a class outside it apart from Bukkit and the JDK. If you find
yourself importing `me.jaymar921.epicmobs.<anything else>`, you have left the contract and
your plugin will break on the next release.

### What is deliberately not exposed

| Not exposed | Why |
| --- | --- |
| The plugin's own `EpicMob` and `MobDefinition` | they carry the loader, the phase engine, the ability clock, the edition policy and the boss bar. Compiling against them pins you to an internal shape that changes every release |
| The loot table of a definition | it is the one part of a definition an owner has a reason to keep from their players, and an API that hands it to every plugin hands it to the first one that prints it in chat |

You still get loot at the moment it drops, through
[`EpicMobLootDropEvent`](04-events.md#epicmoblootdropevent).

---

## 2. Four views

| Interface | What it is | Live? |
| --- | --- | --- |
| `EpicMobsAPI` | the entry point | yes |
| `EpicMobHandle` | one mob currently in the world | yes, and keeps answering after it dies |
| `EpicMobDefinitionView` | one mob file's numbers | rebuilt by `/ep reload` |
| `RaidView` | the raid that is running | yes |

**A handle is a live view, not a snapshot.** `health()` answers what the mob's health is
when you ask, not what it was when you got the handle. A handle to a mob that has since died
keeps working, with `alive()` false, so a listener that stored one does not have to guard
every call.

```java
EpicMobHandle mob = api.getEpicMob(entity).orElseThrow();

mob.health();   // now
// ... 30 seconds later ...
mob.health();   // now, again. Not the number from before
mob.alive();    // false if it died in between. No exception either way
```

**A definition view is the file's numbers, before the world touches them.** A mob spawned
from a definition can have more health than `definition.health()` says, because player count
scaling and companion level both apply at spawn. Ask the handle what a particular mob
actually has:

```java
api.getDefinition("frost wolf").get().health();   // what the file says
handle.maxHealth();                               // what this one got
```

---

## 3. Keys, not names

A definition key is the display name in lower case, with colour codes stripped.

```java
api.getDefinition("Frost Wolf");   // same
api.getDefinition("frost wolf");   // as this
api.getDefinition("FROST WOLF");   // and this
```

Every method that takes a name is case-insensitive. `EpicMobHandle.definitionKey()` always
gives you the lower case form, and that is the value to store in your own database: it is
stable across renames of the display text.

Identity for a live mob is the **entity UUID**, `EpicMobHandle.id()`. Never the display
name, which is not unique and is not stable.

---

## 4. Two editions

Epic Mobs Rework ships as Lite and Premium. The split matters to you in exactly one place.

| | Lite | Premium |
| --- | --- | --- |
| Every query | yes | yes |
| Every event | yes | yes |
| `spawn`, `remove`, `startRaid`, `stopRaid` | no | yes |

The four mutating calls return empty or false in Lite and log once per session naming the
edition. Nothing is silent, and nothing throws.

```java
if (api.canMutate()) {
    api.spawn("crypt warden", location);
} else {
    getLogger().info("Epic Mobs Lite: spawning through the API needs Premium.");
}
```

A few query results are also empty in Lite because the feature itself is Premium:
`getRunningArenas()` is always empty, `EpicMobDefinitionView.companionAllowed()` and
`phased()` are always false, `RaidView.bossUp()` is always false.

**Why the split is here rather than nowhere.** Anybody building against the API should be
able to develop and test against Lite, and every query and every event works there. What
they should not be able to do is write a plugin that turns Lite into Premium.

---

## 5. Reload rebuilds definitions

`/ep reload` rebuilds every definition from disk. The `EpicMobDefinitionView` you are
holding describes the old ones.

| Safe to cache | Not safe to cache |
| --- | --- |
| `EpicMobHandle` (live view of a mob) | `EpicMobDefinitionView` |
| `String definitionKey()` | `Collection<EpicMobDefinitionView>` from `getDefinitions()` |
| `UUID id()` | |

Store the key, look the definition up when you need it:

```java
// Wrong: goes stale on the next reload
private EpicMobDefinitionView warden = api.getDefinition("crypt warden").orElseThrow();

// Right: one hash lookup, always current
private static final String WARDEN = "crypt warden";
api.getDefinition(WARDEN).ifPresent(def -> ...);
```

---

## 6. Main thread only

Epic Mobs holds no locks and does no work off the server thread, which is what lets the
whole plugin be one repeating task. Calling in from an async task is undefined and will
eventually corrupt the registry.

```java
// Wrong
Bukkit.getScheduler().runTaskAsynchronously(this, () -> api.getEpicMobs());

// Right: do the slow part async, come back for the API
Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
    var rows = database.load();
    Bukkit.getScheduler().runTask(this, () -> apply(rows, api.getEpicMobs()));
});
```

Every event is fired on the main thread, so a handler is always already in the right place.

---

Next: [API reference](03-api-reference.md)
