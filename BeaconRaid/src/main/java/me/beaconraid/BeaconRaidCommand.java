package me.beaconraid;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BeaconRaidCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;
    private final AutoSpawnManager autoSpawnManager;

    public BeaconRaidCommand(GameManager gameManager, AutoSpawnManager autoSpawnManager) {
        this.gameManager = gameManager;
        this.autoSpawnManager = autoSpawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только игроки могут использовать эту команду.");
                    return true;
                }
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                Player player = (Player) sender;
                gameManager.spawnBeacon(player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Маяк заспавнен вручную.");
                break;

            case "start":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только игроки могут использовать эту команду.");
                    return true;
                }
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                Player p2 = (Player) sender;
                gameManager.spawnBeacon(p2.getLocation());
                break;

            case "stop":
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                gameManager.stopGame();
                sender.sendMessage(ChatColor.GREEN + "Маяк удалён.");
                break;

            case "setloot":
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                if (args.length < 3)
