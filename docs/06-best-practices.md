# 6. Best practices

[Index](README.md) | Prev: [Mutation](05-mutation.md)

The rules that keep a consumer plugin correct across upgrades, reloads and both editions.

---

## The five hard rules

### 1. Main thread only

Epic Mobs holds no locks and does no work off the server thread. Calling in from an async
task is undefined and will eventually corrupt the registry.

```java
// Wrong
Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
    for (EpicMobHandle mob : api.getEpicMobs()) { ... }
});

// Right
Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
    List<Row> rows = database.query();                       // slow work off thread
    Bukkit.getScheduler().runTask(this, () -> {
        for (EpicMobHandle mob : api.getEpicMobs()) { ... }  // API work on thread
    });
});
```

Every event is fired on the main thread, so a handler is already in the right place.

### 2. Resolve from `onEnable`, never earlier

The provider is installed during Epic Mobs' own enable. A static initialiser, a field
initialiser or a constructor that runs before that sees nothing.

```java
// Wrong: runs at class load, before Epic Mobs enabled
private static final EpicMobsAPI API = EpicMobsProvider.get();

// Right
private EpicMobsAPI api;

@Override
public void onEnable() {
    this.api = EpicMobsProvider.find().orElse(null);
}
```

### 3. Never shade the API jar

`provided` in Maven, `compileOnly` in Gradle. Two copies of `me.jaymar921.epicmobs.api` on
one server, under different class loaders, are two different types, and every cast across
the boundary throws `ClassCastException` at runtime.

### 4. Do not cache definition views across a reload

`/ep reload` rebuilds every definition. Store the key, resolve on use.

```java
// Wrong
private final EpicMobDefinitionView warden = api.getDefinition("crypt warden").orElseThrow();

// Right
private static final String WARDEN = "crypt warden";
api.getDefinition(WARDEN).ifPresent(this::use);
```

Handles to live mobs are fine to keep. So are keys and UUIDs.

### 5. Do not throw out of a handler

Epic Mobs wraps every event it fires and logs what threw, so a badly written listener cannot
take the plugin down. But a handler that throws inside `EpicMobDeathEvent` still costs that
death whatever your listener was going to do.

```java
@EventHandler
public void onDeath(EpicMobDeathEvent event) {
    try {
        payBounty(event);
    } catch (Exception failed) {
        getLogger().warning("Bounty failed: " + failed.getMessage());
    }
}
```

---

## Versioning

| Method | Moves when | Gate on it? |
| --- | --- | --- |
| `apiVersion()` | the `api` package changes shape | yes |
| `version()` | every plugin release | for display only |

```java
private static final int REQUIRED_API = 1;

if (api.apiVersion() < REQUIRED_API) {
    getLogger().severe("Needs Epic Mobs API v" + REQUIRED_API
            + ", server has v" + api.apiVersion() + ". Disabling.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

New methods and new events bump `apiVersion()`. Compiling against a newer API jar than the
server runs is what produces `NoSuchMethodError`, so state your minimum and check it.

---

## Writing for both editions

Develop against Lite. Every query and every event works there, so if your plugin only reads
and reacts, Lite is a complete test environment.

| Behaves differently in Lite | |
| --- | --- |
| `spawn`, `remove`, `startRaid`, `stopRaid` | empty or false |
| `getRunningArenas()` | always empty |
| `EpicMobDefinitionView.companionAllowed()` | always false |
| `EpicMobDefinitionView.phased()` | always false |
| `RaidView.bossUp()` | always false |
| `EpicMobHandle.phase()` | always `-1` |

Degrade, do not disable:

```java
if (api.canMutate()) {
    registerAdminCommands();
} else {
    getLogger().info("Epic Mobs Lite detected. Admin spawning is off, "
            + "tracking and rewards are on.");
}
```

---

## Performance

`isEpicMob` is one hash lookup and safe in a hot listener. `getEpicMobs()` builds a snapshot
collection, so it is not.

```java
// Wrong: allocates the whole world's mobs on every hit
@EventHandler
public void onDamage(EntityDamageEvent event) {
    for (EpicMobHandle mob : api.getEpicMobs()) {
        if (mob.id().equals(event.getEntity().getUniqueId())) { ... }
    }
}

// Right
@EventHandler
public void onDamage(EntityDamageEvent event) {
    api.getEpicMob(event.getEntity()).ifPresent(mob -> { ... });
}
```

Order your handler's checks cheapest first, and prefer the narrower query:

| Instead of | Use |
| --- | --- |
| iterating `getEpicMobs()` to find one entity | `getEpicMob(entity)` |
| iterating `getEpicMobs()` filtering on key | `getEpicMobs(key)` |
| iterating `getDefinitions()` to find one | `getDefinition(nameOrKey)` |
| calling `getEpicMobs()` every tick | call it on a repeating task at 20 ticks or slower |

---

## Nulls, at a glance

| Can be null | Never null |
| --- | --- |
| `EpicMobDamageEvent.getAttacker()` | `EpicMobHandle.entity()` |
| `EpicMobDeathEvent.getKiller()` | anything returning `Optional` |
| `EpicMobLootDropEvent.getKiller()` | any `Collection` or `List` returned |
| `EpicMobHandle.owner()` | |
| `RaidView.centre()` | |

`entity()` is never null but may be dead or removed. Check `alive()` or `isValid()`.

---

## Checklist before you ship

- [ ] The API jar is `provided` or `compileOnly`, not shaded
- [ ] `softdepend: [ EpicMobsRework ]` (or `depend`) is in your `plugin.yml`
- [ ] The API is resolved in `onEnable`, not in a field or static initialiser
- [ ] Your plugin logs a clear line when Epic Mobs is absent, and does not crash
- [ ] No API call happens off the main thread
- [ ] No `EpicMobDefinitionView` is held in a field
- [ ] Every handler that can throw is wrapped
- [ ] Hot handlers filter before they work
- [ ] `canMutate()` gates anything that spawns, removes or starts a raid
- [ ] Tested with the plugin present, absent, and after a `/ep reload`

---

Back to the [index](README.md), or read the [examples](../examples/README.md).
