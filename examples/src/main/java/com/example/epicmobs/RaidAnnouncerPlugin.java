package com.example.epicmobs;

import me.jaymar921.epicmobs.api.EpicMobsAPI;
import me.jaymar921.epicmobs.api.EpicMobsProvider;
import me.jaymar921.epicmobs.api.RaidView;
import me.jaymar921.epicmobs.api.event.RaidEndEvent;
import me.jaymar921.epicmobs.api.event.RaidStartEvent;
import me.jaymar921.epicmobs.api.event.RaidWaveEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Example 6: following a raid from start to finish.
 *
 * <p>Covers all three raid events, the live {@link RaidView}, a repeating task that polls it
 * to drive the plugin's own boss bar, and {@code startRaid}/{@code stopRaid} behind an
 * edition check.
 *
 * <p>The polling task is the pattern to copy for anything that wants a number every second:
 * schedule it on the main thread, hold nothing but the API instance between runs, and stop
 * it the moment there is nothing to show.
 *
 * <p>plugin.yml:
 *
 * <pre>{@code
 * name: RaidAnnouncer
 * version: 1.0
 * main: com.example.epicmobs.RaidAnnouncerPlugin
 * api-version: '1.21'
 * softdepend: [ EpicMobsRework ]
 * commands:
 *   raidcall:
 *     description: Start or stop a raid
 *     usage: /raidcall <start [name]|stop|status>
 *     permission: raidannouncer.admin
 * }</pre>
 */
public final class RaidAnnouncerPlugin extends JavaPlugin implements Listener {

    private static final long POLL_TICKS = 20;   // once a second is plenty

    private EpicMobsAPI epicMobs;

    /** Both non-null only while a raid is running. */
    private BukkitTask ticker;
    private BossBar bar;

    /** Everyone who was present when the raid started, for the closing message. */
    private final Set<UUID> participants = new HashSet<>();

    @Override
    public void onEnable() {
        EpicMobsProvider.find().ifPresentOrElse(
                api -> {
                    this.epicMobs = api;
                    getServer().getPluginManager().registerEvents(this, this);

                    if (!api.canMutate()) {
                        getLogger().info("Epic Mobs Lite: /raidcall start and stop are off, "
                                + "announcements are on.");
                    }
                },
                () -> getLogger().warning("Epic Mobs is absent. Raid announcements are off."));
    }

    @Override
    public void onDisable() {
        stopTicker();
    }

    /* ------------------------------------------------------------------------ events */

    /** Fired after the boss bar exists. isScheduled() is false for a command or API call. */
    @EventHandler
    public void onRaidStart(RaidStartEvent event) {
        RaidView raid = event.getRaid();

        participants.clear();
        event.getParticipants().forEach(player -> participants.add(player.getUniqueId()));

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "  " + raid.title());
        Bukkit.broadcastMessage(ChatColor.GRAY + "  " + raid.killGoal() + " to hold the line"
                + (event.isScheduled() ? "" : ChatColor.DARK_GRAY + "  (called)"));
        Bukkit.broadcastMessage("");

