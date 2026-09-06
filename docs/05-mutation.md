# 5. Mutation

[Index](README.md) | Prev: [Events](04-events.md) | Next: [Best practices](06-best-practices.md)

Four calls change the world. All four are Premium.

```java
Optional<EpicMobHandle> spawned = api.spawn("crypt warden", location);
boolean removed = api.remove(entity);
boolean started = api.startRaid("Hollow Siege");
boolean stopped = api.stopRaid();
```

---

## The edition gate

In Lite each of the four returns empty or false and logs once per session naming the
edition. Nothing is silent, and nothing throws.

```java
if (api.canMutate()) {
    api.spawn("crypt warden", location);
} else {
    getLogger().info("Epic Mobs Lite: spawning through the API needs Premium.");
}
```

Check `canMutate()` when you would rather ask than be refused, and when you want to disable
your own command or hide your own menu entry up front. Otherwise just handle the empty
`Optional` or the `false` you get back; both are already correct answers.

**Why the split is here rather than nowhere.** Anybody building against the API should be
able to develop and test against Lite, and every query and every event works there. What
they should not be able to do is write a plugin that turns Lite into Premium.

---

## `spawn(String definitionKey, Location where)`

Spawns one Epic Mob and gives you a handle to it.

```java
Optional<EpicMobHandle> mob = api.spawn("crypt warden", player.getLocation());

mob.ifPresentOrElse(
        handle -> player.sendMessage("Summoned " + handle.displayName()),
        ()     -> player.sendMessage("Could not summon that."));
```

Returns empty when any of these is true:

| Reason | How to tell it apart |
| --- | --- |
| the definition is unknown | `api.getDefinition(key).isEmpty()` |
| the world is gone | `where.getWorld() == null` |
| the server refused the entity | rare, usually a chunk or entity limit |
| this is the Lite build | `!api.canMutate()` |

If you want a specific message per case, check the first three yourself before calling.

**`spawn` bypasses the spawn guard**, for the same reason `/ep summon` does: a caller naming
a definition and a location has made the decision, and a silent refusal from a protection
plugin is impossible to debug from the other side of an API.

It does still fire `EpicMobPreSpawnEvent` with the reason `developer API`, so your own
listeners, and every other plugin's, still see it and can still cancel or relocate it. A
cancelled pre-spawn is one more way `spawn` returns empty.

```java
// Spawn a whole pack in a ring
Location centre = player.getLocation();
for (int i = 0; i < 6; i++) {
    double angle = Math.PI * 2 * i / 6;
    Location at = centre.clone().add(Math.cos(angle) * 8, 0, Math.sin(angle) * 8);
    api.spawn("frost wolf", at);
}
```

---

## `remove(Entity entity)`

Takes an Epic Mob out of the world without killing it.

```java
boolean removed = api.remove(entity);
```

| | `remove(entity)` | damaging it to death |
| --- | --- | --- |
| `EpicMobDeathEvent` | no | yes |
| Loot | no | yes |
| Rewards | no | yes |
| Raid kill credit | no | yes |
| Codex and kill counts | no | yes |

Returns false when the entity is not an Epic Mob, or in Lite.

To kill one properly, damage the entity through Bukkit and let Epic Mobs handle the death
the way it handles every other:

```java
// Removes it silently: for cleanup, world resets, admin tools
api.remove(entity);

// Kills it properly: loot, rewards, raid credit, events
if (entity instanceof LivingEntity living) {
    living.damage(Double.MAX_VALUE, player);
}
```

---

## `startRaid(String raidName)`

Starts a raid by name, or a random one when the name is null.

```java
boolean started = api.startRaid("Hollow Siege");
boolean random  = api.startRaid(null);
```

Subject to every gate `/ep raid start` is subject to except the chance roll:

| Gate | |
| --- | --- |
| raids enabled in config | otherwise false |
| no raid already running | check with `api.getActiveRaid().isEmpty()` |
| the minimum player count is met | otherwise false |
| Premium | otherwise false |

`startRaid` returns only a boolean. Get the raid itself straight afterwards:

```java
if (api.startRaid("Hollow Siege")) {
    api.getActiveRaid().ifPresent(raid ->
            Bukkit.broadcastMessage(raid.title() + " begins. " + raid.killGoal() + " to win."));
}
```

`RaidStartEvent` fires with `isScheduled()` false for an API call.

---

## `stopRaid()`

Stops the running raid, unwon.

```java
boolean stopped = api.stopRaid();
```

Returns false when no raid was running, or in Lite. `RaidEndEvent` fires with the outcome
`STOPPED`, so your own end-of-raid handler should treat that case separately from
`DEFEATED`:

```java
@EventHandler
public void onRaidEnd(RaidEndEvent event) {
    if (event.getOutcome() == RaidEndEvent.Outcome.STOPPED) return;   // no prizes
    payOut(event.getParticipants());
}
```

---

## A complete admin command

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage("Players only.");
        return true;
    }
    if (!api.canMutate()) {
        player.sendMessage("This needs Epic Mobs Premium.");
        return true;
    }
    if (args.length == 0) {
        player.sendMessage("/summon <mob>");
        return true;
    }

    String key = String.join(" ", args).toLowerCase(Locale.ROOT);

    if (api.getDefinition(key).isEmpty()) {
        player.sendMessage("No such mob: " + key);
        return true;
    }

    api.spawn(key, player.getLocation()).ifPresentOrElse(
            mob   -> player.sendMessage("Summoned " + mob.displayName()),
            ()    -> player.sendMessage("Something refused the spawn."));
    return true;
}
```

---

Next: [Best practices](06-best-practices.md)
