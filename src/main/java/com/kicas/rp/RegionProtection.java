package com.kicas.rp;

import com.kicas.rp.command.*;
import com.kicas.rp.data.DataManager;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.event.EntityEventHandler;
import com.kicas.rp.event.RegionToolHandler;
import com.kicas.rp.event.PlayerEventHandler;
import com.kicas.rp.event.WorldEventHandler;
import com.kicas.rp.util.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This is the main plugin class for the Region Protection plugin. All non-utility features of the plugin can be
 * accessed through this class.
 */
public class RegionProtection extends JavaPlugin {
    private final DataManager dataManager;

    private Material claimCreationTool, claimViewer;
    private double claimBlocksGainedPerMinute;
    private List<UUID> claimableWorlds;

    private static RegionProtection instance;

    public RegionProtection() {
        this.dataManager = new DataManager(getDataFolder());
        instance = this;
    }

    /**
     * Called when the plugin is enabled. Initializes the config, loads serialized data, registers commands, registers
     * event handlers, and schedules tasks.
     */
    @Override
    public void onEnable() {
        initConfig();
        dataManager.load();
        registerCommands();
        registerEventHandlers();
        scheduleTasks();
    }

    /**
     * Called when the plugin is disabled. Saves persistent data to disk.
     */
    @Override
    public void onDisable() {
        dataManager.save();
    }

    /**
     * @return the plugin instance.
     */
    public static RegionProtection getInstance() {
        return instance;
    }

    /**
     * @return the data manager instance.
     */
    public static DataManager getDataManager() {
        return instance.dataManager;
    }

    /**
     * @return the plugin config.
     */
    public static FileConfiguration getRPConfig() {
        return instance.getConfig();
    }

    /**
     * @return the material associated with claim creation.
     */
    public static Material getClaimCreationTool() {
        return instance.claimCreationTool;
    }

    /**
     * @return the material associated with claim inquiry.
     */
    public static Material getClaimViewerTool() {
        return instance.claimViewer;
    }

    /**
     * Returns the UIDs of the worlds that can be claimed by players. The check for this is buried in the
     * RegionToolHandler in the code for the first vertex selection.
     *
     * @return the UIDs of the worlds that can be claimed by players.
     */
    public static List<UUID> getClaimableWorlds() {
        return Collections.unmodifiableList(instance.claimableWorlds);
    }

    /**
     * Logs an object to the console.
     *
     * @param x the object to log.
     */
    public static void log(Object x) {
        Bukkit.getLogger().info("[RegionProtection] " + x);
    }

    /**
     * Logs an error to the console.
     *
     * @param x the error to log.
     */
    public static void error(Object x) {
        Bukkit.getLogger().severe("[RegionProtection] " + x);
    }

    /**
     * Initializes the plugin config and registers default flag values.
     */
    private void initConfig() {
        FileConfiguration config = getConfig();

        config.addDefault("general.claim-creation-item", Material.GOLDEN_SHOVEL.name());
        config.addDefault("general.claim-viewer", Material.STICK.name());
        config.addDefault("general.minimum-claim-size", 100);
        config.addDefault("general.minimum-subdivision-size", 25);
        config.addDefault("general.minimum-subdivision-height", 5);
        config.addDefault("general.starting-claim-blocks", 100);
        config.addDefault("general.claim-blocks-gained-per-hour", 512);
        config.addDefault("general.claim-expiration-time", 60);
        config.addDefault("general.enable-claim-stealing", false);
        config.addDefault("general.enable-claims-in-worlds", Collections.singletonList("world"));

        config.addDefault("region.invincible", false);
        config.addDefault("region.potion-splash", true);

        config.addDefault("player.pvp", false);
        config.addDefault("player.hostile-damage", true);
        config.addDefault("player.animal-damage", true);

        config.addDefault("entity.animal-grief-blocks", true);
        config.addDefault("entity.hostile-grief-blocks", false);
        config.addDefault("entity.hostile-grief-entities", false);

        config.addDefault("world.tnt", false);
        config.addDefault("world.snow-change", true);
        config.addDefault("world.ice-change", true);
        config.addDefault("world.coral-death", true);
        config.addDefault("world.leaf-decay", true);
        config.addDefault("world.lightning-mob-damage", false);
        config.addDefault("world.portal-pair-formation", false);
        config.addDefault("world.fire-tick", true);

        config.options().copyDefaults(true);
        saveConfig();

        // Convert the strings to enum constants
        claimCreationTool = Utils.safeValueOf(Material::valueOf, config.getString("general.claim-creation-item"));
        if (claimCreationTool == null) {
            log("Invalid material found in config under general.claim-creation-item: " +
                    config.getString("general.claim-creation-item"));
            claimCreationTool = Material.GOLDEN_SHOVEL;
        }

        claimViewer = Utils.safeValueOf(Material::valueOf, config.getString("general.claim-viewer"));
        if (claimViewer == null) {
            log("Invalid material found in config under general.claim-viewer: " +
                    config.getString("general.claim-viewer"));
            claimViewer = Material.STICK;
        }

        // Put claim block addition in a usable form
        claimBlocksGainedPerMinute = (double) config.getInt("general.claim-blocks-gained-per-hour") / 60.0;

        // Convert the world names to UUIDs, filtering out invalid names in the process
        claimableWorlds = config.getStringList("general.enable-claims-in-worlds").stream().map(name -> {
            World world = Bukkit.getWorld(Utils.getWorldName(name));
            if (world == null)
                log("Invalid world name in config value general.enable-claims-in-worlds: " + name);
            return world;
        }).filter(Objects::nonNull).map(World::getUID).collect(Collectors.toList());

        // Register default region flag values
        RegionFlag.registerDefaults(config);
    }

