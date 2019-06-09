package com.kicas.rp;

import com.kicas.rp.command.CommandHandler;
import com.kicas.rp.data.DataManager;
import com.kicas.rp.data.RegionFlag;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class RegionProtection extends JavaPlugin {
    private final CommandHandler commandHandler;
    private final DataManager dataManager;

    private static RegionProtection instance;

    public RegionProtection() {
        this.commandHandler = new CommandHandler();
        this.dataManager = new DataManager(getDataFolder());
        instance = this;
    }

    @Override
    public void onEnable() {
        initConfig();
        commandHandler.registerCommands();
        Bukkit.getPluginManager().registerEvents(commandHandler, this);
        dataManager.load();
    }

    @Override
    public void onDisable() {
        dataManager.save();
    }

    public static RegionProtection getInstance() {
        return instance;
    }

    public static DataManager getDataManager() {
        return instance.dataManager;
    }

    public static void log(Object x) {
        Bukkit.getLogger().info("[FLv1] - " + Objects.toString(x));
    }

    public static void error(Object x) {
        String msg = Objects.toString(x);
        Bukkit.getLogger().severe("[FLv1] - " + msg);
    }

    private void initConfig() {
        FileConfiguration config = getConfig();
        saveConfig();

        RegionFlag.registerDefaults(config);
    }
}
