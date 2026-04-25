package me.beaconraid;

import org.bukkit.*;
import org.bukkit.block.*;
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
    private final ArmorStand[] hologramScores = new ArmorStand[3];
    private final Map<UUID, Integer> scores = new HashMap<>();
    private boolean gameRunning = false;
    private boolean chestsUnlocked = false;
    private int timeLeft;
    private BukkitTask gameTimerTask;
    private BukkitTask scoreTask;
    private BukkitTask despawnTask;
    private Chest chest1;
    private Chest chest2;
    private String currentRarity = "common";
    private int spawnX, spawnY, spawnZ;

    public GameManager(BeaconRaid plugin) {
        this.plugin = plugin;
    }

    public boolean isGameRunning() { return gameRunning; }
    public boolean isChestsUnlocked() { return chestsUnlocked; }
    public Location getBeaconLocation() { return beaconLocation; }

    public void spawnBeacon(Location location) {
        if (gameRunning) return;
        if (location == null) return;

        World world = location.getWorld();
        if (world == null) return;

        cleanup();

        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        beaconLocation = new Location(world, bx + 0.5, by, bz + 0.5);
        spawnX = bx; spawnY = by; spawnZ = bz;

        // Редкость
        String configuredRarity = plugin.getConfig().getString("beacon-rarity", "common").toLowerCase();
        List<String> rarities = Arrays.asList("common", "rare", "epic", "legendary");
        currentRarity = rarities.contains(configuredRarity) ? configuredRarity : "common";

        // Строим основание 5x5 из железных блоков
        for (int x = bx - 2; x <= bx + 2; x++) {
            for (int z = bz - 2; z <= bz + 2; z++) {
                world.getBlockAt(x, by - 1, z).setType(Material.IRON_BLOCK);
            }
        }

        // Пирамида 4 уровня (максимальная): 9x9, 7x7, 5x5, 3x3 из железных блоков
        buildPyramidLayer(world, bx, by, bz, 0, 3); // 9x9
        buildPyramidLayer(world, bx, by + 1, bz, 1, 3); // 7x7
        buildPyramidLayer(world, bx, by + 2, bz, 2, 3); // 5x5
        buildPyramidLayer(world, bx, by + 3, bz, 3, 3); // 3x3

        // Маяк (без активации — просто блок)
        Block beaconBlock = world.getBlockAt(bx, by + 4, bz);
        beaconBlock.setType(Material.BEACON);

        // Сундуки
        Block c1 = world.getBlockAt(bx - 4, by + 3, bz);
        c1.setType(Material.CHEST);
        if (c1.getState() instanceof Chest) {
            chest1 = (Chest) c1.getState();
            chest1.setCustomName("Сундук #1 (" + currentRarity + ")");
            chest1.update();
            fillChest(chest1.getInventory(), "chest1");
        }

        Block c2 = world.getBlockAt(bx + 4, by + 3, bz);
        c2.setType(Material.CHEST);
        if (c2.getState() instanceof Chest) {
            chest2 = (Chest) c2.getState();
            chest2.setCustomName("Сундук #2 (" + currentRarity + ")");
            chest2.update();
            fillChest(chest2.getInventory(), "chest2");
        }

        // Голограммы
        hologramTimer = spawnHologram(world, bx + 0.5, by + 5.5, bz + 0.5, formatTime(plugin.getConfig().getInt("game-duration-minutes", 10) * 60));
        for (int i = 0; i < 3; i++) {
            hologramScores[i] = spawnHologram(world, bx + 0.5, by + 5.9 + i * 0.3, bz + 0.5, "");
        }

        scores.clear();
        chestsUnlocked = false;
        gameRunning = true;
        timeLeft = plugin.getConfig().getInt("game-duration-minutes", 10) * 60;

        String msg = plugin.getConfig().getString("messages.spawned", "")
                .replace("%x%", String.valueOf(spawnX))
                .replace("%y%", String.valueOf(spawnY))
                .replace("%z%", String.valueOf(spawnZ))
                .replace("%rarity%", currentRarity);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));

        scoreTask = new BukkitRunnable() {
            public void run() {
                if (!gameRunning) { cancel(); return; }
                int r = plugin.getConfig().getInt("zone-radius", 10);
                int pps = plugin.getConfig().getInt("points-per-second", 1);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(beaconLocation.getWorld()) && p.getLocation().distance(beaconLocation) <= r) {
                        addScore(p.getUniqueId(), pps);
                    }
                }
                updateTop();
            }
        }.runTaskTimer(plugin, 0L, 20L);

        gameTimerTask = new BukkitRunnable() {
            public void run() {
                if (!gameRunning) { cancel(); return; }
                timeLeft--;
                if (hologramTimer != null && hologramTimer.isValid()) hologramTimer.setCustomName(formatTime(timeLeft));
                if (timeLeft <= 0) { unlockChests(); cancel(); }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void buildPyramidLayer(World w, int cx, int y, int cz, int layer, int maxLayer) {
        int size = (maxLayer - layer) * 2 + 1;
        int offset = (maxLayer - layer);
        for (int x = cx - offset; x <= cx + offset; x++) {
            for (int z = cz - offset; z <= cz + offset; z++) {
                w.getBlockAt(x, y, z).setType(Material.IRON_BLOCK);
            }
        }
    }

    private ArmorStand spawnHologram(World w, double x, double y, double z, String text) {
        ArmorStand as = (ArmorStand) w.spawnEntity(new Location(w, x, y, z), EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setCustomNameVisible(true);
        as.setGravity(false);
        as.setMarker(true);
        as.setInvulnerable(true);
        as.setCustomName(text);
        return as;
    }

    private void fillChest(Inventory inv, String chestName) {
        inv.clear();
        String path = "loot." + currentRarity + "." + chestName;
        if (!plugin.getConfig().contains(path)) return;
        Random rand = new Random();
        for (String entry : plugin.getConfig().getStringList(path)) {
            String[] parts = entry.split(":");
            if (parts.length < 3) continue;
            Material mat = Material.getMaterial(parts[0].toUpperCase());
            if (mat == null) continue;
            int amount;
            double chance;
            try {
                amount = Integer.parseInt(parts[1]);
                chance = Double.parseDouble(parts[2]);
            } catch (NumberFormatException e) { continue; }
            if (rand.nextDouble() * 100 < chance) {
                inv.addItem(new ItemStack(mat, amount));
            }
        }
    }

    private void unlockChests() {
        chestsUnlocked = true;
        gameRunning = false;
        if (scoreTask != null) scoreTask.cancel();
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.chests-unlocked", "")));
        UUID winner = null;
        int max = -1;
        for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); winner = e.getKey(); }
        }
        if (winner != null) {
            OfflinePlayer wp = Bukkit.getOfflinePlayer(winner);
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.winner-message", "")
                            .replace("%player%", wp.getName() != null ? wp.getName() : "???")
                            .replace("%score%", String.valueOf(max))));
        }
        despawnTask = new BukkitRunnable() { public void run() { despawnBeacon(); } }.runTaskLater(plugin, 1200L);
    }

    public void despawnBeacon() {
        if (beaconLocation == null) return;
        World w = beaconLocation.getWorld();
        if (w == null) return;
        int bx = beaconLocation.getBlockX(), by = beaconLocation.getBlockY(), bz = beaconLocation.getBlockZ();
        for (int y = by - 1; y <= by + 4; y++) {
            for (int x = bx - 4; x <= bx + 4; x++) {
                for (int z = bz - 4; z <= bz + 4; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.IRON_BLOCK || b.getType() == Material.BEACON || b.getType() == Material.CHEST) {
                        b.setType(Material.AIR);
                    }
                }
            }
        }
        removeHolograms();
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.beacon-despawned", "")));
        chestsUnlocked = false; gameRunning = false; beaconLocation = null; chest1 = null; chest2 = null;
        if (despawnTask != null) despawnTask.cancel();
    }

    private void removeHolograms() {
        if (hologramTimer != null && hologramTimer.isValid()) { hologramTimer.remove(); hologramTimer = null; }
        for (ArmorStand as : hologramScores) { if (as != null && as.isValid()) as.remove(); }
    }

    public void addScore(UUID id, int pts) { scores.put(id, scores.getOrDefault(id, 0) + pts); }
    public int getScore(UUID id) { return scores.getOrDefault(id, 0); }
    public Map<UUID, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    private void updateTop() {
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        for (int i = 0; i < 3; i++) {
            if (hologramScores[i] != null && hologramScores[i].isValid()) {
                hologramScores[i].setCustomName(i < sorted.size()
                        ? ChatColor.GOLD + "#" + (i + 1) + " " + Bukkit.getOfflinePlayer(sorted.get(i).getKey()).getName() + ": " + sorted.get(i).getValue()
                        : "");
            }
        }
    }

    private String formatTime(int sec) { return ChatColor.YELLOW + String.format("%02d:%02d", sec / 60, sec % 60); }

    public void stopGame() {
        if (gameTimerTask != null) gameTimerTask.cancel();
        if (scoreTask != null) scoreTask.cancel();
        despawnBeacon();
    }

    public void cleanup() { stopGame(); }
}
