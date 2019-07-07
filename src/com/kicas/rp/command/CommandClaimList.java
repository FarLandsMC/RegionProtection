package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.DataManager;
import com.kicas.rp.data.Region;
import com.kicas.rp.util.TextUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Allow players to view a list of the claims they have in their current world, including the x and z location as well
 * as the number of claim blocks the take up. Players with OP can specify which player's claim list they wish to view.
 */
public class CommandClaimList implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Get the owner in question
        UUID uuid = args.length > 0 && sender.hasPermission("rp.command.externalclaimlist")
                ? DataManager.uuidForUsername(args[0]) : ((Player)sender).getUniqueId();

        // Build the list
        List<Region> claimlist = RegionProtection.getDataManager().getRegionsInWorld(((Player)sender).getWorld())
                .stream().filter(region -> !region.isAdminOwned() && region.isOwner(uuid)).collect(Collectors.toList());

        // Format the list and send it to the player
        TextUtils.sendFormatted(sender, "&(gold)%0 {&(aqua)%1} $(inflect,noun,1,claim) in this world:",
                uuid.equals(((Player)sender).getUniqueId()) ? "You have" : args[0] + " has", claimlist.size());
        claimlist.forEach(region -> TextUtils.sendFormatted(sender, "&(gold)%0x, %1z: %2 claim blocks",
                (int)(0.5 * (region.getMin().getX() + region.getMax().getX())),
                (int)(0.5 * (region.getMin().getZ() + region.getMax().getZ())),
                region.area()));

        return true;
    }
}
