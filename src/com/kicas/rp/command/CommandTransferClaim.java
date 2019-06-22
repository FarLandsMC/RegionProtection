package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CommandTransferClaim extends Command {
    CommandTransferClaim() {
        super("transferclaim", "Transfer the ownership of your claim to another person.", "/transferclaim <newOwner>");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        if(args.length == 0)
            return false;

        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        UUID newOwner = Utils.uuidForUsername(args[0]);
        if(newOwner == null) {
            sender.sendMessage(ChatColor.RED + "Invalid username: " + args[0]);
            return true;
        }

        Region region = RegionProtection.getDataManager().getParentRegionsAt(((Player)sender).getLocation()).stream()
                .filter(r -> r.isOwner((Player)sender)).findAny().orElse(null);
        if(region == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the region you wish to transfer to this person.");
            return true;
        }

        if(RegionProtection.getDataManager().tryTransferOwnership((Player)sender, region, newOwner, true))
            sender.sendMessage(ChatColor.GREEN + "This claim is now owned by " + args[0] + ".");

        return true;
    }

    @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location)
            throws IllegalArgumentException {
        return args.length == 1 ? getOnlinePlayers(args[0]) : Collections.emptyList();
    }
}
