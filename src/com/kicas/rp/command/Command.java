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

/**
 * Represents the superclass for custom Region Protection commands.
 */
public abstract class Command extends org.bukkit.command.Command {
    protected Command(String name, String description, String usage, String... aliases) {
        super(name, description, usage, Arrays.asList(aliases));
    }

    // Used so that command execution can be wrapped
    protected abstract boolean executeUnsafe(CommandSender sender, String alias, String[] args) throws Exception;

    /**
     * Executes the command synchronously and catches and exceptions that it throws.
     * @param sender the command sender.
     * @param alias the alias used.
     * @param args the arguments used.
     * @return true, always.
     */
    @Override
    public boolean execute(CommandSender sender, String alias, String[] args) {
        Bukkit.getScheduler().runTask(RegionProtection.getInstance(), () -> {
            try {
                if(!executeUnsafe(sender, alias, args))
                    showUsage(sender);
            }catch(TextUtils.SyntaxException ex) {
                sender.sendMessage(ChatColor.RED + ex.getMessage());
            }catch(Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location)
            throws IllegalArgumentException {
        return Collections.emptyList();
    }

    @Override
    public boolean isRegistered() {
        return true;
    }

    public boolean isOpOnly() {
        return false;
    }

    /**
     * Returns whether or not this command matches the provided token. The provided command should not have any
     * arguments.
     * @param command the command to check.
     * @return true if this command's name matches the given text ignoring case, or if any of this command's aliases
     * equal the given text ignoring case.
     */
    public boolean matches(String command) {
        command = command.toLowerCase();
        return command.equalsIgnoreCase(getName()) || getAliases().contains(command);
    }

    protected void showUsage(CommandSender sender) {
        sender.sendMessage("Usage: " + getUsage());
    }
}
