package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.Utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Allows players to kick* certain players from their claim.
 * * send to the nearest unclaimed block
 */
public class CommandExpel extends TabCompletorBase implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Sender check
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }
    
        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAtIgnoreY(((Player) sender).getLocation());
        if (claim == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim where you wish to expel players.");
            return true;
        }
    
        // Make sure the sender has permission to expel other players
        TrustMeta trustMeta = claim.getAndCreateFlagMeta(RegionFlag.TRUST);
        if (!trustMeta.hasTrust((Player) sender, TrustLevel.MANAGEMENT, claim)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to expel players from this claim.");
            return true;
        }
    
        // Make sure the owner isn't trying to expel themselves
        if (claim.isEffectiveOwner((Player) sender) && sender.getName().equals(args[0])) {
            sender.sendMessage(ChatColor.RED + "You cannot expel yourself from your own claim.");
            return true;
        }

        // Check the player exists
        Player player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Could not find player " + args[0]);
            return true;
        }
        
        // Find a safe place to send the player
        int w = claim.getMax().getBlockX() - claim.getMin().getBlockX(),
                l = claim.getMax().getBlockZ() - claim.getMin().getBlockZ();
        Location ejection = claim.getMin().clone().add(w >> 1, 0, l >> 1); // center x,z of the claim
        
        // Expel the player // dx dz tend to 0 ~ 0 to avoid world border issues
        player.teleport(Utils.walk(ejection, ejection.getBlockX() < 0 ? w : -w, ejection.getBlockZ() < 0 ? l : -l));
        sender.sendMessage(ChatColor.GREEN + "Expelled player " + player.getName() + " from your claim");

        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        return args.length == 1 ? getOnlinePlayers(args[0]) : Collections.emptyList();
    }
}
