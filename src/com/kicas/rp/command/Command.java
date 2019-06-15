package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the superclass for custom Region Protection commands.
 */
public abstract class Command extends org.bukkit.command.Command {
    protected Command(String name, String description, String usage, String... aliases) {
        super(name, description, usage, Arrays.asList(aliases));
    }

    // Used so that command execution can be wrapped, returns true if the command usage should NOT be shown
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
        // Lacking permission
        if(isOpOnly() && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You must be an administrator to use this command.");
            return true;
        }

        // Run the command
        Bukkit.getScheduler().runTask(RegionProtection.getInstance(), () -> {
            try {
                if(!executeUnsafe(sender, alias.startsWith("regionprotection:") ? alias.substring(17) : alias, args))
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

    /**
     * Returns a list of the currently online players whose name starts with the given partial name.
     * @param partialName the partial name.
     * @return a list of the currently online players whose name starts with the given partial name.
     */
    public static List<String> getOnlinePlayers(String partialName) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partialName.toLowerCase())).collect(Collectors.toList());
    }

    /**
     * Joins all the arguments after the argument at the given index with the given delimiter.
     * @param index the index.
     * @param delim the delimiter.
     * @param args the arguments.
     * @return the result of joining the argument after the given index with the given delimiter.
     */
    public static String joinArgsBeyond(int index, String delim, String[] args) {
        ++ index;
        String[] data = new String[args.length - index];
        System.arraycopy(args, index, data, 0, data.length);
        return String.join(delim, data);
    }

    public static List<String> filterStartingWith(String prefix, Stream<String> stream) {
        return stream.filter(s -> s != null && s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    public static List<String> filterStartingWith(String prefix, Collection<String> strings) {
        return filterStartingWith(prefix, strings.stream());
    }
}
