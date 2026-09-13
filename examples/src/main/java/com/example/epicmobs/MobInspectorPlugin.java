package com.example.epicmobs;

import me.jaymar921.epicmobs.api.EpicMobDefinitionView;
import me.jaymar921.epicmobs.api.EpicMobHandle;
import me.jaymar921.epicmobs.api.EpicMobsAPI;
import me.jaymar921.epicmobs.api.EpicMobsProvider;
import me.jaymar921.epicmobs.api.RaidView;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Example 2: every query the API offers, behind one command.
 *
 * <p>{@code /mobinfo} inspects the nearest Epic Mob, {@code /mobinfo <name>} a definition,
 * {@code /mobinfo list} every definition, {@code /mobinfo raid} the running raid, and
 * {@code /mobinfo me} the caller's own statistics.
 *
 * <p>Note what is cached and what is not: the API instance is a field, and definition views
 * are never held past the line that uses them, because {@code /ep reload} rebuilds them.
 *
 * <p>plugin.yml:
 *
 * <pre>{@code
 * name: MobInspector
 * version: 1.0
 * main: com.example.epicmobs.MobInspectorPlugin
 * api-version: '1.21'
 * depend: [ EpicMobsRework ]
 * commands:
 *   mobinfo:
 *     description: Inspect Epic Mobs
 *     usage: /mobinfo [list|raid|me|<mob name>]
 *     permission: mobinspector.use
 * }</pre>
 */
public final class MobInspectorPlugin extends JavaPlugin {

    private static final double SEARCH_RADIUS = 24;

    private EpicMobsAPI epicMobs;

