package com.example.epicmobs;

import me.jaymar921.epicmobs.api.EpicMobHandle;
import me.jaymar921.epicmobs.api.EpicMobsAPI;
import me.jaymar921.epicmobs.api.EpicMobsProvider;
import me.jaymar921.epicmobs.api.event.CompanionDeathEvent;
import me.jaymar921.epicmobs.api.event.CompanionSummonEvent;
import me.jaymar921.epicmobs.api.event.EpicMobDeathEvent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Example 7: companions, and how to avoid counting a death twice.
 *
 * <p>Companions are a Premium feature. The event classes exist in both editions and this
 * plugin compiles and runs against Lite; the handlers simply never fire there.
 *
 * <p>The thing worth copying here is {@link #onAnyDeath}: a companion death fires both
 * {@link CompanionDeathEvent} and {@link EpicMobDeathEvent}. If you listen to both, guard
 * with {@code handle.owner() != null}, which is non-null only for a companion.
 *
 * <p>plugin.yml:
 *
 * <pre>{@code
 * name: CompanionPerks
 * version: 1.0
 * main: com.example.epicmobs.CompanionPerksPlugin
 * api-version: '1.21'
 * softdepend: [ EpicMobsRework ]
 * }</pre>
 */
public final class CompanionPerksPlugin extends JavaPlugin implements Listener {

    private static final int BUFF_DURATION_TICKS = 20 * 30;

    private EpicMobsAPI epicMobs;

    @Override
    public void onEnable() {
        EpicMobsProvider.find().ifPresentOrElse(
                api -> {
                    this.epicMobs = api;
                    getServer().getPluginManager().registerEvents(this, this);

                    if (!api.premium()) {
                        getLogger().info("Epic Mobs Lite: companions do not exist here, "
                                + "so these handlers will never fire.");
                    }
                },
                () -> getLogger().warning("Epic Mobs is absent. Companion perks are off."));
    }

    /**
     * Fired after the companion is bound and already out in the world.
     *
     * <p>isFirstClaim() is true the first time this player claims this companion, which is
     * the moment worth marking.
     */
    @EventHandler
    public void onSummon(CompanionSummonEvent event) {
        Player owner = event.getOwner();
        EpicMobHandle companion = event.getCompanion();

        if (event.isFirstClaim()) {
            owner.sendTitle(ChatColor.LIGHT_PURPLE + companion.displayName(),
                    ChatColor.GRAY + "is yours", 10, 60, 20);
            owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            owner.sendMessage(ChatColor.GRAY + companion.displayName()
                    + " answers, level " + companion.level() + ".");
        }

        // A companion's level scales its own health and damage, so the perk scales with it.
        applyBondBuff(owner, companion.level());
    }

    /**
     * Fired alongside {@link EpicMobDeathEvent}. Not cancellable: it has already happened.
     */
    @EventHandler
    public void onCompanionDeath(CompanionDeathEvent event) {
        Player owner = event.getOwner();
        EpicMobHandle companion = event.getCompanion();

        if (event.isPermanent()) {
            owner.sendMessage(ChatColor.DARK_RED + companion.displayName()
                    + ChatColor.GRAY + " is gone for good.");
            owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 0.8f);
            return;
        }

        long seconds = event.getCooldownTicks() / 20;
        owner.sendMessage(ChatColor.RED + companion.displayName() + ChatColor.GRAY
                + " has fallen. Back in " + seconds + " second" + (seconds == 1 ? "" : "s") + ".");

        // Strip the perk that was riding on the companion being alive.
        owner.removePotionEffect(PotionEffectType.SPEED);
        owner.removePotionEffect(PotionEffectType.RESISTANCE);
    }

    /**
     * Every Epic Mob death, companions included.
     *
     * <p>owner() is null for everything that is not a companion, which is the one line that
     * keeps this from double counting against {@link #onCompanionDeath}.
     */
    @EventHandler
    public void onAnyDeath(EpicMobDeathEvent event) {
        EpicMobHandle mob = event.getMob();

        if (mob.owner() != null) return;   // a companion: handled above
        if (event.getKiller() == null) return;

        // Count only mobs killed while the player had a companion out beside them.
        boolean hasCompanionOut = epicMobs.getEpicMobs().stream()
                .anyMatch(other -> event.getKiller().getUniqueId().equals(other.owner())
                        && other.alive());

        if (hasCompanionOut && mob.boss()) {
            event.getKiller().sendMessage(ChatColor.LIGHT_PURPLE
                    + "Your companion shares in the kill.");
        }
    }

    private void applyBondBuff(Player owner, int level) {
        int amplifier = Math.min(2, Math.max(0, level / 5));

        owner.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, BUFF_DURATION_TICKS, amplifier, true, false));
        owner.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, BUFF_DURATION_TICKS, amplifier, true, false));
    }
}