        event.getParticipants().forEach(player ->
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f));

        startTicker(raid);
    }

    /** Fired once a wave's mobs are actually in the world. */
    @EventHandler
    public void onRaidWave(RaidWaveEvent event) {
        Bukkit.broadcastMessage(ChatColor.RED + "Wave " + (event.getWave() + 1)
                + ChatColor.GRAY + ": " + event.getSpawned() + " incoming"
                + (event.getRaid().bossUp() ? ChatColor.DARK_RED + "  THE BOSS IS HERE" : ""));
    }

    /**
     * Fired before the prizes are paid, so anything extra you want to give goes here.
     *
     * <p>STOPPED means an operator or another plugin ended it: pay nothing, say nothing.
     */
    @EventHandler
    public void onRaidEnd(RaidEndEvent event) {
        stopTicker();

        RaidView raid = event.getRaid();

        switch (event.getOutcome()) {
            case DEFEATED -> {
                Bukkit.broadcastMessage(ChatColor.GREEN + raid.title() + ChatColor.GRAY
                        + " has fallen to " + event.getParticipants().size() + " players.");
                event.getParticipants().forEach(this::congratulate);
            }
            case EXPIRED -> Bukkit.broadcastMessage(ChatColor.RED + "The siege holds. "
                    + ChatColor.GRAY + raid.killed() + " of " + raid.killGoal() + " felled.");
            case STOPPED -> getLogger().info("Raid " + raid.name() + " was stopped.");
        }

        participants.clear();
    }

    private void congratulate(Player player) {
        player.sendTitle(ChatColor.GOLD + "Victory", ChatColor.GRAY + "The raid is broken", 10, 60, 20);
    }

    /* ------------------------------------------------------------------------ ticker */

    private void startTicker(RaidView raid) {
        stopTicker();

        bar = Bukkit.createBossBar(ChatColor.stripColor(raid.title()),
                BarColor.RED, BarStyle.SEGMENTED_10);
        participants.stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null)
                .forEach(player -> bar.addPlayer(player));

        // Main thread. Never touch the API from an async task.
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, POLL_TICKS, POLL_TICKS);
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
    }

    private void tick() {
        // A live view: this reads the current numbers every second without re-fetching
        // anything. If the raid ended without an event reaching us, stop cleanly.
        RaidView raid = epicMobs.getActiveRaid().orElse(null);
        if (raid == null) {
            stopTicker();
            return;
        }

        // progress() is already 0 to 1, but clamp anyway: setProgress throws otherwise.
        bar.setProgress(Math.min(1, Math.max(0, raid.progress())));
        bar.setTitle(ChatColor.stripColor(raid.title())
                + "   " + raid.killed() + "/" + raid.killGoal()
                + "   wave " + (raid.wave() + 1)
                + clock(raid));

        // The boss turning up is worth a colour change.
        bar.setColor(raid.bossUp() ? BarColor.PURPLE : BarColor.RED);

        // Latecomers see the bar too.
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) bar.addPlayer(player);
        }
    }

    /** ticksRemaining() is -1 when the raid has no time limit. */
    private String clock(RaidView raid) {
        if (raid.ticksRemaining() < 0) return "";
        long seconds = raid.ticksRemaining() / 20;
        return String.format("   %d:%02d", seconds / 60, seconds % 60);
    }

    /* ----------------------------------------------------------------------- command */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (epicMobs == null) {
            sender.sendMessage(ChatColor.RED + "Epic Mobs is not installed.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/raidcall <start [name]|stop|status>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> epicMobs.getActiveRaid().ifPresentOrElse(
                    raid -> sender.sendMessage(raid.title() + "  " + raid.killed()
                            + "/" + raid.killGoal() + "  wave " + (raid.wave() + 1)),
                    () -> sender.sendMessage("No raid is running."));

            case "start" -> {
                // The four mutating calls are Premium. Ask before being refused, so the
                // message names the real reason rather than "something went wrong".
                if (!epicMobs.canMutate()) {
                    sender.sendMessage(ChatColor.RED + "Starting a raid needs Epic Mobs Premium.");
                    return true;
                }
                // A null name starts a random raid.
                String name = args.length > 1 ? String.join(" ", args).substring(args[0].length() + 1) : null;

                if (epicMobs.startRaid(name)) {
                    // startRaid returns only a boolean, so fetch the raid afterwards.
                    epicMobs.getActiveRaid().ifPresent(raid ->
                            sender.sendMessage(ChatColor.GREEN + "Started " + raid.title()));
                } else {
                    // Raids disabled, one already running, or too few players online.
                    sender.sendMessage(ChatColor.RED + "No raid started. Check that raids are "
                            + "enabled, none is running, and enough players are online.");
                }
            }

            case "stop" -> {
                if (!epicMobs.canMutate()) {
                    sender.sendMessage(ChatColor.RED + "Stopping a raid needs Epic Mobs Premium.");
                    return true;
                }
                sender.sendMessage(epicMobs.stopRaid()
                        ? ChatColor.YELLOW + "Raid stopped."
                        : ChatColor.GRAY + "No raid was running.");
            }

            default -> sender.sendMessage("/raidcall <start [name]|stop|status>");
        }
        return true;
    }
}
