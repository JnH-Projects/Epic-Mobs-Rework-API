package com.example.epicmobs;

import me.jaymar921.epicmobs.api.event.EpicMobPreSpawnEvent;
import me.jaymar921.epicmobs.api.event.EpicMobSpawnEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Example 3: deciding where Epic Mobs are allowed to appear.
 *
 * <p>Four rules, in the order a spawn meets them:
 *
 * <ol>
 *   <li>nothing spawns in a world on the blocklist
 *   <li>nothing spawns inside the spawn-protection radius
 *   <li>bosses only spawn below a height, so they do not land on rooftops
 *   <li>a spawn too close to a sleeping player is moved rather than refused
 * </ol>
 *
 * <p>Every spawn path in the plugin funnels through one method, so this single event covers
 * natural spawning, the boss schedule, raid and arena waves, packs, summons, spawners,
 * triggers, the command and the developer API itself.
 *
 * <p>This example needs no API instance at all: it is pure event handling, so it works
 * identically in Lite and Premium.
 *
 * <p>plugin.yml:
 *
 * <pre>{@code
 * name: SpawnControl
 * version: 1.0
 * main: com.example.epicmobs.SpawnControlPlugin
 * api-version: '1.21'
 * softdepend: [ EpicMobsRework ]
 * }</pre>
 */
public final class SpawnControlPlugin extends JavaPlugin implements Listener {

    private static final Set<String> BLOCKED_WORLDS = Set.of("lobby", "creative");
    private static final double SPAWN_PROTECTION_RADIUS = 64;
    private static final int BOSS_CEILING = 90;
    private static final double KEEP_AWAY_FROM_SLEEPERS = 32;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Runs at HIGH so ordinary listeners have already had their say, and still leaves
     * MONITOR free for anything that only wants to observe the final decision.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreSpawn(EpicMobPreSpawnEvent event) {
        Location where = event.getLocation();
        World world = where.getWorld();
        if (world == null) return;

        // 1. Whole worlds that are not for fighting in.
        if (BLOCKED_WORLDS.contains(world.getName().toLowerCase(Locale.ROOT))) {
            event.setCancelled(true);
            return;
        }

        // 2. Keep the town safe.
        Location worldSpawn = world.getSpawnLocation();
        if (where.distanceSquared(worldSpawn) < SPAWN_PROTECTION_RADIUS * SPAWN_PROTECTION_RADIUS) {
            event.setCancelled(true);
            return;
        }

        // 3. Bosses stay out of the sky. getDefinitionKey() is always lower case.
        if (event.getDefinitionKey().contains("dragon") && where.getBlockY() > BOSS_CEILING) {
            event.setCancelled(true);
            return;
        }

        // 4. The location is mutable, so a spawn near a sleeping player can be pushed away
        //    rather than refused. Whatever it is when this event returns is where the mob
        //    appears, and the spawn guard sees the moved location too.
        Player sleeper = nearestSleeper(where);
        if (sleeper != null) {
            event.setLocation(pushAway(where, sleeper.getLocation()));
        }
    }

    private Player nearestSleeper(Location where) {
        List<Player> players = where.getWorld().getPlayers();
        for (Player player : players) {
            if (!player.isSleeping()) continue;
            if (player.getLocation().distanceSquared(where)
                    < KEEP_AWAY_FROM_SLEEPERS * KEEP_AWAY_FROM_SLEEPERS) {
                return player;
            }
        }
        return null;
    }

    /** Moves {@code where} to the far side of the keep-away radius, on solid ground. */
    private Location pushAway(Location where, Location from) {
        var direction = where.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 0.01) direction.setX(1);   // exactly on top

        Location moved = from.clone().add(direction.normalize().multiply(KEEP_AWAY_FROM_SLEEPERS + 4));
        moved.setY(moved.getWorld().getHighestBlockYAt(moved) + 1);
        return moved;
    }

    /**
     * Only for logging. getReason() is human-readable prose such as "natural",
     * "boss schedule", "trigger", "raid Hollow Siege" or "developer API": show it and log
     * it, but never branch on the exact string, because it changes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(EpicMobSpawnEvent event) {
        if (!event.getMob().boss()) return;

        Location at = event.getMob().location();
        getLogger().info("Boss " + event.getMob().displayName()
                + " (" + event.getReason() + ") at "
                + at.getWorld().getName() + " "
                + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ());
    }
}
