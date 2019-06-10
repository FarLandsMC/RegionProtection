package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.MaterialUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerEventHandler implements Listener {
    
    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;

        if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(event.getBlock().getType())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break that here.");
            event.setCancelled(true);
            return;
        }

        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;

        if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_PLACE).isAllowed(event.getBlock().getType())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place that here.");
            event.setCancelled(true);
            return;
        }

        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place that here.");
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getClickedBlock().getLocation());
        switch (event.getAction()){
            case LEFT_CLICK_BLOCK:
                if (flags == null)
                    return;
                // disable fire extinguishing
                Block block = event.getClickedBlock().getRelative(event.getBlockFace());
                if (block.getType().equals(Material.FIRE)) {
                    if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(MaterialUtils.getMaterial(event.getClickedBlock()))) {
                        event.getPlayer().sendMessage(ChatColor.RED + "You cannot extinguish that here.");
                        event.setCancelled(true);
                        return;
                    }
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                    }
                }
                break;
            case RIGHT_CLICK_AIR:
                // TODO: handle claim tool features
                // getTargetBlock(player, 100);
                break;
            case RIGHT_CLICK_BLOCK:
                // TODO: handle claim tool features
                // TODO: cake cauldron dragon egg flower pots anvil (axe shovel hoe)
                if (flags == null)
                    return;
                if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_SPAWN).isAllowed(MaterialUtils.getMaterial(event.getClickedBlock()))) {
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot place that here.");
                    event.setCancelled(true);
                    return;
                }
                // Cancel redstone interactions and everything unhandled by blockPlaceEvent for anyone with trust lower than build
                // unhandled
                if (MaterialUtils.PLACEABLES.contains(event.getPlayer().getItemInHand().getType()) || MaterialUtils.COMPONENTS.contains(event.getClickedBlock().getType())
                        || (MaterialUtils.RAILS.contains(event.getClickedBlock().getType()) && MaterialUtils.MINECARTS.contains(event.getPlayer().getItemInHand().getType()))
                        || (Material.WATER.equals(event.getClickedBlock().getType()) && MaterialUtils.BOATS.contains(event.getPlayer().getItemInHand().getType()))) {
                    // trust
                    if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                    } else
                        return;
                }
                // Cancel container opening for anyone with trust lower than container
                // containers
                if(MaterialUtils.CONTAINERS.contains(event.getClickedBlock().getType())
                        || MaterialUtils.SHULKERS.contains(event.getClickedBlock().getType())) {
                    // trust
                    if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                    } else
                        return;
                }
                // Cancel "doors", redstone inputs and bed entry for anyone with trust lower than access
                // doors switches and beds
                if (MaterialUtils.DOORS.contains(event.getClickedBlock().getType()) || MaterialUtils.TRAPDOORS.contains(event.getClickedBlock().getType())
                    || MaterialUtils.GATES.contains(event.getClickedBlock().getType()) || MaterialUtils.BUTTONS.contains(event.getClickedBlock().getType())
                    || Material.LEVER.equals(event.getClickedBlock().getType())|| MaterialUtils.BEDS.contains(event.getClickedBlock().getType())) {
                    // trust
                    if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                    } else
                        return;
                }
                break;
            case PHYSICAL:
                // TODO: maybe disable pressure plates
                if (flags == null)
                    return;
                if (!Material.TURTLE_EGG.equals(MaterialUtils.getMaterial(event.getClickedBlock())))
                    return;
                
                if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(MaterialUtils.getMaterial(event.getClickedBlock()))) {
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot break that here.");
                    event.setCancelled(true);
                    return;
                }
                if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                    event.setCancelled(true);
                }
        }
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event){
        // TODO: item frame  painting  end crystal  vehicle container  armour stand  flame arrow cauldron tnt  ...
    }
}
