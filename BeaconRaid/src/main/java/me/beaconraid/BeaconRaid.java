package me.beaconraid;

import org.bukkit.plugin.java.JavaPlugin;

public final class BeaconRaid extends JavaPlugin {

    private static BeaconRaid instance;
    private GameManager gameManager;
    private AutoSpawnManager autoSpawnManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        gameManager = new GameManager(this);
        autoSpawnManager = new AutoSpawnManager(this, gameManager);

        BeaconRaidCommand commandExecutor = new BeaconRaidCommand(gameManager, autoSpawnManager);
        if (getCommand("beaconraid") != null) {
            getCommand("beaconraid").setExecutor(commandExecutor);
            getCommand("beaconraid").setTabCompleter(commandExecutor);
        } else {
            getLogger().severe("Команда beaconraid не найдена в plugin.yml! Плагин не будет работать корректно.");
        }

        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new LootProtectionListener(), this);

        // Запускаем автоспавн если включен
        if (getConfig().getBoolean("auto-spawn.enabled", true)) {
            autoSpawnManager.start();
            getLogger().info("Авто-спавн маяка запущен. Интервал: " +
                    getConfig().getInt("auto-spawn.interval-minutes", 20) + " минут.");
        }

        getLogger().info("BeaconRaid успешно запущен.");
    }

    @Override
    public void onDisable() {
        if (autoSpawnManager != null) {
            autoSpawnManager.stop();
        }
        if (gameManager != null) {
            gameManager.cleanup();
        }
        getLogger().info("BeaconRaid выключен.");
    }

    public static BeaconRaid getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
