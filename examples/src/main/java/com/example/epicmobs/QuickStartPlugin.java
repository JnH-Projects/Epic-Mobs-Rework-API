package com.example.epicmobs;

import me.jaymar921.epicmobs.api.EpicMobHandle;
import me.jaymar921.epicmobs.api.EpicMobsAPI;
import me.jaymar921.epicmobs.api.EpicMobsProvider;
import me.jaymar921.epicmobs.api.event.EpicMobDeathEvent;
import me.jaymar921.epicmobs.api.event.EpicMobSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Example 1: the smallest useful hook.
 *
 * <p>Resolves the API, survives its absence, and listens to two events. Everything else in
 * these examples is a variation on this shape.
 *
 * <p>plugin.yml:
 *
 * <pre>{@code
 * name: QuickStart
 * version: 1.0
 * main: com.example.epicmobs.QuickStartPlugin
 * api-version: '1.21'
 * softdepend: [ EpicMobsRework ]
 * }</pre>
 */
public final class QuickStartPlugin extends JavaPlugin implements Listener {

    /** Null when Epic Mobs is not installed. Every use is guarded. */
    private EpicMobsAPI epicMobs;

    @Override
    public void onEnable() {
        // Resolve here, never in a field initialiser or a static block: the provider is
        // installed during Epic Mobs' own enable, and softdepend puts us after it.
        EpicMobsProvider.find().ifPresentOrElse(this::hook, this::noEpicMobs);

        // Listeners are safe to register either way. With the plugin absent, nothing fires.
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void hook(EpicMobsAPI api) {
        this.epicMobs = api;

        getLogger().info("Hooked Epic Mobs " + api.version()
                + " (API v" + api.apiVersion() + ", "
                + (api.premium() ? "Premium" : "Lite") + ")");
        getLogger().info(api.getDefinitions().size() + " definitions loaded, "
                + api.getEpicMobs().size() + " mobs in the world");
    }

    private void noEpicMobs() {
        getLogger().warning("Epic Mobs Rework is not installed. Features are off.");
    }

    @EventHandler
    public void onSpawn(EpicMobSpawnEvent event) {
        EpicMobHandle mob = event.getMob();

        getLogger().info(mob.displayName() + " (tier " + mob.tier() + ") spawned via "
                + event.getReason() + " with " + mob.maxHealth() + " health");
    }

    @EventHandler
    public void onDeath(EpicMobDeathEvent event) {
        if (event.getKiller() == null) return;   // died to fall damage, fire, another mob

        EpicMobHandle mob = event.getMob();

        // The handle keeps answering after death, so this is safe here.
        event.getKiller().sendMessage("You slew " + mob.displayName()
                + " (tier " + mob.tier() + ")");

        // The API is optional, so guard before using it.
        if (epicMobs != null) {
            int killed = epicMobs.getKillCount(event.getKiller().getUniqueId(),
                    mob.definitionKey());
            event.getKiller().sendMessage("That is number " + killed + " for you.");
        }
    }
}
