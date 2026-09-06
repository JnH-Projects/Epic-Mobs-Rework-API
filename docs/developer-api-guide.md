# Epic Mobs Rework developer API

Feature #14. A small, stable surface another plugin can build on, published the way
Kumandra's Economy publishes its own.

Everything here lives in `me.jaymar921.epicmobs.api`. That package is the whole contract:
nothing in it names a class outside it apart from Bukkit and the JDK, and it ships as its
own artifact so you compile against a handful of interfaces rather than against the plugin.

---

## Getting it

`EpicMobsRework-api.jar` is attached to every release. Add it at `provided` scope, the same
way you add Spigot.

```xml
<dependency>
    <groupId>me.JayMar921</groupId>
    <artifactId>EpicMobsRework</artifactId>
    <version>1.0</version>
    <classifier>api</classifier>
    <scope>provided</scope>
</dependency>
```

Or drop the jar in and reference it directly. It has no dependencies of its own beyond the
Bukkit API you already have.

Then declare the plugin as a soft dependency, so you enable after it:

```yaml
softdepend: [ EpicMobsRework ]
```

`depend` instead of `softdepend` if your plugin genuinely does not work without it.

---

## Hello world

```java
import me.jaymar921.epicmobs.api.*;

@Override
public void onEnable() {
    EpicMobsProvider.find().ifPresent(api ->
            getLogger().info("Epic Mobs " + api.version() + ", "
                    + api.getDefinitions().size() + " definitions loaded"));
}
```

`find()` returns an `Optional` and is what you want when your plugin works either way.
`get()` throws when Epic Mobs is absent, which is the right answer for a plugin that
requires it: a silent null surfaces a hundred lines later as something else.

**Resolve from `onEnable` or later, never from a static initialiser.** The provider is
installed during Epic Mobs' own enable.

---

## Queries

```java
EpicMobsAPI api = EpicMobsProvider.get();

boolean epic = api.isEpicMob(entity);
Optional<EpicMobHandle> mob = api.getEpicMob(entity);
Collection<EpicMobHandle> all = api.getEpicMobs();
Collection<EpicMobHandle> wolves = api.getEpicMobs("frost wolf");

Collection<EpicMobDefinitionView> defs = api.getDefinitions();
Optional<EpicMobDefinitionView> wolf = api.getDefinition("Frost Wolf");

Optional<RaidView> raid = api.getActiveRaid();
Collection<String> arenas = api.getRunningArenas();

int killed = api.getKillCount(player.getUniqueId(), "crypt warden");
int seen = api.getDiscoveredCount(player.getUniqueId());
```

Names are case-insensitive everywhere and a definition key is the display name in lower
case, so `"Frost Wolf"` and `"frost wolf"` are the same argument.

`isEpicMob` is one hash lookup, so it is safe in a hot listener.

### The four views

| Interface | What it is | Live? |
| --- | --- | --- |
| `EpicMobHandle` | one mob in the world | yes, keeps answering after it dies |
| `EpicMobDefinitionView` | one mob file's numbers | rebuilt by `/ep reload` |
| `RaidView` | the running raid | yes |
| `EpicMobsAPI` | the entry point | yes |

A handle is a live view rather than a snapshot: `health()` answers what the mob's health is
when you ask. A handle to a dead mob keeps working, with `alive()` false, so a listener that
stored one does not have to guard every call.

**Do not cache a definition view across a `/ep reload`.** A reload builds new definitions,
and the view you are holding describes the old ones.

### What is deliberately not exposed

- **The plugin's own `EpicMob` and `MobDefinition`.** They reference the loader, the phase
  engine, the ability clock, the edition policy and the boss bar. A consumer compiled
  against them would be pinned to an internal shape that changes every release.
- **The loot table.** It is the one part of a definition an owner has a reason to keep from
  their players, and an API that hands it to every plugin on the server hands it to the
  first one that prints it in chat.

---

## Events

Eleven, all in `me.jaymar921.epicmobs.api.event`, all fired on the main thread.

| Event | Cancellable | Fired |
| --- | --- | --- |
| `EpicMobPreSpawnEvent` | yes | before the entity exists |
| `EpicMobSpawnEvent` | no | after it is tagged, equipped and registered |
| `EpicMobDamageEvent` | yes | after resistance, before the pool moves |
| `EpicMobDeathEvent` | no | after the registry, before loot and rewards |
| `EpicMobAbilityEvent` | yes | after the telegraph, with the final target list |
| `EpicMobLootDropEvent` | yes | after the roll, before it hits the ground |
| `RaidStartEvent` | no | after the bar exists |
| `RaidWaveEvent` | no | after a wave's mobs are in the world |
| `RaidEndEvent` | no | before the prizes are paid |
| `CompanionSummonEvent` | no | after a companion is bound and out |
| `CompanionDeathEvent` | no | alongside `EpicMobDeathEvent` |

### Refusing a spawn

Every spawn path in the plugin funnels through one method, which is what makes one event
enough: natural spawning, the boss schedule, raid and arena waves, packs, summons, spawners,
triggers, the command and the API itself.

