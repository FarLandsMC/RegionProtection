package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Allows a player to toggle on or off tnt in their claim.
 */
public class CommandClaimToggle extends TabCompleterBase implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if(args.length == 0)
            return false;

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

        RegionFlag toggle = Utils.valueOfFormattedName(args[0], RegionFlag.class);
        if(toggle == null || !toggle.isPlayerToggleable()) {
            sender.sendMessage(ChatColor.RED + "Invalid toggle: " + args[0]);
            return true;
        }

        boolean newValue;

        if(args.length == 2) {
            if("on".equalsIgnoreCase(args[1]))
                newValue = true;
            else if("off".equalsIgnoreCase(args[1]))
                newValue = false;
            else{
                sender.sendMessage(ChatColor.RED + "Invalid toggle value: " + args[1] + ". Expected \"on\" or \"off\"");
                return true;
            }
        }else
            newValue = !claim.isAllowed(toggle);

        claim.setFlag(toggle, newValue);
        sender.sendMessage(ChatColor.GOLD + (newValue ? "Enabled" : "Disabled") + " " + Utils.formattedName(toggle) +
                " in your claim.");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        switch (args.length) {
            case 1:
                return filterStartingWith(args[0], Arrays.stream(RegionFlag.VALUES)
                        .filter(RegionFlag::isPlayerToggleable).map(Utils::formattedName));
            case 2:
                return filterStartingWith(args[1], Stream.of("on", "off"));
            default:
                return Collections.emptyList();
        }
    }
}
