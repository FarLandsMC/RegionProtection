package com.kicas.rp;

import com.kicas.rp.command.CommandHandler;
import com.kicas.rp.data.DataManager;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.event.EntityEventHandler;
import com.kicas.rp.event.PlayerActionHandler;
import com.kicas.rp.event.PlayerEventHandler;
import com.kicas.rp.event.WorldEventHandler;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * This is the main plugin class for the Region Protection plugin. All non-utility features of the plugin can be
 * accessed through this class.
 */
public class RegionProtection extends JavaPlugin {
    private final CommandHandler commandHandler;
    private final DataManager dataManager;
    private Material claimCreationTool, claimViewer;

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
        dataManager.load();

        Bukkit.getPluginManager().registerEvents(commandHandler, this);
        Bukkit.getPluginManager().registerEvents(dataManager, this);
        Bukkit.getPluginManager().registerEvents(new PlayerActionHandler(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerEventHandler(), this);
        Bukkit.getPluginManager().registerEvents(new EntityEventHandler(), this);
        Bukkit.getPluginManager().registerEvents(new WorldEventHandler(), this);
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

    public static FileConfiguration getRPConfig() {
        return instance.getConfig();
    }

    public static Material getClaimCreationTool() {
        return instance.claimCreationTool;
    }

    public static Material getClaimViewerTool() {
        return instance.claimViewer;
    }

    public static void log(Object x) {
        Bukkit.getLogger().info("[RegionProtection] " + Objects.toString(x));
    }

    public static void error(Object x) {
        String msg = Objects.toString(x);
        Bukkit.getLogger().severe("[RegionProtection] " + msg);
    }

    private void initConfig() {
        FileConfiguration config = getConfig();
        config.addDefault("general.claim-creation-item", Material.GOLDEN_SHOVEL.name());
        config.addDefault("general.claim-viewer", Material.STICK.name());
        config.addDefault("general.minimum-claim-size", 100);
        config.options().copyDefaults(true);
        saveConfig();

        // Convert the text input to the enum values
        claimCreationTool = Utils.safeValueOf(Material::valueOf, config.getString("general.claim-creation-item"));
        if(claimCreationTool == null) {
            log("Invalid material found in config under general.claim-creation-item: " +
                    config.getString("general.claim-creation-item"));
            claimCreationTool = Material.GOLDEN_SHOVEL;
        }
        claimViewer = Utils.safeValueOf(Material::valueOf, config.getString("general.claim-viewer"));
        if(claimViewer == null) {
            log("Invalid material found in config under general.claim-viewer: " +
                    config.getString("general.claim-viewer"));
            claimViewer = Material.STICK;
        }

        // Register default region flag values
        RegionFlag.registerDefaults(config);
    }
}
