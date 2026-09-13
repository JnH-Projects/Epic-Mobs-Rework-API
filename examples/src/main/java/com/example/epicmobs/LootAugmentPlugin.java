package com.example.epicmobs;

import me.jaymar921.epicmobs.api.EpicMobHandle;
import me.jaymar921.epicmobs.api.event.EpicMobLootDropEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Example 5: editing what a mob drops.
 *
 * <p>The drop list on this event is live and is everything the mob will drop, because Epic
 * Mobs replaces vanilla drops rather than adding to them. Add, remove, clear, or cancel to
 * drop nothing at all.
 *
 * <p>Four things happen here:
 *
 * <ol>
 *   <li>bosses always drop a signed trophy naming the killer
 *   <li>a player with a luck permission rolls for a duplicate of one existing drop
 *   <li>everyone who dealt a real share of the damage gets a token in their inventory
 *   <li>tier 1 mobs in a peaceful world drop nothing
 * </ol>
 *
 * <p>plugin.yml:
 *
 * <pre>{@code
 * name: LootAugment
 * version: 1.0
 * main: com.example.epicmobs.LootAugmentPlugin
 * api-version: '1.21'
 * softdepend: [ EpicMobsRework ]
 * permissions:
 *   loot.lucky:
 *     description: Chance at a duplicate drop
 *     default: false
 * }</pre>
 */
public final class LootAugmentPlugin extends JavaPlugin implements Listener {

    private static final double LUCK_CHANCE = 0.15;
    private static final double TOKEN_SHARE = 0.10;   // 10% of the mob's health

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Fired after the loot roll and before anything hits the ground.
     *
     * <p>HIGHEST so this runs after plugins that only want to see the plugin's own table.
     * ignoreCancelled because another listener may already have decided this mob drops
     * nothing, and there is no reason to fight it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLoot(EpicMobLootDropEvent event) {
        EpicMobHandle mob = event.getMob();
        Player killer = event.getKiller();         // may be null: fall damage, fire, a mob
        List<ItemStack> drops = event.getDrops();  // live and mutable

        // 4. A whole world where the small mobs are not worth farming.
        if (mob.tier() <= 1 && isPeaceful(mob)) {
            event.setCancelled(true);
            return;
        }

        // 1. A trophy for the boss, naming whoever finished it.
        if (mob.boss()) {
            drops.add(trophy(mob, killer));
        }

        // 2. Luck: duplicate one of the existing drops rather than inventing an item, so
        //    the reward stays inside whatever the server owner actually configured.
        if (killer != null && killer.hasPermission("loot.lucky") && !drops.isEmpty()) {
            if (ThreadLocalRandom.current().nextDouble() < LUCK_CHANCE) {
                ItemStack pick = drops.get(ThreadLocalRandom.current().nextInt(drops.size()));
                drops.add(pick.clone());
                killer.sendMessage(ChatColor.GREEN + "Lucky! An extra " + describe(pick));
            }
        }

        // 3. Participation tokens go straight to inventories rather than onto the ground,
        //    so the player who fought from range does not have to walk into the pile.
        awardTokens(mob);
    }

    private void awardTokens(EpicMobHandle mob) {
        Map<UUID, Double> damage = mob.damageByPlayer();
        if (damage.isEmpty()) return;

        double threshold = mob.maxHealth() * TOKEN_SHARE;

        damage.forEach((uuid, dealt) -> {
            if (dealt < threshold) return;

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;

            ItemStack token = named(Material.SUNFLOWER, ChatColor.GOLD + "Hunter's Mark",
                    ChatColor.GRAY + "From " + mob.displayName(),
                    ChatColor.GRAY + "Tier " + mob.tier());

            // Anything that will not fit goes on the floor at their feet.
            player.getInventory().addItem(token).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        });
    }

    private ItemStack trophy(EpicMobHandle mob, Player killer) {
        return named(Material.PLAYER_HEAD,
                ChatColor.GOLD + "Trophy: " + mob.displayName(),
                ChatColor.GRAY + "Tier " + mob.tier(),
                ChatColor.GRAY + "Felled by " + (killer == null ? "the world" : killer.getName()));
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String describe(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    private boolean isPeaceful(EpicMobHandle mob) {
        return mob.location().getWorld() != null
                && mob.location().getWorld().getName().endsWith("_peaceful");
    }
}
