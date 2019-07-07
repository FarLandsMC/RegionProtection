package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allows a player to toggle on or off tnt in their claim.
 */
public class CommandClaimTnt implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player)sender).getLocation());
        if(claim == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim where you wish to trust this person.");
            return true;
        }

        boolean newValue = !(boolean)claim.getFlagMeta(RegionFlag.TNT);
        claim.setFlag(RegionFlag.TNT, newValue);

        sender.sendMessage(ChatColor.GOLD + (newValue ? "Enabled" : "Disabled") + " TNT in your claim.");

        return true;
    }
}
