package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.stream.Collectors;

public class PlayerActionHandler implements Listener {
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        DataManager dm = RegionProtection.getDataManager();
        Player player = event.getPlayer();
        PlayerSession ps = dm.getPlayerSession(player);

        if((Action.RIGHT_CLICK_BLOCK.equals(event.getAction()) || Action.RIGHT_CLICK_AIR.equals(event.getAction())) &&
                player.isSneaking() && RegionProtection.getClaimViewer().equals(event.getMaterial())) {
            List<Region> regions = dm.getRegionsInWorld(player.getWorld()).stream()
                    .filter(region -> Math.min(region.getMin().distanceSquared(player.getLocation()),
                            region.getMax().distanceSquared(player.getLocation())) < 10000).collect(Collectors.toList());
            player.sendMessage(ChatColor.GOLD + "Found " + regions.size() + " claim" + (regions.size() == 1 ? "" : "s") + " nearby.");
            if(!regions.isEmpty())
                ps.setRegionHighlighter(new RegionHighlighter(player, regions));
            event.setCancelled(true);
            return;
        }

        if(Action.RIGHT_CLICK_BLOCK.equals(event.getAction())) {
            if(RegionProtection.getClaimCreationTool().equals(event.getMaterial())) {
                if(ps.getLastClickedBlock() == null) {
                    ps.setLastClickedBlock(event.getClickedBlock().getLocation());
                    player.sendMessage(ChatColor.GOLD + "Claim corner set. Select another corner to create your claim.");
                }else{
                    Location vertex = ps.getLastClickedBlock();
                    ps.setLastClickedBlock(null);
                    Region claim = RegionProtection.getDataManager().tryCreateClaim(player, vertex, event.getClickedBlock().getLocation());
                    if(claim != null) {
                        player.sendMessage(ChatColor.GREEN + "Claim created. You have " + ps.getClaimBlocks() + " claim blocks remaining.");
                        ps.setRegionHighlighter(new RegionHighlighter(player, claim));
                    }
                }
                event.setCancelled(true);
            }else if(RegionProtection.getClaimViewer().equals(event.getMaterial()))
                detailClaimAt(player, event.getClickedBlock().getLocation());
        }else if(Action.RIGHT_CLICK_AIR.equals(event.getAction())) {
            RayTraceResult result = player.rayTraceBlocks(100);
            if(result == null) {
                player.sendMessage(ChatColor.RED + "This block is too far away.");
                ps.setRegionHighlighter(null);
            }else
                detailClaimAt(player, result.getHitPosition().toLocation(player.getWorld()));
        }
    }

    @EventHandler
    public void onHotbarScroll(PlayerItemHeldEvent event) {
        if(RegionProtection.getClaimCreationTool().equals(Utils.stackType(event.getPlayer().getInventory().getItem(event.getNewSlot())))) {
            event.getPlayer().sendMessage(ChatColor.GOLD + "You can claim up to " + RegionProtection.getDataManager()
                    .getPlayerSession(event.getPlayer()).getClaimBlocks() + " more blocks.");
        }
    }

    private static void detailClaimAt(Player recipient, Location location) {
        Region claim = RegionProtection.getDataManager().getRegionsAt(location)
                .stream().filter(r -> !r.isAllowed(RegionFlag.OVERLAP)).findAny().orElse(null);
        if(claim == null) {
            recipient.sendMessage(ChatColor.RED + "No one has claimed this block.");
            RegionProtection.getDataManager().getPlayerSession(recipient).setRegionHighlighter(null);
        }else{
            if(claim.isAdminOwned())
                recipient.sendMessage(ChatColor.RED + "This belongs to an administrator.");
            else
                recipient.sendMessage(ChatColor.GOLD + "This belongs to " + claim.getOwnerName());
            RegionProtection.getDataManager().getPlayerSession(recipient).setRegionHighlighter(new RegionHighlighter(recipient, claim));
        }
    }
}
