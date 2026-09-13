# 1. Getting started

[Index](README.md) | Next: [Core concepts](02-core-concepts.md)

Ten minutes from an empty plugin to a working hook.

---

## Requirements

| | |
| --- | --- |
| Java | 21 |
| Server | Spigot or Paper |
| Runtime | Epic Mobs Rework installed on the server |

You do **not** need the plugin jar to compile. You compile against
`EpicMobsRework-api.jar`, which is interfaces and events only.

---

## Step 1: add the dependency

Download [`EpicMobsRework-api.jar`](../releases/1.0-RC1/EpicMobsRework-api.jar), then pick
one of the three ways below.

### Maven (install to your local repository)

```bash
mvn install:install-file \
  -Dfile=EpicMobsRework-api.jar \
  -DgroupId=me.JayMar921 \
  -DartifactId=EpicMobsRework \
  -Dversion=1.0-RC1 \
  -Dclassifier=api \
  -Dpackaging=jar
```

```xml
<dependency>
    <groupId>me.JayMar921</groupId>
    <artifactId>EpicMobsRework</artifactId>
    <version>1.0-RC1</version>
    <classifier>api</classifier>
    <scope>provided</scope>
</dependency>
```

### Maven (jar checked into your project)

```xml
<dependency>
    <groupId>me.JayMar921</groupId>
    <artifactId>EpicMobsRework</artifactId>
    <version>1.0-RC1</version>
    <classifier>api</classifier>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/EpicMobsRework-api.jar</systemPath>
</dependency>
```

### Gradle

```groovy
dependencies {
    compileOnly files('libs/EpicMobsRework-api.jar')
}
```

> **`provided` / `compileOnly`, always.** Never shade the API jar into your plugin. Two
> copies of `me.jaymar921.epicmobs.api` on one server, under different class loaders, are
> two different types, and every cast across the boundary fails at runtime.

---

## Step 2: declare the dependency in `plugin.yml`

```yaml
name: MyPlugin
version: 1.0
main: com.example.MyPlugin
api-version: '1.21'

softdepend: [ EpicMobsRework ]
```

`softdepend` orders your enable after Epic Mobs and still lets your plugin load when Epic
Mobs is absent. Use `depend` instead only if your plugin genuinely cannot work without it.

| | `softdepend` | `depend` |
| --- | --- | --- |
| Epic Mobs present | you enable after it | you enable after it |
| Epic Mobs absent | you still enable | your plugin does not load |

---

## Step 3: resolve the API

```java
import me.jaymar921.epicmobs.api.EpicMobsAPI;
import me.jaymar921.epicmobs.api.EpicMobsProvider;

public final class MyPlugin extends JavaPlugin {

    private EpicMobsAPI epicMobs;

    @Override
    public void onEnable() {
        EpicMobsProvider.find().ifPresentOrElse(
                api -> {
                    epicMobs = api;
                    getLogger().info("Hooked Epic Mobs " + api.version()
                            + " (API v" + api.apiVersion() + ")");
                },
                () -> getLogger().warning("Epic Mobs is absent. Features are off."));
    }
}
```

`find()` returns an `Optional` and is what you want when your plugin works either way.
`get()` throws `IllegalStateException` when Epic Mobs is absent, which is the right answer
for a plugin that requires it: a silent null surfaces a hundred lines later as something
else.

> **Resolve from `onEnable` or later, never from a static initialiser or a constructor.**
> The provider is installed during Epic Mobs' own enable. Anything running before that sees
> nothing.

---

## Step 4: listen to something

Events are ordinary Bukkit events. Register a listener the way you always do.

```java
import me.jaymar921.epicmobs.api.event.EpicMobDeathEvent;

public final class DeathLogger implements Listener {

    @EventHandler
    public void onEpicMobDeath(EpicMobDeathEvent event) {
        if (event.getKiller() == null) return;

        event.getKiller().sendMessage("You slew " + event.getMob().displayName()
                + " (tier " + event.getMob().tier() + ")");
    }
}
```

```java
getServer().getPluginManager().registerEvents(new DeathLogger(), this);
```

You do not need the API instance to receive events. Register the listener unconditionally
if you like: with Epic Mobs absent, nothing ever fires it.

---

## Step 5: check it works

1. Put your plugin and Epic Mobs Rework on a test server.
2. Start the server and look for your own `Hooked Epic Mobs ...` line.
3. Run `/ep summon <mob>` in game and kill it.

---

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `IllegalStateException: Epic Mobs Rework is not enabled` | resolved too early, or the plugin is missing | resolve in `onEnable`, add `softdepend` |
| `NoClassDefFoundError: me/jaymar921/epicmobs/api/...` | the API jar is not on the server, and you did not shade it | that is correct behaviour: the plugin supplies it. Check Epic Mobs is installed |
| `ClassCastException` on an API type | you shaded the API jar into your plugin | switch to `provided` / `compileOnly` |
| Events never fire | listener not registered, or Epic Mobs is absent | check your own hook log line |
| `spawn()` returns empty, `startRaid()` returns false | the server runs Epic Mobs Lite | see [Mutation](05-mutation.md) |
| Your listener never sees vanilla mobs | correct: these events only fire for Epic Mobs | use `api.isEpicMob(entity)` to tell them apart |

---

Next: [Core concepts](02-core-concepts.md)
