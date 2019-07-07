package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.DataManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Allows server operators to view and adjust the claim blocks for a given player.
 */
public class CommandClaimBlocks extends TabCompletorBase implements CommandExecutor {
    private static final List<String> SUB_COMMANDS = Arrays.asList("add", "remove", "view");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Args check
        if(args.length < 2)
            return false;

        // Sub-command check
        if(!SUB_COMMANDS.contains(args[0].toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "Invalid sub-command: " + args[0]);
            return true;
        }

        // Get and check the UUID of the given player
        UUID player = DataManager.uuidForUsername(args[1]);
        if(player == null) {
            sender.sendMessage(ChatColor.RED + "Invalid username: " + args[1]);
            return true;
        }

        // View the number of claim blocks they have
        if("view".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.GOLD + args[1] + " has " + RegionProtection.getDataManager()
                    .getClaimBlocks(player) + " claim blocks.");
        }else{
            // Secondary args check
            if(args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /claimblocks <add|remove> <name> <amount>");
                return true;
            }

            // Whether or not we're removing blocks
            boolean removing = "remove".equalsIgnoreCase(args[0]);

            // Parse the amount and account for parsing errors
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            }catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
                return true;
            }

            // Check to make sure we're not going to end up with negative claim blocks
            if(removing && amount > RegionProtection.getDataManager().getClaimBlocks(player)) {
                sender.sendMessage(ChatColor.RED + "You cannot take that many claim blocks from this player " +
                        "otherwise they would have negative claim blocks.");
                return true;
            }

            // Modify the claim blocks and notify the serder
            RegionProtection.getDataManager().modifyClaimBlocks(player, (removing ? -1 : 1) * amount);
            sender.sendMessage(ChatColor.GOLD + (removing ? "Removed " : "Added ") + amount + " claim blocks " +
                    (removing ? "from " : "to ") + args[1] + ". They now have " +
                    RegionProtection.getDataManager().getClaimBlocks(player) + " claim blocks.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        switch (args.length) {
            case 1:
                return filterStartingWith(args[0], SUB_COMMANDS);
            case 2:
                return getOnlinePlayers(args[1]);
            default:
                return Collections.emptyList();
        }
    }
}
