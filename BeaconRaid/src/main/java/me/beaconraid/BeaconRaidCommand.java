package me.beaconraid;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

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
            case "start":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Только игроки могут это сделать.");
                    return true;
                }
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                gameManager.spawnBeacon(((Player) sender).getLocation());
                sender.sendMessage(ChatColor.GREEN + "Маяк заспавнен вручную.");
                break;

            case "stop":
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                gameManager.stopGame();
                sender.sendMessage(ChatColor.GREEN + "Маяк удалён.");
                break;

            case "reload":
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                BeaconRaid.getInstance().reloadConfig();
                if (autoSpawnManager != null) {
                    autoSpawnManager.stop();
                    if (BeaconRaid.getInstance().getConfig().getBoolean("auto-spawn.enabled", true)) {
                        autoSpawnManager.start();
                    }
                }
                sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен.");
                break;

            case "setloot":
                if (!sender.hasPermission("beaconraid.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Используй: /beaconraid setloot <1|2> <предмет> <шанс%>");
                    return true;
                }
                String chestNum = args[1];
                if (!chestNum.equals("1") && !chestNum.equals("2")) {
                    sender.sendMessage(ChatColor.RED + "Номер сундука: 1 или 2");
                    return true;
                }
                Material material = Material.getMaterial(args[2].toUpperCase());
                if (material == null) {
                    sender.sendMessage(ChatColor.RED + "Неизвестный предмет: " + args[2]);
                    return true;
                }
                double chance;
                try {
                    chance = Double.parseDouble(args[3]);
                    if (chance < 0 || chance > 100) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Шанс должен быть числом от 0 до 100.");
                    return true;
                }
                BeaconRaid.getInstance().getConfig().set("loot.chest" + chestNum + "." + material.name(), chance);
                BeaconRaid.getInstance().saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Лут обновлён: сундук #" + chestNum +
                        ", предмет " + material.name() + ", шанс " + chance + "%");
                break;

            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== BeaconRaid ===");
        sender.sendMessage(ChatColor.YELLOW + "/beaconraid start" + ChatColor.WHITE + " - заспавнить маяк");
        sender.sendMessage(ChatColor.YELLOW + "/beaconraid stop" + ChatColor.WHITE + " - удалить маяк");
        sender.sendMessage(ChatColor.YELLOW + "/beaconraid setloot <1|2> <предмет> <шанс%>" + ChatColor.WHITE + " - настроить лут");
        sender.sendMessage(ChatColor.YELLOW + "/beaconraid reload" + ChatColor.WHITE + " - перезагрузить конфиг");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "spawn", "setloot", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setloot")) {
            return Arrays.asList("1", "2").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setloot")) {
            return Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .map(Material::name)
                    .filter(s -> s.startsWith(args[2].toUpperCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setloot")) {
            return Arrays.asList("10", "25", "50", "75", "100").stream()
                    .filter(s -> s.startsWith(args[3]))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