```java
@EventHandler
public void onPreSpawn(EpicMobPreSpawnEvent event) {
    if (event.getLocation().getBlockY() < 40) {
        event.setCancelled(true);
    }
}
```

The location is mutable, so you can move a spawn rather than refuse it. Whatever it is when
the event returns is where the mob appears, spawn guard included.

### Changing damage

```java
@EventHandler
public void onDamage(EpicMobDamageEvent event) {
    if (event.getAttacker() != null && event.getAttacker().hasPermission("mine.slayer")) {
        event.setAmount(event.getAmount() * 1.5);
    }
}
```

**The amount is the amount that will really be dealt.** That is worth saying because
1.4.13 zeroed the event after applying its own pool, so everything downstream saw a hit that
did nothing. This plugin reports real numbers.

To stop a hit entirely, cancel rather than setting zero: a zero damage event still sets the
victim's last attacker, and the mob will retaliate against it.

### Changing loot

```java
@EventHandler
public void onLoot(EpicMobLootDropEvent event) {
    if (event.getMob().boss()) {
        event.getDrops().add(new ItemStack(Material.NETHER_STAR));
    }
}
```

The list is live. Add to it, take from it, clear it, or cancel to drop nothing at all. Epic
Mobs replaces vanilla drops rather than adding to them, so this list is everything the mob
will drop.

### Sparing an ability target

```java
@EventHandler
public void onAbility(EpicMobAbilityEvent event) {
    event.getTargets().removeIf(target ->
            target instanceof Player player && player.getGameMode() == GameMode.ADVENTURE);
}
```

The list has already been filtered by faction, by the ally rule and by the companion
binding, so what you see is what would land.

---

## Mutation

Four calls, and all four are Premium.

```java
Optional<EpicMobHandle> spawned = api.spawn("crypt warden", location);
boolean removed = api.remove(entity);
boolean started = api.startRaid("Hollow Siege");
boolean stopped = api.stopRaid();
```

In Lite each returns empty or false and logs once per session naming the edition. Nothing is
silent, and nothing throws. Check first if you would rather ask than be refused:

```java
if (api.canMutate()) {
    api.spawn("crypt warden", location);
} else {
    getLogger().info("Epic Mobs Lite: spawning through the API needs Premium.");
}
```

**Why the split is here rather than nowhere.** Anybody building against the API should be
able to develop and test against Lite, and every query and every event works there. What
they should not be able to do is write a plugin that turns Lite into Premium.

`spawn` bypasses the spawn guard, for the same reason `/ep summon` does: a caller naming a
definition and a location has made the decision, and a silent refusal from a protection
plugin is impossible to debug from the other side of an API.

`remove` takes a mob out of the world without killing it: no death event, no loot, no
rewards, no raid credit. To kill one properly, damage the entity through Bukkit and let Epic
Mobs handle the death the way it handles every other.

---

## Rules

**Main thread only.** Epic Mobs holds no locks and does no work off the server thread, which
is what lets the whole plugin be one repeating task. Calling in from an async task is
undefined and will eventually corrupt the registry.

**Do not throw out of a handler.** Epic Mobs wraps every event it fires and logs what threw,
so a badly written listener cannot take the plugin down, but a handler that throws inside
`EpicMobDeathEvent` still costs that death whatever your listener was going to do.

**Do not cache across a reload.** Definitions and libraries are rebuilt by `/ep reload`.
Handles to live mobs are fine; views of definitions are not.

**The API version is `EpicMobsAPI.apiVersion()`**, and it is bumped when something in the
`api` package changes shape. `version()` is the plugin version and moves for every release.

---

## Worked example

A plugin that pays a bounty for boss kills and announces raid results.

```java
public final class BountyPlugin extends JavaPlugin implements Listener {

    private EpicMobsAPI epicMobs;

    @Override
    public void onEnable() {
        EpicMobsProvider.find().ifPresentOrElse(
                api -> {
                    epicMobs = api;
                    getServer().getPluginManager().registerEvents(this, this);
                    getLogger().info("Hooked Epic Mobs " + api.version());
                },
                () -> getLogger().warning("Epic Mobs is absent, bounties are off."));
    }

    @EventHandler
    public void onDeath(EpicMobDeathEvent event) {
        if (!event.getMob().boss() || event.getKiller() == null) return;

        int killed = epicMobs.getKillCount(event.getKiller().getUniqueId(),
                event.getMob().definitionKey());

        // First kill of this boss pays double.
        int bounty = event.getMob().tier() * (killed <= 1 ? 200 : 100);
        pay(event.getKiller(), bounty);
    }

    @EventHandler
    public void onRaidEnd(RaidEndEvent event) {
        if (!event.isDefeated()) return;

        getServer().broadcastMessage(event.getRaid().title() + " fell to "
                + event.getParticipants().size() + " players.");
    }
}
```

---

## Where things are

| | |
| --- | --- |
| Plugin page | <https://jhprojects.vercel.app/epic-mobs-rework> |
| API package | `me.jaymar921.epicmobs.api` |
| Events | `me.jaymar921.epicmobs.api.event` |
| Artifact | `EpicMobsRework-api.jar`, attached to every release |