package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Entities;
import com.kicas.rp.util.Materials;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
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
        if(event.getClickedBlock() == null)
            return;
        Material blockType = event.getClickedBlock().getType();
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
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getRightClicked().getLocation());
        if(flags == null)
            return;
        
        // Cancel breaking leash hitches
        if (event.getRightClicked().getType() == EntityType.LEASH_HITCH) {
            // trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                if(EquipmentSlot.HAND.equals(event.getHand()))
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
                return;
            }
        }
        // Cancel entity containers for anyone with trust lower than container
        if (Entities.isInventoryHolder(event.getRightClicked().getType())) {
            // trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
                if(EquipmentSlot.HAND.equals(event.getHand()))
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
                return;
            }
        }
        // Cancel feedable entities and trading for anyone with trust lower than access
        if (Entities.isInteractable(event.getRightClicked().getType())) {
            // trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
                if(EquipmentSlot.HAND.equals(event.getHand()))
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerManipulateArmorStand(PlayerArmorStandManipulateEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getRightClicked().getLocation());
        if(flags == null)
            return;

        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if(flags == null)
            return;

        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if(flags == null)
            return;

        if(event.getDamager() instanceof Player) {
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getDamager(), TrustLevel.BUILD, flags)) {
                event.getDamager().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onVehicleDamaged(VehicleDamageEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getVehicle().getLocation());
        if(flags == null)
            return;

        if(event.getAttacker() instanceof Player) {
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getAttacker(), TrustLevel.BUILD, flags)) {
                event.getAttacker().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onVehicleEntered(VehicleEnterEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getVehicle().getLocation());
        if(flags == null)
            return;

        if(event.getEntered() instanceof Player) {
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getEntered(), TrustLevel.ACCESS, flags)) {
                event.getEntered().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingEntityBroken(HangingBreakByEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if(flags == null)
            return;

        if(event.getRemover() instanceof Player) {
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getRemover(), TrustLevel.BUILD, flags)) {
                event.getRemover().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }
}
