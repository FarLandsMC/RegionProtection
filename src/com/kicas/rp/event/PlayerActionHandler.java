package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Materials;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.util.RayTraceResult;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the claim creation and viewing tools as specified in the config.
 */
public class PlayerActionHandler implements Listener {
    /**
     * Handle right-clicking with the claim creation tool or the claim viewing tool.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOWEST)
    public void onRightClick(PlayerInteractEvent event) {
        // Just for ease of access
        DataManager dm = RegionProtection.getDataManager();
        Player player = event.getPlayer();
        PlayerSession ps = dm.getPlayerSession(player);

        // Handle right-clicking with the claim viewer while holding shift. This will tell the player how many claims
        // are within 100 blocks of them and highlight those claims for them.
        if((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) &&
                player.isSneaking() && event.getMaterial() == RegionProtection.getClaimViewerTool()) {
            List<Region> regions = dm.getRegionsInWorld(player.getWorld()).stream()
                    .filter(region -> region.distanceFromEdge(player.getLocation()) < 100).collect(Collectors.toList());
            player.sendMessage(ChatColor.GOLD + "Found " + regions.size() + " claim" +
                    (regions.size() == 1 ? "" : "s") + " nearby.");
            if(!regions.isEmpty())
                ps.setRegionHighlighter(new RegionHighlighter(player, regions));

            event.setCancelled(true);
            return;
        }

        // Handle regular right clicking with a block
        if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Claim creation, resizing, and subdividing
            if(RegionProtection.getClaimCreationTool().equals(event.getMaterial())) {
                // Setting the first location
                if(ps.getLastClickedBlock() == null) {
                    // Subdivisions will be selected over
                    Region region = dm.getHighestPriorityRegionAt(event.getClickedBlock().getLocation());

                    ps.setLastClickedBlock(event.getClickedBlock().getLocation());

                    // No region here, make a new one
                    if(region == null) {
                        ps.setAction(PlayerRegionAction.CREATE_REGION);
                        player.sendMessage(ChatColor.GOLD + "Claim corner set. Select another corner to create your " +
                                "claim.");
                    }else{ // Modify the region at the clicked location
                        // Permissions check
                        /*if(regions.stream().anyMatch(region -> !region.isOwner(player))) {
                            player.sendMessage(ChatColor.RED + "You do not have permission to modify this claim.");
                            event.setCancelled(true);
                            return;
                        }*/