    @Override
    public void onEnable() {
        // depend, not softdepend, so get() rather than find(): this plugin is useless
        // without Epic Mobs and a hard failure here is easier to read than a null later.
        try {
            this.epicMobs = EpicMobsProvider.get();
        } catch (IllegalStateException absent) {
            getLogger().severe(absent.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        String argument = args.length == 0 ? "" : String.join(" ", args).toLowerCase(Locale.ROOT);

        switch (argument) {
            case ""     -> nearest(player);
            case "list" -> list(player);
            case "raid" -> raid(player);
            case "me"   -> statistics(player, player);
            default     -> definition(player, argument);
        }
        return true;
    }

    /** The Epic Mob the player is looking at, or the closest one within the radius. */
    private void nearest(Player player) {
        EpicMobHandle closest = null;
        double closestDistance = Double.MAX_VALUE;

        // getEpicMobs() builds a snapshot, so call it once and iterate that. Never call it
        // per entity, and never inside a hot event handler.
        for (Entity entity : player.getNearbyEntities(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            // isEpicMob is one hash lookup: filter with it before doing anything else.
            if (!epicMobs.isEpicMob(entity)) continue;

            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = epicMobs.getEpicMob(entity).orElse(null);
            }
        }

        if (closest == null) {
            player.sendMessage(ChatColor.GRAY + "No Epic Mob within " + (int) SEARCH_RADIUS + " blocks.");
            return;
        }
        describe(player, closest);
    }

    private void describe(Player player, EpicMobHandle mob) {
        player.sendMessage(ChatColor.GOLD + mob.displayName()
                + ChatColor.GRAY + "  (" + mob.definitionKey() + ")");
        player.sendMessage(ChatColor.GRAY + "  tier " + ChatColor.WHITE + mob.tier()
                + ChatColor.GRAY + "   boss " + ChatColor.WHITE + mob.boss()
                + ChatColor.GRAY + "   alive " + ChatColor.WHITE + mob.alive());
        player.sendMessage(ChatColor.GRAY + "  health " + ChatColor.WHITE
                + round(mob.health()) + " / " + round(mob.maxHealth())
                + ChatColor.GRAY + "   damage " + ChatColor.WHITE + round(mob.damage()));

        // -1 means the mob has no phases. Premium only; always -1 in Lite.
        if (mob.phase() >= 0) {
            player.sendMessage(ChatColor.GRAY + "  phase " + ChatColor.WHITE + (mob.phase() + 1));
        }
        // Non-null only for a companion.
        if (mob.owner() != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(mob.owner());
            player.sendMessage(ChatColor.GRAY + "  companion of " + ChatColor.WHITE + owner.getName()
                    + ChatColor.GRAY + " at level " + ChatColor.WHITE + mob.level());
        }

        // damageByPlayer is a copy, so sorting it here costs nothing to the plugin.
        Map<UUID, Double> damage = mob.damageByPlayer();
        if (damage.isEmpty()) return;

        player.sendMessage(ChatColor.GRAY + "  damage dealt:");
        damage.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> player.sendMessage(ChatColor.GRAY + "    "
                        + Bukkit.getOfflinePlayer(entry.getKey()).getName()
                        + "  " + round(entry.getValue())
                        + " (" + Math.round(entry.getValue() / mob.maxHealth() * 100) + "%)"));
    }

    private void list(Player player) {
        // Resolve, use, discard. Holding this list in a field would go stale on /ep reload.
        List<EpicMobDefinitionView> definitions = epicMobs.getDefinitions().stream()
                .sorted(Comparator.comparingInt(EpicMobDefinitionView::tier).reversed()
                        .thenComparing(EpicMobDefinitionView::key))
                .toList();

        player.sendMessage(ChatColor.GOLD + "" + definitions.size() + " definitions");
        for (EpicMobDefinitionView definition : definitions) {
            int live = epicMobs.getEpicMobs(definition.key()).size();

            player.sendMessage(ChatColor.GRAY + "  T" + definition.tier() + " "
                    + ChatColor.WHITE + definition.displayName()
                    + (definition.boss() ? ChatColor.RED + " [boss]" : "")
                    + (live > 0 ? ChatColor.GREEN + " x" + live : ""));
        }
    }

    private void definition(Player player, String nameOrKey) {
        // Case insensitive: "Frost Wolf", "frost wolf" and "FROST WOLF" are one argument.
        epicMobs.getDefinition(nameOrKey).ifPresentOrElse(
                definition -> {
                    player.sendMessage(ChatColor.GOLD + definition.displayName()
                            + ChatColor.GRAY + "  (" + definition.key() + ")");
                    player.sendMessage(ChatColor.GRAY + "  type " + ChatColor.WHITE + definition.entityType()
                            + ChatColor.GRAY + "   tier " + ChatColor.WHITE + definition.tier());
                    // File numbers, before player-count scaling. A spawned mob may have more.
                    player.sendMessage(ChatColor.GRAY + "  base health " + ChatColor.WHITE + round(definition.health())
                            + ChatColor.GRAY + "   base damage " + ChatColor.WHITE + round(definition.damage())
                            + ChatColor.GRAY + "   resistance " + ChatColor.WHITE + round(definition.resistance()) + "%");
                    player.sendMessage(ChatColor.GRAY + "  abilities " + ChatColor.WHITE
                            + (definition.abilities().isEmpty() ? "none" : String.join(", ", definition.abilities())));
                    player.sendMessage(ChatColor.GRAY + "  boss " + ChatColor.WHITE + definition.boss()
                            + ChatColor.GRAY + "   phased " + ChatColor.WHITE + definition.phased()
                            + ChatColor.GRAY + "   companion " + ChatColor.WHITE + definition.companionAllowed());
                    player.sendMessage(ChatColor.GRAY + "  in the world now: " + ChatColor.WHITE
                            + epicMobs.getEpicMobs(definition.key()).size());
                },
                () -> player.sendMessage(ChatColor.RED + "No definition named " + nameOrKey));
    }

    private void raid(Player player) {
        // At most one raid runs at a time, which is why this is an Optional of one.
        epicMobs.getActiveRaid().ifPresentOrElse(
                raid -> {
                    player.sendMessage(ChatColor.GOLD + raid.title() + ChatColor.GRAY + " (" + raid.name() + ")");
                    player.sendMessage(ChatColor.GRAY + "  " + raid.killed() + " / " + raid.killGoal()
                            + "   " + Math.round(raid.progress() * 100) + "%"
                            + "   wave " + (raid.wave() + 1)
                            + (raid.bossUp() ? ChatColor.RED + "   BOSS UP" : ""));
                    player.sendMessage(ChatColor.GRAY + "  " + timeLeft(raid)
                            + "   at " + centre(raid));
                },
                () -> player.sendMessage(ChatColor.GRAY + "No raid is running."));

        // Always empty in Lite, where arenas are a Premium feature.
        var arenas = epicMobs.getRunningArenas();
        if (!arenas.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Arenas running: " + String.join(", ", arenas));
        }
    }

    private void statistics(Player viewer, Player subject) {
        int discovered = epicMobs.getDiscoveredCount(subject.getUniqueId());
        int total = epicMobs.getDefinitions().size();

        viewer.sendMessage(ChatColor.GOLD + subject.getName() + ChatColor.GRAY
                + "  codex " + ChatColor.WHITE + discovered + "/" + total
                + ChatColor.GRAY + " (" + (total == 0 ? 0 : discovered * 100 / total) + "%)");

        epicMobs.getDefinitions().stream()
                .map(definition -> Map.entry(definition,
                        epicMobs.getKillCount(subject.getUniqueId(), definition.key())))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<EpicMobDefinitionView, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> viewer.sendMessage(ChatColor.GRAY + "  "
                        + entry.getKey().displayName() + "  " + ChatColor.WHITE + entry.getValue()));
    }

    /** -1 ticks means the raid has no time limit. */
    private String timeLeft(RaidView raid) {
        if (raid.ticksRemaining() < 0) return "no time limit";
        long seconds = raid.ticksRemaining() / 20;
        return String.format("%d:%02d left", seconds / 60, seconds % 60);
    }

    /** Null means the raid follows the players rather than sitting in one place. */
    private String centre(RaidView raid) {
        if (raid.centre() == null) return "the players";
        return raid.centre().getWorld().getName()
                + " " + raid.centre().getBlockX()
                + ", " + raid.centre().getBlockY()
                + ", " + raid.centre().getBlockZ();
    }

    private String round(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0);
    }
}
