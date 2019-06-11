package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Materials;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.EquipmentSlot;

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
    
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Material blockType = Materials.getMaterial(event.getClickedBlock());
        if(event.getClickedBlock() == null)
            return;
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getClickedBlock().getLocation());
        if(flags == null)
            return;
        switch (event.getAction()){
            case LEFT_CLICK_BLOCK:
            {
                // disable fire extinguishing and dragon egg punching
                Block block = event.getClickedBlock().getRelative(event.getBlockFace());
                if(Material.FIRE.equals(block.getType()) || Material.DRAGON_EGG.equals(blockType)) {
                    if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(Materials.getMaterial(event.getClickedBlock()))) {
                        event.getPlayer().sendMessage(ChatColor.RED + "You cannot break that here.");
                        event.setCancelled(true);
                    }else if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                    }
                    if(Material.FIRE.equals(block.getType())) {
                        Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(),
                                () -> event.getPlayer().sendBlockChange(block.getLocation(), block.getBlockData().clone()), 1L);
                    }
                }
                break;
            }
            case RIGHT_CLICK_BLOCK:
            {
                Material heldItem = Utils.stackType(Utils.heldItem(event.getPlayer(), event.getHand()));
                // Cancel redstone interactions and everything unhandled by blockPlaceEvent for anyone with trust lower than build
                // unhandled
                if(Materials.isPlaceable(Utils.stackType(Utils.heldItem(event.getPlayer(), event.getHand()))) ||
                        Materials.changesOnUse(blockType, heldItem)) {
                    // trust
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                        if(EquipmentSlot.HAND.equals(event.getHand()))
                            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                }
                // Cancel container opening for anyone with trust lower than container
                // containers
                if(Materials.isInventoryHolder(blockType)) {
                    // trust
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
                        if(EquipmentSlot.HAND.equals(event.getHand()))
                            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                }
                // Cancel "doors", redstone inputs and bed entry for anyone with trust lower than access
                // doors switches and beds
                if(Materials.changesOnInteraction(blockType)) {
                    // trust
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
                        if(EquipmentSlot.HAND.equals(event.getHand()))
                            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                }
                break;
            }
            case PHYSICAL:
                if(!(Material.TURTLE_EGG.equals(blockType) || Materials.isPressurePlate(blockType)))
                    return;
                if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(Materials.getMaterial(event.getClickedBlock()))) {
                    event.setCancelled(true);
                    return;
                }
                if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags))
                    event.setCancelled(true);
                break;
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event){
        // TODO: item frame  painting  end crystal  vehicle container  armour stand  flame arrow cauldron tnt  ...
    }
}