                        // Clicking a corner will trigger a resizing action
                        if(region.isCorner(ps.getLastClickedBlock())) {
                            ps.setAction(PlayerRegionAction.RESIZE_REGION);
                            ps.setCurrentSelectedRegion(region);
                            player.sendMessage(ChatColor.GOLD + "Claim corner selected. Select another block to " +
                                    "resize the claim.");
                        }else{
                            // Make sure they're not subdividing a subdivision
                            if(region.hasParent())
                                player.sendMessage(ChatColor.RED + "You cannot subdivide this claim further here.");
                            else{
                                ps.setAction(PlayerRegionAction.SUBDIVIDE_REGION);
                                ps.setCurrentSelectedRegion(region);
                                player.sendMessage(ChatColor.GOLD + "You are subdividing this claim. Select another " +
                                        "block to create the subdivision.");
                            }
                        }
                    }
                }else{ // Second location selection, thus completing the current pending action
                    Location vertex = ps.getLastClickedBlock();
                    ps.setLastClickedBlock(null);

                    // Fairly self-explanatory
                    switch(ps.getAction()) {
                        case CREATE_REGION:
                        {
                            Region claim = dm.tryCreateClaim(player, vertex, event.getClickedBlock().getLocation());
                            if(claim != null) {
                                player.sendMessage(ChatColor.GREEN + "Claim created. You have " + ps.getClaimBlocks() +
                                        " claim blocks remaining.");
                                ps.setRegionHighlighter(new RegionHighlighter(player, claim));
                            }
                            break;
                        }

                        case RESIZE_REGION:
                        {
                            Region claim = dm.tryResizeClaim(player, ps.getCurrentSelectedRegion(), vertex,
                                    event.getClickedBlock().getLocation());
                            if(claim != null) {
                                // Don't tell them how many claim blocks they have if they were resizing a subdivision
                                player.sendMessage(ChatColor.GREEN + "Claim resized." + (claim.hasParent() ? ""
                                        : " You have " + ps.getClaimBlocks() + " claim blocks remaining."));
                                ps.setRegionHighlighter(new RegionHighlighter(player, claim));
                            }
                            break;
                        }

                        case SUBDIVIDE_REGION:
                        {
                            Region subdivision = dm.tryCreateSubdivision(player, ps.getCurrentSelectedRegion(), vertex,
                                    event.getClickedBlock().getLocation());
                            if(subdivision != null) {
                                player.sendMessage(ChatColor.GREEN + "Subdivision created.");
                                ps.setRegionHighlighter(new RegionHighlighter(player,
                                        Collections.singleton(ps.getCurrentSelectedRegion()), null, null, true));
                            }
                            break;
                        }
                    }
                }

                event.setCancelled(true);
            }else if(event.getMaterial() == RegionProtection.getClaimViewerTool()) // Claim viewer
                detailClaimAt(player, event.getClickedBlock().getLocation());
        }else if(event.getAction() == Action.RIGHT_CLICK_AIR &&
                event.getMaterial() == RegionProtection.getClaimViewerTool()) { // Long-range claim viewer
            // Limit it to 100 blocks
            RayTraceResult result = player.rayTraceBlocks(100);
            if(result == null) {
                player.sendMessage(ChatColor.RED + "This block is too far away.");
                ps.setRegionHighlighter(null);
            }else
                detailClaimAt(player, result.getHitPosition().toLocation(player.getWorld()));
        }
    }

    /**
     * Handle scrolling to or away from the claim creation tool in a player's hotbar.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOWEST)
    public void onHotbarScroll(PlayerItemHeldEvent event) {
        // Ease of access
        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession(event.getPlayer());

        // Scrolling to the creation tool
        if(Materials.stackType(event.getPlayer().getInventory().getItem(event.getNewSlot())) ==
                RegionProtection.getClaimCreationTool()) {
            // Notify the player of their claim block count
            event.getPlayer().sendMessage(ChatColor.GOLD + "You can claim up to " + ps.getClaimBlocks() +
                    " more blocks.");

            // Highlight regions within 100 blocks of the player
            List<Region> regions = RegionProtection.getDataManager().getRegionsInWorld(event.getPlayer().getWorld())
                    .stream().filter(region -> region.distanceFromEdge(event.getPlayer().getLocation()) < 100)
                    .collect(Collectors.toList());
            if(!regions.isEmpty())
                ps.setRegionHighlighter(new RegionHighlighter(event.getPlayer(), regions));
        }else if(Materials.stackType(event.getPlayer().getInventory().getItem(event.getPreviousSlot())) ==
                RegionProtection.getClaimCreationTool()) { // Scrolling away from the claim creation tool
            // Clear action values
            ps.setLastClickedBlock(null);
            ps.setCurrentSelectedRegion(null);
            ps.setAction(null);
        }
    }

    // Notify the player about the highest priority claim at a given location and highlight it
    private static void detailClaimAt(Player recipient, Location location) {
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(location);

        if(claim == null) {
            recipient.sendMessage(ChatColor.RED + "No one has claimed this block.");
            // If there's no claim at the location clear the highlighter. Allows players to remove their current
            // highlighter themselves.
            RegionProtection.getDataManager().getPlayerSession(recipient).setRegionHighlighter(null);
        }else{
            recipient.sendMessage(ChatColor.GOLD + "This belongs to " + claim.getOwnerName());
            RegionProtection.getDataManager().getPlayerSession(recipient)
                    .setRegionHighlighter(new RegionHighlighter(recipient, claim));
        }
    }
}
