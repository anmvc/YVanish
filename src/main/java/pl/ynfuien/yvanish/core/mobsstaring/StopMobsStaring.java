package pl.ynfuien.yvanish.core.mobsstaring;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import pl.ynfuien.yvanish.YVanish;
import pl.ynfuien.yvanish.config.PluginConfig;
import pl.ynfuien.yvanish.core.VanishManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StopMobsStaring {
    private static YVanish instance;
    private static VanishManager vanishManager;

    private static final float HIGHEST_RANGE = LookGoalProvider.getHighestRange();
    private static final MobGoals mobGoals = Bukkit.getMobGoals();
    private static final GoalKey<Mob>[] LOOK_GOALS = new GoalKey[] {
            VanillaGoal.LOOK_AT_PLAYER,
            VanillaGoal.FOX_LOOK_AT_PLAYER,
            VanillaGoal.PANDA_LOOK_AT_PLAYER,
            VanillaGoal.INTERACT // Wandering trader
    };
    public static GoalKey<Mob> CUSTOM_LOOK_GOAL = null;

    private static ScheduledTask interval = null;
    // ConcurrentHashMap, because on Folia mobs get modified
    // on their own region threads, and this map is read from others
    private static final Map<Mob, List<Goal<Mob>>> temporaryModified = new ConcurrentHashMap<>();

    // https://github.com/PaperMC/Paper/issues/10743
    // It's just a lazy check whether version is 1.20.6 or higher.
    // No need for more complex stuff.
    private static final boolean PAPER_FIX = Bukkit.getUnsafe().getProtocolVersion() >= 766;

    public static void setup(YVanish instance) {
        StopMobsStaring.instance = instance;
        vanishManager = instance.getVanishManager();

        CUSTOM_LOOK_GOAL = GoalKey.of(Mob.class, new NamespacedKey(instance, "look_at_player"));
    }

    public static void startInterval() {
        if (!PluginConfig.mobsNoStaring) return;

        interval = Bukkit.getGlobalRegionScheduler().runAtFixedRate(instance, (task) -> {
            // Snapshot of vanished players and their locations,
            // used on other region threads to check whether mobs are still in range
            List<Player> players = new ArrayList<>(vanishManager.getVanishedPlayers());
            List<Location> playerLocations = new ArrayList<>();
            for (Player p : players) playerLocations.add(p.getLocation());

            // Collect nearby mobs on each player's own region thread
            for (Player p : players) {
                p.getScheduler().run(instance, (t) -> {
                    if (!p.isOnline()) return;

                    Collection<LivingEntity> entities = p.getLocation().getNearbyLivingEntities(HIGHEST_RANGE);
                    for (LivingEntity entity : entities) {
                        if (!(entity instanceof Mob mob)) continue;
                        if (temporaryModified.containsKey(mob)) continue;

                        makeMobSpecial(mob);
                    }
                }, () -> {});
            }

            // Check modified mobs on their own region threads
            for (Mob mob : new HashSet<>(temporaryModified.keySet())) {
                checkMobStillInRange(mob, playerLocations);
            }
        }, 5, 5);

    }

    public static void stopInterval() {
        if (interval == null) return;

        interval.cancel();
        interval = null;

        // Restore modified mobs on their own region threads
        for (Mob mob : new HashSet<>(temporaryModified.keySet())) {
            List<Goal<Mob>> goals = temporaryModified.remove(mob);
            if (goals == null) continue;

            makeMobNotSoSpecial(mob, goals);
        }
    }

    /**
     * Removes vanilla look goals; adds custom one.
     */
    private static void makeMobSpecial(Mob mob) {
        // Mob's scheduler is used, so that its goals
        // are modified on the region the mob is in (Folia)
        mob.getScheduler().run(instance, (task) -> {
            if (mob.isDead()) return;
            if (temporaryModified.containsKey(mob)) return;

            // Get look goals
            List<Goal<Mob>> goals = new ArrayList<>();
            for (GoalKey<Mob> goalKey : LOOK_GOALS) {
                goals.addAll(mobGoals.getGoals(mob, goalKey));
            }
            if (goals.isEmpty()) return;

            // Remove them
            for (Goal<Mob> goal : goals) mobGoals.removeGoal(mob, goal);
            if (PAPER_FIX) temporaryModified.put(mob, goals);

            // Add a custom one
            mobGoals.addGoal(mob, LookGoalProvider.getPriority(mob), LookGoalProvider.getGoal(vanishManager, mob));
        }, () -> temporaryModified.remove(mob));
    }

    /**
     * Checks whether a modified mob is dead or out of range,
     * and if so, restores its vanilla goals.
     */
    private static void checkMobStillInRange(Mob mob, List<Location> playerLocations) {
        // Mob's scheduler is used, so that its goals and position
        // are accessed on the region the mob is in (Folia)
        mob.getScheduler().run(instance, (task) -> {
            List<Goal<Mob>> goals = temporaryModified.get(mob);
            if (goals == null) return;

            boolean inRange = false;
            Location mobLocation = mob.getLocation();
            for (Location playerLocation : playerLocations) {
                if (playerLocation.getWorld() != mobLocation.getWorld()) continue;
                if (playerLocation.distanceSquared(mobLocation) <= HIGHEST_RANGE * HIGHEST_RANGE) {
                    inRange = true;
                    break;
                }
            }

            if (!mob.isDead() && inRange) return;

            temporaryModified.remove(mob);
            makeMobNotSoSpecial(mob, goals);
        }, () -> temporaryModified.remove(mob));
    }

    /**
     * Removes custom look goal; adds vanilla ones back.
     */
    private static void makeMobNotSoSpecial(Mob mob, List<Goal<Mob>> goals) {
        if (!PAPER_FIX) return;

        // Remove custom goal
        mobGoals.removeGoal(mob, CUSTOM_LOOK_GOAL);
        // Add original ones back
        for (Goal<Mob> goal: goals) {
            mobGoals.addGoal(mob, LookGoalProvider.getPriority(mob), goal);
        }
    }
}
