# 4. Events

[Index](README.md) | Prev: [API reference](03-api-reference.md) | Next: [Mutation](05-mutation.md)

Eleven events, all in `me.jaymar921.epicmobs.api.event`, all extending `EpicMobsEvent`,
all fired on the main thread, all working in both editions.

They are ordinary Bukkit events. Register a `Listener`, annotate with `@EventHandler`, done.
You do not need the API instance to receive them.

---

## The eleven

| Event | Cancellable | Fired |
| --- | --- | --- |
| [`EpicMobPreSpawnEvent`](#epicmobprespawnevent) | yes | before the entity exists |
| [`EpicMobSpawnEvent`](#epicmobspawnevent) | no | after it is tagged, equipped and registered |
| [`EpicMobDamageEvent`](#epicmobdamageevent) | yes | after resistance, before the pool moves |
| [`EpicMobDeathEvent`](#epicmobdeathevent) | no | after the registry, before loot and rewards |
| [`EpicMobAbilityEvent`](#epicmobabilityevent) | yes | after the telegraph, with the final target list |
| [`EpicMobLootDropEvent`](#epicmoblootdropevent) | yes | after the roll, before it hits the ground |
| [`RaidStartEvent`](#raidstartevent) | no | after the bar exists |
| [`RaidWaveEvent`](#raidwaveevent) | no | after a wave's mobs are in the world |
| [`RaidEndEvent`](#raidendevent) | no | before the prizes are paid |
| [`CompanionSummonEvent`](#companionsummonevent) | no | after a companion is bound and out |
| [`CompanionDeathEvent`](#companiondeathevent) | no | alongside `EpicMobDeathEvent` |

`EpicMobsEvent` is constructed with `async = false`, so a handler is always already on the
main thread and may call the API directly.

---

## Lifecycle of one mob

```
        EpicMobPreSpawnEvent      cancellable, location is mutable
                 |
                 v
         EpicMobSpawnEvent        the mob now exists, you get a handle
                 |
      +----------+----------+
      |                     |
      v                     v
EpicMobAbilityEvent   EpicMobDamageEvent     both cancellable, repeat many times
      |                     |
      +----------+----------+
                 |
                 v
         EpicMobDeathEvent        not cancellable, the mob is already gone
                 |
                 v
       EpicMobLootDropEvent       cancellable, the drop list is live
```

---

## `EpicMobPreSpawnEvent`

Fired before the entity exists. **Cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getDefinitionKey()` | `String` | lower case |
| `getLocation()` | `Location` | mutable through `setLocation` |
| `setLocation(Location)` | | ignored when null |
| `getReason()` | `String` | human readable, for logging only |

Every spawn path in the plugin funnels through one method, which is what makes one event
enough: natural spawning, the boss schedule, raid and arena waves, packs, summons, spawners,
triggers, the command and the API itself.

`getReason()` is descriptive text such as `natural`, `boss schedule`, `trigger`,
`spawner block`, `raid <name>`, `summoned by <mob>`, `developer API`. Log it, show it, but
do not branch on the exact string: it is prose and it changes.

```java
@EventHandler
public void onPreSpawn(EpicMobPreSpawnEvent event) {
    // Nothing spawns in the deep caves.
    if (event.getLocation().getBlockY() < 40) {
        event.setCancelled(true);
        return;
    }

    // Push a spawn out of spawn protection instead of refusing it.
    if (isProtected(event.getLocation())) {
        event.setLocation(nearestSafeSpot(event.getLocation()));
    }
}
```

The location is mutable, so you can move a spawn rather than refuse it. Whatever it is when
the event returns is where the mob appears, spawn guard included.

---

## `EpicMobSpawnEvent`

Fired after the mob is tagged, equipped and registered. **Not cancellable**: it already
exists, so cancel in `EpicMobPreSpawnEvent` instead.

| Accessor | Type |
| --- | --- |
| `getMob()` | `EpicMobHandle` |
| `getReason()` | `String` |

```java
@EventHandler
public void onSpawn(EpicMobSpawnEvent event) {
    EpicMobHandle mob = event.getMob();
    if (!mob.boss()) return;

    Bukkit.broadcastMessage(mob.displayName() + " has risen in "
            + mob.location().getWorld().getName());
}
```

---

## `EpicMobDamageEvent`

Fired after resistance is applied and before the damage pool moves. **Cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getMob()` | `EpicMobHandle` | |
| `getAttacker()` | `Player` | null when the damage did not come from a player |
| `getAmount()` | `double` | the amount that will really be dealt |
| `setAmount(double)` | | clamped at 0, never negative |

**The amount is the amount that will really be dealt.** That is worth saying because 1.4.13
zeroed the event after applying its own pool, so everything downstream saw a hit that did
nothing. This plugin reports real numbers.

```java
@EventHandler
public void onDamage(EpicMobDamageEvent event) {
    Player attacker = event.getAttacker();
    if (attacker == null) return;

    if (attacker.hasPermission("mine.slayer")) {
        event.setAmount(event.getAmount() * 1.5);
    }
}
```

To stop a hit entirely, cancel rather than setting zero: a zero damage event still sets the
victim's last attacker, and the mob will retaliate against it.

```java
event.setCancelled(true);    // right
event.setAmount(0);          // wrong, the mob still turns on them
```

---

## `EpicMobDeathEvent`

Fired after the registry is updated, before loot and rewards. **Not cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getMob()` | `EpicMobHandle` | still answers, with `alive()` false |
| `getKiller()` | `Player` | null for a mob that died to fall damage, fire, another mob, or `/kill` |

The handle still works. `damageByPlayer()` in particular is how you reward a whole party
rather than only the last hit.

The killer's own kill count is recorded **before** this event fires, so
`getKillCount(killer, mob.definitionKey())` already includes this kill: a count of 1 means
it was their first. Only the killer's count moves, not every damager's.

```java
@EventHandler
public void onDeath(EpicMobDeathEvent event) {
    EpicMobHandle mob = event.getMob();

    mob.damageByPlayer().forEach((uuid, dealt) -> {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;

        double share = dealt / mob.maxHealth();
        p.sendMessage("You dealt " + Math.round(share * 100) + "% of "
                + mob.displayName() + ".");
    });
}
```

---

## `EpicMobAbilityEvent`

Fired after the telegraph, with the final target list. **Cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getMob()` | `EpicMobHandle` | |
| `getAbilityName()` | `String` | the ability's name from the mob file |
| `getTargets()` | `List<LivingEntity>` | live and mutable |

The list has already been filtered by faction, by the ally rule and by the companion
binding, so what you see is what would land.

```java
@EventHandler
public void onAbility(EpicMobAbilityEvent event) {
    // Adventure mode players are spectators here.
    event.getTargets().removeIf(target ->
            target instanceof Player player && player.getGameMode() == GameMode.ADVENTURE);
}
```

Cancel to stop the ability outright. Empty the list to let it fire and hit nothing.

---

## `EpicMobLootDropEvent`

Fired after the loot roll, before anything hits the ground. **Cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getMob()` | `EpicMobHandle` | |
| `getKiller()` | `Player` | may be null |
| `getDrops()` | `List<ItemStack>` | live and mutable |

The list is live. Add to it, take from it, clear it, or cancel to drop nothing at all. Epic
Mobs replaces vanilla drops rather than adding to them, so this list is everything the mob
will drop.

```java
@EventHandler
public void onLoot(EpicMobLootDropEvent event) {
    if (event.getMob().boss()) {
        event.getDrops().add(new ItemStack(Material.NETHER_STAR));
    }

    // Keep cursed items away from players who cannot use them.
    Player killer = event.getKiller();
    if (killer != null && !killer.hasPermission("mine.cursed")) {
        event.getDrops().removeIf(this::isCursed);
    }
}
```

This event is the only place the API exposes loot. Definition views do not carry the loot
table on purpose; see
[What is deliberately not exposed](02-core-concepts.md#what-is-deliberately-not-exposed).

---

## `RaidStartEvent`

Fired after the boss bar exists. **Not cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getRaid()` | `RaidView` | live |
| `getParticipants()` | `List<Player>` | who was in at the start |
| `isScheduled()` | `boolean` | true when the schedule started it, false for a command or an API call |

---

## `RaidWaveEvent`

Fired after a wave's mobs are in the world. **Not cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getRaid()` | `RaidView` | |
| `getWave()` | `int` | the wave that just landed, counting from zero |
| `getSpawned()` | `int` | how many mobs it actually put in the world |

```java
@EventHandler
public void onWave(RaidWaveEvent event) {
    Bukkit.broadcastMessage("Wave " + (event.getWave() + 1) + ": "
            + event.getSpawned() + " incoming");
}
```

---

## `RaidEndEvent`

Fired before the prizes are paid. **Not cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getRaid()` | `RaidView` | |
| `getOutcome()` | `RaidEndEvent.Outcome` | `DEFEATED`, `EXPIRED` or `STOPPED` |
| `isDefeated()` | `boolean` | shorthand for `getOutcome() == DEFEATED` |
| `getParticipants()` | `List<Player>` | everyone who took part |

| Outcome | Means |
| --- | --- |
| `DEFEATED` | the players won |
| `EXPIRED` | the time limit ran out |
| `STOPPED` | an operator or `stopRaid()` ended it |

```java
@EventHandler
public void onRaidEnd(RaidEndEvent event) {
    switch (event.getOutcome()) {
        case DEFEATED -> Bukkit.broadcastMessage(event.getRaid().title() + " has fallen.");
        case EXPIRED  -> Bukkit.broadcastMessage("The siege holds. Time ran out.");
        case STOPPED  -> { /* an operator ended it, stay quiet */ }
    }
}
```

Because it fires before payout, this is where you add your own prize on top of the raid's.

---

## `CompanionSummonEvent`

Fired after a companion is bound and out. Premium feature, but the event class exists in
both editions. **Not cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getOwner()` | `Player` | |
| `getCompanion()` | `EpicMobHandle` | `owner()` is the summoner, `level()` is its level |
| `isFirstClaim()` | `boolean` | true the first time this player claims this companion |

---

## `CompanionDeathEvent`

Fired alongside `EpicMobDeathEvent` when the dead mob was a companion. **Not cancellable.**

| Accessor | Type | Notes |
| --- | --- | --- |
| `getOwner()` | `Player` | |
| `getCompanion()` | `EpicMobHandle` | |
| `getCooldownTicks()` | `long` | before it can be summoned again |
| `isPermanent()` | `boolean` | true when the companion is gone for good |

```java
@EventHandler
public void onCompanionDeath(CompanionDeathEvent event) {
    if (event.isPermanent()) {
        event.getOwner().sendMessage(event.getCompanion().displayName() + " is lost.");
    } else {
        event.getOwner().sendMessage("Back in "
                + (event.getCooldownTicks() / 20) + " seconds.");
    }
}
```

A companion death fires both this and `EpicMobDeathEvent`. If you listen to both, guard
against double counting with `handle.owner() != null`.

---

## Handler rules

**Do not throw out of a handler.** Epic Mobs wraps every event it fires and logs what threw,
so a badly written listener cannot take the plugin down. But a handler that throws inside
`EpicMobDeathEvent` still costs that death whatever your listener was going to do.

**Keep hot handlers cheap.** `EpicMobDamageEvent` fires on every hit on every Epic Mob on
the server. Filter first, work second.

```java
@EventHandler
public void onDamage(EpicMobDamageEvent event) {
    if (!event.getMob().boss()) return;      // cheapest check first
    if (event.getAttacker() == null) return;
    expensiveWork(event);
}
```

**Use `ignoreCancelled` when you only care about hits that land.**

```java
@EventHandler(ignoreCancelled = true)
public void onDamage(EpicMobDamageEvent event) { ... }
```

**Priority works normally.** Listen at `MONITOR` to observe without changing anything, and
do not modify the event there.

---

Next: [Mutation](05-mutation.md)
