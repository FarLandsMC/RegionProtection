package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class Command extends org.bukkit.command.Command {
    protected Command(String name, String description, String usage, String... aliases) {
        super(name, description, usage, Arrays.asList(aliases));
    }

    protected abstract boolean execute(CommandSender sender, String[] args) throws Exception;

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        try {
            Bukkit.getScheduler().runTask(RegionProtection.getInstance(), () -> {
                try {
                    if(!execute(sender, args))
                        showUsage(sender);
                }catch(TextUtils.SyntaxException ex) {
                    sender.sendMessage(ChatColor.RED + ex.getMessage());
                }catch(Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }catch(Throwable ex) {
            sender.sendMessage("There was an error executing this command.");
            ex.printStackTrace(System.out);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) throws IllegalArgumentException {
        return Collections.emptyList();
    }

    @Override
    public boolean isRegistered() {
        return true;
    }

    public boolean isOpOnly() {
        return false;
    }

    public boolean matches(String command) { // Does this command math the given token?
        command = command.toLowerCase();
        return command.equalsIgnoreCase(getName()) || getAliases().contains(command);
    }

    protected void showUsage(CommandSender sender) {
        sender.sendMessage("Usage: " + getUsage());
    }
}
