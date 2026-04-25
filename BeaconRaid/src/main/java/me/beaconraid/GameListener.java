package me.beaconraid;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {

    private final GameManager gameManager;
    private final Set<UUID> playersInZone = new HashSet<>();

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameManager.isGameRunning() && !gameManager.isChestsUnlocked()) return;
        Location beaconLoc = gameManager.getBeaconLocation();
        if (beaconLoc == null) return;

        Player player = event.getPlayer();
        if (!player.getWorld().equals(beaconLoc.getWorld())) return;

        int zoneRadius = BeaconRaid.getInstance().getConfig().getInt("zone-radius", 10);
        boolean inZoneNow = player.getLocation().distance(beaconLoc) <= zoneRadius;
        boolean wasInZone = playersInZone.contains(player.getUniqueId());

        if (inZoneNow && !wasInZone) {
            playersInZone.add(player.getUniqueId());
            if (gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        BeaconRaid.getInstance().getConfig().getString("messages.entering-zone", "&aВы вошли в зону маяка!")));
            }
        } else if (!inZoneNow && wasInZone) {
            playersInZone.remove(player.getUniqueId());
            if (gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        BeaconRaid.getInstance().getConfig().getString("messages.leaving-zone", "&cВы покинули зону маяка!")));
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!gameManager.isGameRunning()) return;
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;

        Player damager = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();
        Location beaconLoc = gameManager.getBeaconLocation();
        if (beaconLoc == null) return;

        int zoneRadius = BeaconRaid.getInstance().getConfig().getInt("zone-radius", 10);
        if (damager.getLocation().distance(beaconLoc) <= zoneRadius ||
                victim.getLocation().distance(beaconLoc) <= zoneRadius) {

            // Если жертва умирает, даём бонус убийце
            if (victim.getHealth() - event.getFinalDamage() <= 0) {
                int killBonus = BeaconRaid.getInstance().getConfig().getInt("kill-bonus-points", 15);
                gameManager.addScore(damager.getUniqueId(), killBonus);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location beaconLoc = gameManager.getBeaconLocation();
        if (beaconLoc == null) return;
        if (event.getBlock().getWorld() == null || !event.getBlock().getWorld().equals(beaconLoc.getWorld())) return;

        int bx = beaconLoc.getBlockX();
        int by = beaconLoc.getBlockY();
        int bz = beaconLoc.getBlockZ();
        int bx2 = event.getBlock().getX();
        int by2 = event.getBlock().getY();
        int bz2 = event.getBlock().getZ();

        // Проверяем: блок в радиусе структуры маяка?
        if (Math.abs(bx2 - bx) <= 2 && Math.abs(bz2 - bz) <= 2 &&
                by2 >= by - 1 && by2 <= by + 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Location beaconLoc = gameManager.getBeaconLocation();
        if (beaconLoc == null) return;
        event.blockList().removeIf(block -> {
            if (block.getWorld() == null || !block.getWorld().equals(beaconLoc.getWorld())) return false;
            int bx = beaconLoc.getBlockX();
            int by = beaconLoc.getBlockY();
            int bz = beaconLoc.getBlockZ();
            return Math.abs(block.getX() - bx) <= 2 && Math.abs(block.getZ() - bz) <= 2 &&
                    block.getY() >= by - 1 && block.getY() <= by + 1;
        });
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Location beaconLoc = gameManager.getBeaconLocation();
        if (beaconLoc == null) return;
        event.blockList().removeIf(block -> {
            if (block.getWorld() == null || !block.getWorld().equals(beaconLoc.getWorld())) return false;
            int bx = beaconLoc.getBlockX();
            int by = beaconLoc.getBlockY();
            int bz = beaconLoc.getBlockZ();
            return Math.abs(block.getX() - bx) <= 2 && Math.abs(block.getZ() - bz) <= 2 &&
                    block.getY() >= by - 1 && block.getY() <= by + 1;
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersInZone.remove(event.getPlayer().getUniqueId());
    }
}
