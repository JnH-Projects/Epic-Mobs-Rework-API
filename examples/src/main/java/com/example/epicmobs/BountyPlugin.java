package com.example.epicmobs;

import me.jaymar921.epicmobs.api.EpicMobHandle;
import me.jaymar921.epicmobs.api.EpicMobsAPI;
import me.jaymar921.epicmobs.api.EpicMobsProvider;
import me.jaymar921.epicmobs.api.event.EpicMobDamageEvent;
import me.jaymar921.epicmobs.api.event.EpicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Example 4: paying players for kills, fairly.
 *
 * <p>The naive version pays the last hit. This one splits the bounty by damage dealt, using
 * {@link EpicMobHandle#damageByPlayer()}, which is what makes a boss fight worth joining for
 * a player who is not the strongest on the server.
 *
 * <p>Three rules:
 *
 * <ol>
 *   <li>the pot is the mob's tier times a per-tier rate, doubled for a boss
 *   <li>it is split by share of total damage, with a minimum share to filter out tourists
 *   <li>the first kill of a definition pays a discovery bonus on top
 * </ol>
 *
 * <p>Swap {@code pay} for your own economy: Vault, a scoreboard objective, an item, points.
 *
 * <p>plugin.yml:
 *
 * <pre>{@code
 * name: Bounty
 * version: 1.0
 * main: com.example.epicmobs.BountyPlugin
 * api-version: '1.21'
 * softdepend: [ EpicMobsRework ]
 * permissions:
 *   bounty.bonus:
 *     description: Pays 25% more
 *     default: false
 * }</pre>
 */
public final class BountyPlugin extends JavaPlugin implements Listener {

    private static final int RATE_PER_TIER = 100;
    private static final int DISCOVERY_BONUS = 500;
    private static final double MINIMUM_SHARE = 0.05;   // 5% of the mob's health
    private static final double PERMISSION_MULTIPLIER = 1.25;

    private EpicMobsAPI epicMobs;

    @Override
    public void onEnable() {
        EpicMobsProvider.find().ifPresentOrElse(
                api -> {
                    this.epicMobs = api;
                    getServer().getPluginManager().registerEvents(this, this);
                    getLogger().info("Bounties on, hooked Epic Mobs " + api.version());
                },
                () -> getLogger().warning("Epic Mobs is absent. Bounties are off."));
    }

    /**
     * Fires after the registry is updated and before loot and rewards, so this is the right
     * place to add your own payout on top of the plugin's.
     *
     * <p>MONITOR because this listener changes nothing about the event. Wrapped in a
     * try/catch because a handler that throws costs this death whatever it was going to do.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EpicMobDeathEvent event) {
        try {
            payOut(event);
        } catch (Exception failed) {
            getLogger().warning("Bounty payout failed for "
                    + event.getMob().displayName() + ": " + failed.getMessage());
        }
    }

    private void payOut(EpicMobDeathEvent event) {
        // The handle keeps answering after death, with alive() false. Nothing here needs
        // the mob to still exist.
        EpicMobHandle mob = event.getMob();

        int pot = mob.tier() * RATE_PER_TIER * (mob.boss() ? 2 : 1);

        // A copy, safe to hold and iterate.
        Map<UUID, Double> damage = mob.damageByPlayer();
        if (damage.isEmpty()) {
            // Nobody hit it: it fell, burned, or another mob killed it. No bounty.
            return;
        }

        double total = damage.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return;

        damage.forEach((uuid, dealt) -> {
            double share = dealt / total;
            if (share < MINIMUM_SHARE) return;

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;   // left mid-fight

            int amount = (int) Math.round(pot * share);
            if (player.hasPermission("bounty.bonus")) {
                amount = (int) Math.round(amount * PERMISSION_MULTIPLIER);
            }
            // Only the killer's own count is recorded, so only the killer can discover.
            if (player.equals(event.getKiller())) {
                amount += discoveryBonus(player, mob);
            }

            if (amount <= 0) return;

            pay(player, amount);
            player.sendMessage(ChatColor.GOLD + "+" + amount + ChatColor.GRAY
                    + " for " + mob.displayName()
                    + " (" + Math.round(share * 100) + "% of the damage)");
        });
    }

    /**
     * getKillCount is counted in both editions and includes the kill that just happened, so
     * a count of 1 here means this was the player's first.
     */
    private int discoveryBonus(Player player, EpicMobHandle mob) {
        // definitionKey() is lower case and stable across renames: it is the value to key
        // your own storage on, never displayName().
        int killed = epicMobs.getKillCount(player.getUniqueId(), mob.definitionKey());
        if (killed > 1) return 0;

        player.sendMessage(ChatColor.LIGHT_PURPLE + "First " + mob.displayName() + "! "
                + ChatColor.GRAY + "Codex " + epicMobs.getDiscoveredCount(player.getUniqueId())
                + "/" + epicMobs.getDefinitions().size());
        return DISCOVERY_BONUS;
    }

    /**
     * A worked example of the damage event: a permission that makes a player hit harder.
     *
     * <p>getAmount is the amount that will really be dealt, after resistance. To stop a hit
     * entirely, cancel it: setting zero still marks the attacker as the mob's last
     * assailant, and the mob will turn on them for nothing.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EpicMobDamageEvent event) {
        Player attacker = event.getAttacker();
        if (attacker == null) return;                          // not a player, cheapest check
        if (!attacker.hasPermission("bounty.bonus")) return;

        event.setAmount(event.getAmount() * PERMISSION_MULTIPLIER);
    }

    /** Replace with your economy of choice. */
    private void pay(Player player, int amount) {
        getLogger().info("Paid " + player.getName() + " " + amount);
    }
}
