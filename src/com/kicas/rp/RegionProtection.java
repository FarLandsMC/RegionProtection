package com.kicas.rp;

import com.kicas.rp.command.CommandHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class RegionProtection extends JavaPlugin {
    private final CommandHandler commandHandler;

    private static RegionProtection instance;

    public RegionProtection() {
        this.commandHandler = new CommandHandler();
        instance = this;
    }

    @Override
    public void onEnable() {
        commandHandler.registerCommands();
        Bukkit.getPluginManager().registerEvents(commandHandler, this);
    }

    public static RegionProtection getInstance() {
        return instance;
    }

    public static void log(Object x) {
        Bukkit.getLogger().info("[FLv1] - " + Objects.toString(x));
    }

    public static void error(Object x) {
        String msg = Objects.toString(x);
        Bukkit.getLogger().severe("[FLv1] - " + msg);
    }
}
