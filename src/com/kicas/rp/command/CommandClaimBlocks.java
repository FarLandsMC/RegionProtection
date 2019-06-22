package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CommandClaimBlocks extends Command {
    private static final List<String> SUB_COMMANDS = Arrays.asList("add", "remove", "view");

    CommandClaimBlocks() {
        super("claimblocks", "Add to, remove from, or view someone\'s claim blocks.",
                "/claimblocks <add|remove|view> <name> [amount]");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        if(args.length < 2)
            return false;

        if(!SUB_COMMANDS.contains(args[0].toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "Invalid sub-command: " + args[0]);
            return true;
        }

        UUID player = Utils.uuidForUsername(args[1]);
        if(player == null) {
            sender.sendMessage(ChatColor.RED + "Invalid username: " + args[1]);
            return true;
        }

        if("view".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.GOLD + args[1] + " has " + RegionProtection.getDataManager()
                    .getClaimBlocks(player) + " claim blocks.");
        }else{
            if(args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /claimblocks <add|remove> <name> <amount>");
                return true;
            }

            boolean removing = "remove".equalsIgnoreCase(args[0]);
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            }catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
                return true;
            }

            if(removing && amount > RegionProtection.getDataManager().getClaimBlocks(player)) {
                sender.sendMessage(ChatColor.RED + "You cannot take that many claim blocks from this player " +
                        "otherwise they would have negative claim blocks.");
                return true;
            }

            RegionProtection.getDataManager().modifyClaimBlocks(player, (removing ? -1 : 1) * amount);
            sender.sendMessage(ChatColor.GOLD + (removing ? "Removed " : "Added ") + amount + " claim blocks " +
                    (removing ? "from " : "to ") + args[1] + ". They now have " +
                    RegionProtection.getDataManager().getClaimBlocks(player) + " claim blocks.");
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location)
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
