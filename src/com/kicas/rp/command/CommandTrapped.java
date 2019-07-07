package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.FlagContainer;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.Utils;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allows players to kick* certain players from their claim.
 * * send to the nearest unclaimed block
 */
public class CommandTrapped implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Sender check
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }
        Player player = (Player) sender;
        
        // Make sure the player is actually stuck and cannot dig themselves out to prevent spamming for free teleports
        // stuck meaning that the player has no permissions for the claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAtIgnoreY(player.getLocation());
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(player.getLocation());
        if (claim == null || flags == null || !flags.isAllowed(RegionFlag.DENY_BREAK) ||
                flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player, TrustLevel.BUILD, flags)) {
            sender.sendMessage(ChatColor.RED + "You should not be trapped in this location");
            return true;
        }
        
        // Find a safe place to send the player
        int w = claim.getMax().getBlockX() - claim.getMin().getBlockX(),
                l = claim.getMax().getBlockZ() - claim.getMin().getBlockZ();
        Location ejection = claim.getMin().clone().add(w >> 1, 0, l >> 1); // center x,z of the claim
        // get dx dz tend to 0 ~ 0 to avoid world border issues
        int dx = ejection.getBlockX() < 0 ? 1 : -1, dz =  ejection.getBlockZ() < 0 ? 1 : -1;
        ejection.add(w * dx, 0, l * dz); // get to the edge of the claim closest to 0 ~ 0
        
        // Free the player
        player.teleport(Utils.walk(ejection, 3 * dx, 3 * dz));
        sender.sendMessage(ChatColor.GREEN + "Teleported to safety");
        return true;
    }
}
