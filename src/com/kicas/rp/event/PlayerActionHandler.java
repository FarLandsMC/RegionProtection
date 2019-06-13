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

import java.util.Collections;
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
                    .filter(region -> region.distanceFromEdge(player.getLocation()) < 100).collect(Collectors.toList());
            player.sendMessage(ChatColor.GOLD + "Found " + regions.size() + " claim" + (regions.size() == 1 ? "" : "s") + " nearby.");
            if(!regions.isEmpty())
                ps.setRegionHighlighter(new RegionHighlighter(player, regions));
            event.setCancelled(true);
            return;
        }

        if(Action.RIGHT_CLICK_BLOCK.equals(event.getAction())) {
            if(RegionProtection.getClaimCreationTool().equals(event.getMaterial())) {
                if(ps.getLastClickedBlock() == null) {
                    List<Region> regions = dm.getUnassociatedRegionsAt(event.getClickedBlock().getLocation());
                    /*if(regions.stream().anyMatch(region -> !region.isOwner(player))) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to modify this claim.");
                        event.setCancelled(true);
                        return;
                    }*/
                    ps.setLastClickedBlock(event.getClickedBlock().getLocation());
                    if(regions.isEmpty() || regions.size() > 1) {
                        ps.setAction(PlayerRegionAction.CREATE_REGION);
                        player.sendMessage(ChatColor.GOLD + "Claim corner set. Select another corner to create your claim.");
                    }else{
                        if(regions.get(0).isCorner(ps.getLastClickedBlock())) {
                            ps.setAction(PlayerRegionAction.RESIZE_REGION);
                            ps.setCurrentSelectedRegion(regions.get(0));
                            player.sendMessage(ChatColor.GOLD + "Claim corner selected. Select another block to resize the claim.");
                        }else{
                            ps.setAction(PlayerRegionAction.SUBDIVIDE_REGION);
                            ps.setCurrentSelectedRegion(regions.get(0));
                            player.sendMessage(ChatColor.GOLD + "You are subdividing this claim. Select another block to create the subdivision.");
                        }
                    }
                }else{
                    Location vertex = ps.getLastClickedBlock();
                    ps.setLastClickedBlock(null);
                    switch(ps.getAction()) {
                        case CREATE_REGION:
                        {
                            Region claim = dm.tryCreateClaim(player, vertex, event.getClickedBlock().getLocation());
                            if(claim != null) {
                                player.sendMessage(ChatColor.GREEN + "Claim created. You have " + ps.getClaimBlocks() + " claim blocks remaining.");
                                ps.setRegionHighlighter(new RegionHighlighter(player, claim));
                            }
                            break;
                        }
                        case RESIZE_REGION:
                        {
                            Region claim = dm.tryResizeClaim(player, ps.getCurrentSelectedRegion(), vertex, event.getClickedBlock().getLocation());
                            if(claim != null) {
                                player.sendMessage(ChatColor.GREEN + "Claim resized. You have " + ps.getClaimBlocks() + " claim blocks remaining.");
                                ps.setRegionHighlighter(new RegionHighlighter(player, claim));
                            }
                            break;
                        }
                        case SUBDIVIDE_REGION:
                        {
                            Region subdivision = dm.tryCreateSubdivision(player, ps.getCurrentSelectedRegion(), vertex, event.getClickedBlock().getLocation());
                            if(subdivision != null) {
                                player.sendMessage(ChatColor.GREEN + "Subdivision created.");
                                ps.setRegionHighlighter(new RegionHighlighter(player, Collections.singleton(ps.getCurrentSelectedRegion()), null, null, true));
                            }
                            break;
                        }
                    }
                }
                event.setCancelled(true);
            }else if(RegionProtection.getClaimViewer().equals(event.getMaterial()))
                detailClaimAt(player, event.getClickedBlock().getLocation());
        }else if(Action.RIGHT_CLICK_AIR.equals(event.getAction()) && RegionProtection.getClaimViewer().equals(event.getMaterial())) {
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
        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession(event.getPlayer());
        if(RegionProtection.getClaimCreationTool().equals(Utils.stackType(event.getPlayer().getInventory().getItem(event.getNewSlot())))) {
                event.getPlayer().sendMessage(ChatColor.GOLD + "You can claim up to " + ps.getClaimBlocks() + " more blocks.");
            List<Region> regions = RegionProtection.getDataManager().getRegionsInWorld(event.getPlayer().getWorld()).stream()
                    .filter(region -> region.distanceFromEdge(event.getPlayer().getLocation()) < 100).collect(Collectors.toList());
            if(!regions.isEmpty())
                ps.setRegionHighlighter(new RegionHighlighter(event.getPlayer(), regions));
        }else if(RegionProtection.getClaimCreationTool().equals(Utils.stackType(event.getPlayer().getInventory().getItem(event.getPreviousSlot())))) {
            ps.setLastClickedBlock(null);
            ps.setAction(null);
        }
    }

    private static void detailClaimAt(Player recipient, Location location) {
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(location);
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
