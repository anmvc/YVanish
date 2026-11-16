package pl.ynfuien.yvanish.core;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import pl.ynfuien.ydevlib.messages.colors.ColorFormatter;
import pl.ynfuien.yvanish.YVanish;
import pl.ynfuien.yvanish.config.PluginConfig;
import pl.ynfuien.yvanish.data.Storage;
import pl.ynfuien.yvanish.data.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ActionAndBossBars {
    private static YVanish instance;
    private static VanishManager vanishManager;

    private static ScheduledTask actionBarInterval = null;
    private static ScheduledTask bossBarInterval = null;
    // ConcurrentHashMap, because intervals run asynchronously,
    // while show/hide can be also called from region threads
    private static final Map<Player, BossBar> playerBossBars = new ConcurrentHashMap<>();

    public static void setup(YVanish instance) {
        ActionAndBossBars.instance = instance;
        vanishManager = instance.getVanishManager();
    }

    public static void startIntervals() {
        // 1 tick = 50ms
        actionBarInterval = Bukkit.getAsyncScheduler().runAtFixedRate(instance, (task) -> {
            if (vanishManager.isNoOneVanished()) return;

            for (Player p : vanishManager.getVanishedPlayers()) sendActionBar(p);
        }, 100, PluginConfig.actionBarRefreshRate * 50L, TimeUnit.MILLISECONDS);


        // Boss bar
        int rate = PluginConfig.bossBarRefreshRate;
        if (rate < 0) {
            if (vanishManager.isNoOneVanished()) return;
            for (Player p : vanishManager.getVanishedPlayers()) showBossBar(p);

            return;
        }

        bossBarInterval = Bukkit.getAsyncScheduler().runAtFixedRate(instance, (task) -> {
            if (vanishManager.isNoOneVanished()) return;

            for (Player p : vanishManager.getVanishedPlayers()) showBossBar(p);
        }, 100, rate * 50L, TimeUnit.MILLISECONDS);
    }

    public static void stopIntervals() {
        if (actionBarInterval != null) actionBarInterval.cancel();
        if (bossBarInterval != null) bossBarInterval.cancel();

        for (Player p : playerBossBars.keySet()) {
            p.hideBossBar(playerBossBars.get(p));
        }
        playerBossBars.clear();
    }

    public static void updateForPlayer(Player player) {
        if (!vanishManager.isVanished(player)) {
            sendEmptyActionBar(player);
            hideBossBar(player);
            return;
        }

        sendActionBar(player);
        showBossBar(player);
    }

    public static void sendActionBar(Player player) {
        if (!player.isOnline()) return;
        if (!player.isConnected()) return;

        User user = Storage.getUser(player.getUniqueId());
        if (!user.getActionBar()) return;

        String message = PluginConfig.actionBarMessage;
        Component formatted = ColorFormatter.SERIALIZER.deserialize(ColorFormatter.parsePAPI(player, message));

        player.sendActionBar(formatted);
    }

    public static void sendEmptyActionBar(Player player) {
        if (!player.isOnline()) return;

        player.sendActionBar(Component.empty());
    }

    public static void showBossBar(Player player) {
        if (!player.isOnline()) return;

        User user = Storage.getUser(player.getUniqueId());
        if (!user.getBossBar()) return;

        String title = PluginConfig.bossBarTitle;
        Component formatted = ColorFormatter.SERIALIZER.deserialize(ColorFormatter.parsePAPI(player, title));

        if (playerBossBars.containsKey(player)) {
            BossBar bossBar = playerBossBars.get(player);
            bossBar.name(formatted);

            return;
        }

        BossBar bossBar = BossBar.bossBar(
                formatted,
                PluginConfig.bossBarProgress,
                PluginConfig.bossBarColor,
                PluginConfig.bossBarOverlay,
                PluginConfig.bossBarFlags
        );

        playerBossBars.put(player, bossBar);
        player.showBossBar(bossBar);
    }

    public static void hideBossBar(Player player) {
        BossBar bossBar = playerBossBars.get(player);
        if (bossBar == null) return;

        player.hideBossBar(bossBar);
        playerBossBars.remove(player);
    }
}
