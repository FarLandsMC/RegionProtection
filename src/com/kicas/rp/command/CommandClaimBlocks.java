package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.util.TextUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static org.bukkit.Bukkit.getPlayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows server operators to view and adjust the claim blocks for a given player.
 */
public class CommandClaimBlocks extends TabCompletorBase implements CommandExecutor {
    private static final List<String> SUB_COMMANDS = Arrays.asList("add", "remove", "view");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Assume /claimblocks <self> view
        if (args.length < 1) {
            if (!(sender instanceof Player))
                return false;
            sender.sendMessage(ChatColor.GOLD + sender.getName() + " has " + RegionProtection.getDataManager()
                    .getClaimBlocks(((Player) sender).getUniqueId()) + " claim blocks.");
            int remaining = RegionProtection.getDataManager().getClaimBlocks(((Player) sender).getUniqueId()), used = 0;
            for (Region claim : RegionProtection.getDataManager().getRegionsInWorld(((Player)sender).getWorld())
                    .stream().filter(region -> !region.isAdminOwned() &&
                            region.isOwner(((Player) sender).getUniqueId())).collect(Collectors.toList()))
                used += claim.area();
            TextUtils.sendFormatted(sender, "&(gold)Total Blocks: %0\nUsed: %1\nAvailable: %2",
                    used + remaining, used, remaining);
            return true;
        }
    
        Player player = getPlayer(args[0]);
        if(player == null) {
            sender.sendMessage(ChatColor.RED + "Invalid username: " + args[0]);
            return true;
        }
    
        // Assume /claimblocks <player> view
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + player.getName() + " has " + RegionProtection.getDataManager()
                    .getClaimBlocks(player.getUniqueId()) + " claim blocks.");
            int remaining = RegionProtection.getDataManager().getClaimBlocks(((Player) sender).getUniqueId()), used = 0;
            for (Region claim : RegionProtection.getDataManager().getRegionsInWorld(((Player)sender).getWorld())
                    .stream().filter(region -> !region.isAdminOwned() &&
                            region.isOwner(((Player) sender).getUniqueId())).collect(Collectors.toList()))
                used += claim.area();
            TextUtils.sendFormatted(sender, "&(gold)Total Blocks: %0\nUsed: %1\nAvailable: %2",
                    used + remaining, used, remaining);
            return true;
        }

        // Sub-command check
        if(!SUB_COMMANDS.contains(args[1].toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "Invalid sub-command: " + args[1]);
            return true;
        }

        // View the number of claim blocks they have
        if("view".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ChatColor.GOLD + player.getName() + " has " + RegionProtection.getDataManager()
                    .getClaimBlocks(player.getUniqueId()) + " claim blocks.");
            int remaining = RegionProtection.getDataManager().getClaimBlocks(((Player) sender).getUniqueId()), used = 0;
            for (Region claim : RegionProtection.getDataManager().getRegionsInWorld(((Player)sender).getWorld())
                    .stream().filter(region -> !region.isAdminOwned() &&
                            region.isOwner(((Player) sender).getUniqueId())).collect(Collectors.toList()))
                used += claim.area();
            TextUtils.sendFormatted(sender, "&(gold)Total Blocks: %0\nUsed: %1\nAvailable: %2",
                    used + remaining, used, remaining);
        }else{
            // Secondary args check
            if(args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /claimblocks <name> <add|remove> <amount>");
                return true;
            }

            // Whether or not we're removing blocks
            boolean removing = "remove".equalsIgnoreCase(args[1]);

            // Parse the amount and account for parsing errors
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            }catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
                return true;
            }

            // Check to make sure we're not going to end up with negative claim blocks
            if(removing && amount > RegionProtection.getDataManager().getClaimBlocks(player.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + "You cannot take that many claim blocks from this player " +
                        "otherwise they would have negative claim blocks.");
                return true;
            }

            // Modify the claim blocks and notify the sender
            RegionProtection.getDataManager().modifyClaimBlocks(player.getUniqueId(), (removing ? -1 : 1) * amount);
            sender.sendMessage(ChatColor.GOLD + (removing ? "Removed " : "Added ") + amount + " claim blocks " +
                    (removing ? "from " : "to ") + player.getName() + ". They now have " +
                    RegionProtection.getDataManager().getClaimBlocks(player.getUniqueId()) + " claim blocks.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        switch (args.length) {
            case 1:
                return getOnlinePlayers(args[0]);
            case 2:
                return filterStartingWith(args[1], SUB_COMMANDS);
            default:
                return Collections.emptyList();
        }
    }
}
