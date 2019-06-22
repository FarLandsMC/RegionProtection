package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.TrustMeta;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Allows players to kick* certain players from their claim.
 * * send to the nearest unclaimed block
 */
public class CommandExpel extends Command {
    
    CommandExpel() {
        super("expel", "Expel a player from your claim.", "/expel <player>");
    }
    
    @Override
    protected boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
    
        // Sender check
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }
    
        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player) sender).getLocation());
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
        if (claim.isOwner((Player) sender) && sender.getName().equals(args[0])) {
            sender.sendMessage(ChatColor.RED + "You cannot expel yourself from your own claim.");
            return true;
        }
        

        // Check the player exists
        Player player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Could not find player " + args[0]);
            return true;
        }
        
        // find a safe place to send the player
        int w = claim.getMax().getBlockX() - claim.getMin().getBlockX(),
                l = claim.getMax().getBlockZ() - claim.getMin().getBlockZ();
        Location ejection = claim.getMin().add(w >> 1, 0, l >> 1); // center x,z of the claim
        
        // expel the player
        player.teleport(walk(ejection, ejection.getBlockX() < 0 ? w : -w, ejection.getBlockZ() < 0 ? l : -l));
        sender.sendMessage("Expelled player " + player.getName() + " from your claim");
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location)
            throws IllegalArgumentException {
        return args.length == 1 ? getOnlinePlayers(args[0]) : Collections.emptyList();
    }
    
    private static Location walk(Location location, int dx, int dz) {
        Location temp = findSafe(location.add(dx, 0, dz));
        while (temp == null) {
            temp = findSafe(location.add(dx, 0, dz));
        }
        return temp;
    }
    
    private static boolean doesDamage(Block b) {
        return b.getType().isSolid() || b.isLiquid() || Arrays.asList(Material.FIRE, Material.CACTUS).contains(b.getType());
    }
    private static boolean canStand(Block b) { // if a player can safely stand here
        // (you can drown in water but you can also float and for this case swimming is safe enough)
        return !(b.isPassable() || Arrays.asList(Material.MAGMA_BLOCK, Material.CACTUS).contains(b.getType())) ||
                b.getType().equals(Material.WATER);
    }
    private static boolean isSafe(Location l) { // if block below is solid and 2 blocks in player collision do not do damage
        return !(doesDamage(l.add(0, 1, 0).getBlock()) || doesDamage(l.add(0, 1, 0).getBlock()));
    }
    
    private static Location findSafe(final Location l) {
        l.setX(l.getBlockX() + .5);
        l.setZ(l.getBlockZ() + .5);
        return findSafe(l, Math.max(1, l.getBlockY() - 8), Math.min(l.getBlockY() + 7,
                l.getWorld().getName().equals("world_nether") ? 126 : 254));
    }
    private static Location findSafe(final Location origin, int s, int e) {
        Location safe = origin.clone();
        if (canStand(safe.getBlock()) && isSafe(safe.clone()))
            return safe.add(0, .5, 0);
        do {
            safe.setY((s + e) >> 1);
            if (canStand(safe.getBlock())) {
                if (isSafe(safe.clone()))
                    return safe.add(0, 1, 0);
                s = safe.getBlockY();
            } else
                e = safe.getBlockY();
        } while (e - s > 1);
        safe.getChunk().unload();
        return null;
    }
    
}
