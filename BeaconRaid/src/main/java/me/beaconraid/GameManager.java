package me.beaconraid;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {

    private final BeaconRaid plugin;
    private Location beaconLocation;
    private ArmorStand hologramTimer;
    private ArmorStand[] hologramScores = new ArmorStand[3]; // топ-3
    private final Map<UUID, Integer> scores = new HashMap<>();
    private boolean gameRunning = false;
    private boolean chestsUnlocked = false;
    private int timeLeft;
    private BukkitTask gameTimerTask;
    private BukkitTask scoreTask;
    private BukkitTask despawnTask;
    private Chest chest1;
    private Chest chest2;

    // Спавн-координаты для сообщения
    private int spawnX, spawnY, spawnZ;

    public GameManager(BeaconRaid plugin) {
        this.plugin = plugin;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public boolean isChestsUnlocked() {
        return chestsUnlocked;
    }

    public Location getBeaconLocation() {
        return beaconLocation;
    }

    public void spawnBeacon(Location location) {
        if (gameRunning) return;
        if (location == null) return;

        // Защита: проверяем мир
        World world = location.getWorld();
        if (world == null) return;

        // Очищаем предыдущее если есть
        cleanup();

        // Округляем координаты
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        beaconLocation = new Location(world, baseX + 0.5, baseY, baseZ + 0.5);

        // Сохраняем для сообщений
        spawnX = baseX;
        spawnY = baseY;
        spawnZ = baseZ;

        // Строим основание 5x5 из тёмного призмарина
        for (int x = baseX - 2; x <= baseX + 2; x++) {
            for (int z = baseZ - 2; z <= baseZ + 2; z++) {
                Block block = world.getBlockAt(x, baseY - 1, z);
                block.setType(Material.DARK_PRISMARINE);
            }
        }

        // Пирамида 3x3 из золотых блоков на основании
        for (int x = baseX - 1; x <= baseX + 1; x++) {
            for (int z = baseZ - 1; z <= baseZ + 1; z++) {
                Block block = world.getBlockAt(x, baseY, z);
                block.setType(Material.GOLD_BLOCK);
            }
        }

        // Маяк в центре
        Block beaconBlock = world.getBlockAt(baseX, baseY + 1, baseZ);
        beaconBlock.setType(Material.BEACON);

        // Сундук слева (запад)
        Block chestBlock1 = world.getBlockAt(baseX - 2, baseY, baseZ);
        chestBlock1.setType(Material.CHEST);
        if (chestBlock1.getState() instanceof Chest) {
            chest1 = (Chest) chestBlock1.getState();
            chest1.setCustomName("Сундук маяка #1");
            chest1.update();
            fillChestWithLoot(chest1.getInventory(), "chest1");
        }

        // Сундук справа (восток)
        Block chestBlock2 = world.getBlockAt(baseX + 2, baseY, baseZ);
        chestBlock2.setType(Material.CHEST);
        if (chestBlock2.getState() instanceof Chest) {
            chest2 = (Chest) chestBlock2.getState();
            chest2.setCustomName("Сундук маяка #2");
            chest2.update();
            fillChestWithLoot(chest2.getInventory(), "chest2");
        }

        // Hologram-таймер над маяком
        Location hologramLoc = new Location(world, baseX + 0.5, baseY + 2.5, baseZ + 0.5);
        hologramTimer = (ArmorStand) world.spawnEntity(hologramLoc, EntityType.ARMOR_STAND);
        hologramTimer.setVisible(false);
        hologramTimer.setCustomNameVisible(true);
        hologramTimer.setGravity(false);
        hologramTimer.setMarker(true);
        hologramTimer.setInvulnerable(true);
        hologramTimer.setCustomName(formatTime(plugin.getConfig().getInt("game-duration-minutes", 10) * 60));

        // Hologram-ы для топ-3 игроков
        for (int i = 0; i < 3; i++) {
            Location scoreLoc = new Location(world, baseX + 0.5, baseY + 2.9 + (i * 0.3), baseZ + 0.5);
            ArmorStand scoreStand = (ArmorStand) world.spawnEntity(scoreLoc, EntityType.ARMOR_STAND);
            scoreStand.setVisible(false);
            scoreStand.setCustomNameVisible(true);
            scoreStand.setGravity(false);
            scoreStand.setMarker(true);
            scoreStand.setInvulnerable(true);
            scoreStand.setCustomName("");
            hologramScores[i] = scoreStand;
        }

        // Запускаем игру
        scores.clear();
        chestsUnlocked = false;
        gameRunning = true;
        int durationMinutes = plugin.getConfig().getInt("game-duration-minutes", 10);
        timeLeft = durationMinutes * 60;

        // Отправляем сообщение о спавне
        String spawnMsg = plugin.getConfig().getString("messages.spawned", "&6Маяк заспавнился!")
                .replace("%x%", String.valueOf(spawnX))
                .replace("%y%", String.valueOf(spawnY))
                .replace("%z%", String.valueOf(spawnZ));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', spawnMsg));

        // Таск начисления очков (каждую секунду)
        scoreTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) {
                    cancel();
                    return;
                }
                int zoneRadius = plugin.getConfig().getInt("zone-radius", 10);
                int pointsPerSecond = plugin.getConfig().getInt("points-per-second", 1);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().equals(beaconLocation.getWorld()) &&
                            player.getLocation().distance(beaconLocation) <= zoneRadius) {
                        addScore(player.getUniqueId(), pointsPerSecond);
                    }
                }
                updateTopHolograms();
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // Таск обратного отсчёта
        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) {
                    cancel();
                    return;
                }
                timeLeft--;
                if (hologramTimer != null && hologramTimer.isValid()) {
                    hologramTimer.setCustomName(formatTime(timeLeft));
                }
                if (timeLeft <= 0) {
                    unlockChests();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void unlockChests() {
        chestsUnlocked = true;
        gameRunning = false;

        if (scoreTask != null && !scoreTask.isCancelled()) {
            scoreTask.cancel();
        }

        // Сообщение о разблокировке
        String unlockedMsg = plugin.getConfig().getString("messages.chests-unlocked", "&eСундуки открыты!");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', unlockedMsg));

        // Определяем победителя
        UUID winnerId = null;
        int maxScore = -1;
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winnerId = entry.getKey();
            }
        }
        if (winnerId != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerId);
            String winnerMsg = plugin.getConfig().getString("messages.winner-message", "&aПобедитель: %player%")
                    .replace("%player%", winner.getName() != null ? winner.getName() : "Неизвестный")
                    .replace("%score%", String.valueOf(maxScore));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', winnerMsg));
        }

        // Запускаем таск на удаление маяка через 1 минуту
        despawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                despawnBeacon();
            }
        }.runTaskLater(plugin, 20L * 60);
    }

    public void despawnBeacon() {
        if (beaconLocation == null) return;
        World world = beaconLocation.getWorld();
        if (world == null) return;

        int bx = beaconLocation.getBlockX();
        int by = beaconLocation.getBlockY();
        int bz = beaconLocation.getBlockZ();

        // Удаляем структуру (основание 5x5, пирамида 3x3, маяк, сундуки)
        for (int x = bx - 2; x <= bx + 2; x++) {
            for (int z = bz - 2; z <= bz + 2; z++) {
                Block block1 = world.getBlockAt(x, by - 1, z);
                if (block1.getType() == Material.DARK_PRISMARINE) {
                    block1.setType(Material.AIR);
                }
            }
        }
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                Block block2 = world.getBlockAt(x, by, z);
                if (block2.getType() == Material.GOLD_BLOCK) {
                    block2.setType(Material.AIR);
                }
            }
        }
        Block beaconBlock = world.getBlockAt(bx, by + 1, bz);
        if (beaconBlock.getType() == Material.BEACON) {
            beaconBlock.setType(Material.AIR);
        }
        Block chestBlock1 = world.getBlockAt(bx - 2, by, bz);
        if (chestBlock1.getType() == Material.CHEST) {
            chestBlock1.setType(Material.AIR);
        }
        Block chestBlock2 = world.getBlockAt(bx + 2, by, bz);
        if (chestBlock2.getType() == Material.CHEST) {
            chestBlock2.setType(Material.AIR);
        }

        // Удаляем ArmorStand-ы
        removeHolograms();

        // Сообщение
        String despawnMsg = plugin.getConfig().getString("messages.beacon-despawned", "&eМаяк исчез!");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', despawnMsg));

        chestsUnlocked = false;
        gameRunning = false;
        beaconLocation = null;
        chest1 = null;
        chest2 = null;

        if (despawnTask != null && !despawnTask.isCancelled()) {
            despawnTask.cancel();
        }
    }

    private void removeHolograms() {
        if (hologramTimer != null && hologramTimer.isValid()) {
            hologramTimer.remove();
            hologramTimer = null;
        }
        for (int i = 0; i < hologramScores.length; i++) {
            if (hologramScores[i] != null && hologramScores[i].isValid()) {
                hologramScores[i].remove();
                hologramScores[i] = null;
            }
        }
    }

    public void addScore(UUID playerId, int points) {
        scores.put(playerId, scores.getOrDefault(playerId, 0) + points);
    }

    public int getScore(UUID playerId) {
        return scores.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    public void setScore(UUID playerId, int score) {
        scores.put(playerId, score);
    }

    private void updateTopHolograms() {
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        for (int i = 0; i < 3; i++) {
            if (hologramScores[i] != null && hologramScores[i].isValid()) {
                if (i < sorted.size()) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(sorted.get(i).getKey());
                    String name = player.getName() != null ? player.getName() : "???";
                    hologramScores[i].setCustomName(ChatColor.GOLD + "#" + (i + 1) + " " + name + ": " + sorted.get(i).getValue());
                } else {
                    hologramScores[i].setCustomName("");
                }
            }
        }
    }

    private void fillChestWithLoot(Inventory inventory, String chestPath) {
        inventory.clear();
        if (!plugin.getConfig().contains("loot." + chestPath)) return;
        Random random = new Random();
        for (String key : plugin.getConfig().getConfigurationSection("loot." + chestPath).getKeys(false)) {
            double chance = plugin.getConfig().getDouble("loot." + chestPath + "." + key, 0);
            if (random.nextDouble() * 100 < chance) {
                Material material = Material.getMaterial(key.toUpperCase());
                if (material != null) {
                    inventory.addItem(new ItemStack(material, 1));
                }
            }
        }
    }

    private String formatTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format(ChatColor.YELLOW + "%02d:%02d", min, sec);
    }

    public void stopGame() {
        if (gameTimerTask != null && !gameTimerTask.isCancelled()) {
            gameTimerTask.cancel();
        }
        if (scoreTask != null && !scoreTask.isCancelled()) {
            scoreTask.cancel();
        }
        despawnBeacon();
    }

    public void cleanup() {
        stopGame();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
    }
}
