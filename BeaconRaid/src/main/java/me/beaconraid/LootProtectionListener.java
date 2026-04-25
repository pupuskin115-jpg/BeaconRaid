package me.beaconraid;

import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.ChatColor;
import org.bukkit.event.inventory.InventoryType;

public class LootProtectionListener implements Listener {

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (event.getInventory().getType() != InventoryType.CHEST) return;
        if (!(event.getInventory().getHolder() instanceof Chest)) return;

        GameManager gameManager = BeaconRaid.getInstance() != null ?
                ((BeaconRaid) BeaconRaid.getInstance()).getGameManager() : null;
        if (gameManager == null) return;

        Location beaconLoc = gameManager.getBeaconLocation();
        if (beaconLoc == null) return;

        Chest chest = (Chest) event.getInventory().getHolder();
        Location chestLoc = chest.getLocation();

        if (chestLoc == null || !chestLoc.getWorld().equals(beaconLoc.getWorld())) return;

        int bx = beaconLoc.getBlockX();
        int by = beaconLoc.getBlockY();
        int bz = beaconLoc.getBlockZ();

        // Проверяем, является ли сундук одним из сундуков маяка
        boolean isBeaconChest = (Math.abs(chestLoc.getBlockX() - bx) == 2 && chestLoc.getBlockY() == by && chestLoc.getBlockZ() == bz);

        if (isBeaconChest && !gameManager.isChestsUnlocked()) {
            event.setCancelled(true);
            ((Player) event.getPlayer()).sendMessage(ChatColor.RED + "Сундуки заблокированы до окончания битвы за маяк!");
        }
    }
}