    /**
     * Registers a command with the given name and implementation.
     *
     * @param name the command name.
     * @param impl the command implementation.
     */
    private void registerCommand(String name, Object impl) {
        getCommand(name).setExecutor((CommandExecutor) impl);
        if (impl instanceof TabCompleter)
            getCommand(name).setTabCompleter((TabCompleter) impl);
    }

    /**
     * Registers the plugin commands.
     */
    private void registerCommands() {
        registerCommand("abandonclaim", SimpleCommand.ABANDON_CLAIM);
        registerCommand("addcoowner", SimpleCommand.ADD_CO_OWNER);
        registerCommand("adminregion", SimpleCommand.ADMIN_REGION);
        registerCommand("claim", SimpleCommand.CLAIM);
        registerCommand("claimblocks", new CommandClaimBlocks());
        registerCommand("claimheight", new CommandClaimHeight());
        registerCommand("claimlist", SimpleCommand.CLAIM_LIST);
        registerCommand("claimtoggle", SimpleCommand.CLAIM_TOGGLE);
        registerCommand("expandclaim", SimpleCommand.EXPAND_CLAIM);
        registerCommand("expel", SimpleCommand.EXPEL);
        registerCommand("ignoretrust", SimpleCommand.IGNORE_TRUST);
        registerCommand("nameclaim", SimpleCommand.NAME_CLAIM);
        registerCommand("region", new CommandRegion());
        registerCommand("steal", SimpleCommand.STEAL);
        registerCommand("toregion", SimpleCommand.TO_REGION);
        registerCommand("transferclaim", SimpleCommand.TRANSFER_CLAIM);
        registerCommand("trapped", SimpleCommand.TRAPPED);
        registerCommand("trust", new CommandTrust());
        registerCommand("trustlist", SimpleCommand.TRUST_LIST);
        registerCommand("trusted", SimpleCommand.TRUSTED);
        registerCommand("nearbyclaims", SimpleCommand.NEARBY_CLAIMS);
    }

    /**
     * Registers the plugin event handlers.
     */
    private void registerEventHandlers() {
        Bukkit.getPluginManager().registerEvents(dataManager, this);
        Bukkit.getPluginManager().registerEvents(new RegionToolHandler(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerEventHandler(), this);
        Bukkit.getPluginManager().registerEvents(new EntityEventHandler(), this);
        Bukkit.getPluginManager().registerEvents(new WorldEventHandler(), this);
    }

    /**
     * Registers plugin tasks, such as the claim block adding task, automatic claim expiration task, and automatic data
     * save task.
     */
    private void scheduleTasks() {
        // Automatic claim block gaining
        if (claimBlocksGainedPerMinute > 0) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                Bukkit.getOnlinePlayers().stream().map(dataManager::getPlayerSession)
                        .forEach(ps -> ps.addClaimBlocks(claimBlocksGainedPerMinute));
            }, 0L, 60L * 20L);
        }

        // Expire claims if they are older then the time given in the config (in days). The age of a claim is determined
        // by subtracting the last login time of the most recently active trustee with at least container trust from the
        // current time.
        final long claimExpirationTime = getConfig().getInt("general.claim-expiration-time") * 24L * 60L * 60L * 1000L;
        if (claimExpirationTime > 0 && !getConfig().getBoolean("general.enable-claim-stealing")) {
            // Check every hour
            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
                Bukkit.getWorlds().forEach(world -> {
                    dataManager.tryDeleteRegions(null, world, region -> region.hasExpired(claimExpirationTime), true);
                });
            }, 100L, 60L * 60L * 20L);
        }

        // Periodically save data in case of crashes (5 minutes)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, dataManager::save, 5L * 60L * 20L,
                5L * 60L * 20L);
    }
}
