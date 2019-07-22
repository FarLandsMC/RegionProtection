package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Allows the owner of a claim to give someone else ownership of one of their claims.
 */
public class CommandTransferClaim extends TabCompleterBase implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Args check
        if (args.length == 0)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Get and check the specified new owner
        UUID newOwner = RegionProtection.getDataManager().uuidForUsername(args[0]);
        if (newOwner == null) {
            sender.sendMessage(ChatColor.RED + "Invalid username: " + args[0]);
            return true;
        }

        // Get and check the region the sender is standing in
        Region region = RegionProtection.getDataManager().getParentRegionsAt(((Player) sender).getLocation()).stream()
                .filter(r -> r.isEffectiveOwner((Player) sender)).findAny().orElse(null);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the region you wish to transfer to this person.");
            return true;
        }

        // Transfer ownership
        if (RegionProtection.getDataManager().tryTransferOwnership((Player) sender, region, newOwner, true))
            sender.sendMessage(ChatColor.GREEN + "This claim is now owned by " + args[0] + ".");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Online players
        return args.length == 1 ? getOnlinePlayers(args[0]) : Collections.emptyList();
    }
}
