package me.beaconraid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

public class AutoSpawnManager {

    private final BeaconRaid plugin;
    private final GameManager gameManager;
    private BukkitTask spawnTask;
    private final Random random = new Random();

    public AutoSpawnManager(BeaconRaid plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    public void start() {
        int intervalMinutes = plugin.getConfig().getInt("auto-spawn.interval-minutes", 20);
        long intervalTicks = intervalMinutes * 60L * 20L;

        spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameManager.isGameRunning()) {
                    Location spawnLocation = findRandomSafeLocation();
                    if (spawnLocation != null) {
                        gameManager.spawnBeacon(spawnLocation);
                    } else {
                        plugin.getLogger().warning("Не удалось найти безопасное место для спавна маяка.");
                    }
                } else {
                    plugin.getLogger().info("Авто-спавн пропущен: маяк уже активен.");
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    private Location findRandomSafeLocation() {
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return null;

        int radius = 500;
        int attempts = 50;
        for (int i = 0; i < attempts; i++) {
            int x = random.nextInt(radius * 2) - radius;
            int z = random.nextInt(radius * 2) - radius;
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            Block block = loc.getBlock();
            if (block.getType().isSolid() && !block.isLiquid()) {
                loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                return loc;
            }
        }
        return null;
    }
}
