package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Entities;
import com.kicas.rp.util.Materials;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Objects;

/**
 * Handle events caused by players.
 */
public class PlayerEventHandler implements Listener {
    /**
     * Handle players breaking blocks in a region.
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;
        
        // Check admin flag first
        if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(event.getBlock().getType())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break that here.");
            event.setCancelled(true);
            return;
        }

        // Check trust last
        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handle players placing blocks in a region.
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());

        // Allow for dynamic region expansion downwards
        if(flags == null) {
            // Find the region
            Region region = RegionProtection.getDataManager().getRegionAtIgnoreY(event.getBlock().getLocation())
                    .stream().filter(r -> r.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(),
                            TrustLevel.BUILD, r) && !r.isAdminOwned() && !r.hasParent()).findAny().orElse(null);

            // Adjust the y-value
            if(region != null && Materials.hasRecipe(event.getBlock().getType()))
                region.getMin().setY(event.getBlock().getY());

            return;
        }

        // Check admin flag first
        if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_PLACE).isAllowed(event.getBlock().getType())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place that here.");
            event.setCancelled(true);
            return;
        }

        // Check trust last
        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place that here.");
            event.setCancelled(true);
        }
    }

    /**
     * Handles multiple different types of block interaction as detailed below.
     * Left click block:
     * <ul>
     *     <li>Fire extinguishing</li>
     *     <li>Punching of a dragon egg (causing it to teleport)</li>
     * </ul>
     * Right click block:
     * <ul>
     *     <li>The placing of "placeables" such as boats, armor stands, etc.</li>
     *     <li>The opening of inventory holders, or blocks that contain items.</li>
     *     <li>Interactions with blocks that cause a permanent or semi-permanent state change.</li>
     * </ul>
     * Physical:
     * <ul>
     *     <li>The breaking of turtle eggs by jumping on them.</li>
     *     <li>The activation of pressure plates and tripwires.</li>
     * </ul>
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getClickedBlock() == null)
            return;
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getClickedBlock().getLocation());
        if(flags == null)
            return;
    
        Material blockType = event.getClickedBlock().getType();
        switch(event.getAction()) {
            // Disable fire extinguishing and dragon egg punching.
            case LEFT_CLICK_BLOCK:
            {
                // For fire extinguishing
                Block relativeBlock = event.getClickedBlock().getRelative(event.getBlockFace());

                if(relativeBlock.getType() == Material.FIRE || blockType == Material.DRAGON_EGG) {
                    // Admin flag then trust flag
                    if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK)
                            .isAllowed(Materials.blockType(event.getClickedBlock()))) {
                        event.getPlayer().sendMessage(ChatColor.RED + "You cannot break that here.");
                        event.setCancelled(true);
                    }else if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST)
                            .hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                    }

                    // There's a client side glitch where even though the event is cancelled, the fire still disappears
                    // for the player, therefore we resend the block so it stays visible for the player.
                    if(Material.FIRE.equals(relativeBlock.getType())) {
                        Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(),
                                () -> event.getPlayer().sendBlockChange(relativeBlock.getLocation(),
                                        relativeBlock.getBlockData().clone()), 1L);
                    }
                }

                break;
            }

            // Handle every block related right-click interaction
            case RIGHT_CLICK_BLOCK:
            {
                if (event.getClickedBlock().getType().name().endsWith("CHEST") &&
                        flags.hasFlag(RegionFlag.FORCE_CHEST_ACCESS)) {
                    if(!flags.isAllowed(RegionFlag.FORCE_CHEST_ACCESS)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "You cannot open that here.");
                        event.setCancelled(true);
                    }
                    return;
                }
    
                Material heldItem = Materials.stackType(Materials.heldItem(event.getPlayer(), event.getHand()));

                if((heldItem == Material.FLINT_AND_STEEL || heldItem == Material.FIRE_CHARGE) &&
                        blockType == Material.TNT && !flags.isAllowed(RegionFlag.TNT)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "TNT is not allowed here.");
                    event.setCancelled(true);
                    return;
                }
                
                // Handle the placing of entities and other items as well as other changes that happen when the player's
                // held item is used on the clicked block.
                if(Materials.isUsable(heldItem) || Materials.changesOnUse(blockType, heldItem)) {
                    // Deny placement of boats, paintings, etc.
                    if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_PLACE).isAllowed(heldItem)) {
                        if(EquipmentSlot.HAND.equals(event.getHand()))
                            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place that here.");
                        event.setCancelled(true);
                        return;
                    }

                    // Build trust
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                        if(EquipmentSlot.HAND.equals(event.getHand()))
                            event.getPlayer().sendMessage(ChatColor.RED +
                                    "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                }

                // Handle the opening of block inventory holders
                if(Materials.isInventoryHolder(blockType) || blockType == Material.ANVIL ||
                        blockType == Material.CHIPPED_ANVIL || blockType == Material.DAMAGED_ANVIL) {
                    // Container trust
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER,
                            flags)) {
                        if(EquipmentSlot.HAND.equals(event.getHand()))
                            event.getPlayer().sendMessage(ChatColor.RED +
                                    "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                }

                // Handle "doors", redstone inputs
                if(Materials.changesOnInteraction(blockType)) {
                    // Access trust
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
                        if(EquipmentSlot.HAND.equals(event.getHand()))
                            event.getPlayer().sendMessage(ChatColor.RED +
                                    "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                }

                break;
            }

            // Handle players stepping on things such as turtle eggs, tripwires, farmland, and pressure plates
            case PHYSICAL:
                if(Materials.isPressureSensitive(blockType)) {
                    // Handle trampling
                    if(blockType == Material.TURTLE_EGG || blockType == Material.FARMLAND) {
                        event.setCancelled(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(blockType) ||
                                !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(),
                                        TrustLevel.BUILD, flags));
                        return;
                    }

                    // Pressure plates and tripwires require access trust
                    if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags))
                        event.setCancelled(true);
                }

                break;
        }
    }

    /**
     * Prevent players from taking lectern books unless they have container trust and above.
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onLecternBookTaken(PlayerTakeLecternBookEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLectern().getLocation());
        if(flags != null &&!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(),
                TrustLevel.CONTAINER, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "That belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handles multiple different types of entity interactions as detailed below.
     * <ul>
     *     <li>The breaking of leash hitches by right-clicking it.</li>
     *     <li>The accessing of intentory holder entities such as minecart chests.</li>
     *     <li>The accessing of interactable entities such as trader entites.</li>
     * </ul>
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Messages are only sent if the main-hand is used to prevent duplicate message sending

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getRightClicked().getLocation());
        if(flags == null)
            return;
        
        // Handle breaking leash hitches
        if(event.getRightClicked().getType() == EntityType.LEASH_HITCH) {
            if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK).isAllowed(Material.LEAD)) {
                if(EquipmentSlot.HAND.equals(event.getHand()))
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot break that here.");
                event.setCancelled(true);
                return;
            }

            // Build trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                if(EquipmentSlot.HAND.equals(event.getHand()))
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
                return;
            }
        }

        Material heldItem = Materials.stackType(Materials.heldItem(event.getPlayer(), event.getHand()));

        if((heldItem == Material.NAME_TAG ||
                (heldItem == Material.SHEARS && event.getRightClicked().getType() == EntityType.MUSHROOM_COW)) &&
                !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            if(EquipmentSlot.HAND.equals(event.getHand()))
                event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
            return;
        }

        // Handle entity container interactions
        if(Entities.isInventoryHolder(event.getRightClicked().getType())) {
            // Container trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
                if(EquipmentSlot.HAND.equals(event.getHand()))
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
                return;
            }
        }

        // Handle feedable entities and trader entities
        if(Entities.isInteractable(event.getRightClicked().getType())) {
            // Access trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
                if(EquipmentSlot.HAND.equals(event.getHand()))
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handle the manipulation of an armor stand by a player (requires container trust).
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerManipulateArmorStand(PlayerArmorStandManipulateEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getRightClicked().getLocation());
        if(flags == null)
            return;

        // Container trust
        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handle the placing of a lead on an entity (requires build trust).
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if(flags == null)
            return;

        // Build trust
        if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handle players hitting non-hostile entities inside of a region (requires build trust).
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST) // Highest to force the PVP flag
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if(flags == null)
            return;

        // Only check trust for non-hostile entities
        if(event.getDamager() instanceof Player) {
            if (Entities.isHostile((Player) event.getDamager(), event.getEntity())) {
                // OP flag to deny damage to hostiles
                if(!flags.isAllowed(RegionFlag.HOSTILE_DAMAGE)) {
                    event.getDamager().sendMessage(ChatColor.RED + "You cannot damage that here");
                    event.setCancelled(true);
                    return;
                }
                
                // Build trust
                if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) event.getDamager(),
                        TrustLevel.BUILD, flags)) {
                    event.getDamager().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                    event.setCancelled(true);
                    return;
                }
            } else if (Entities.isPassive((Player) event.getDamager(), event.getEntity())) {
                // OP flag to deny damage to non-hostiles
                if(!flags.isAllowed(RegionFlag.ANIMAL_DAMAGE)) {
                    event.getDamager().sendMessage(ChatColor.RED + "You cannot damage that here");
                    event.setCancelled(true);
                    return;
                }
            }

            // PvP prevention
            event.setCancelled(event.getEntity() instanceof Player && !flags.isAllowed(RegionFlag.PVP));
            return;
        }

        // Prevent arrows, other projectiles, and area effect clouds

        ProjectileSource shooter = null;

        // Determine the shooter
        if(event.getDamager() instanceof Projectile)
            shooter = ((Projectile)event.getDamager()).getShooter();
        else if(event.getDamager() instanceof AreaEffectCloud)
            shooter = ((AreaEffectCloud)event.getDamager()).getSource();

        if(shooter instanceof Player) { // For players check trust and PvP
            if(event.getEntity() instanceof Player) {
                event.setCancelled(!flags.isAllowed(RegionFlag.PVP));
            } else {
                event.setCancelled(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) shooter,
                        TrustLevel.BUILD, flags));
            }
        }else if(shooter instanceof BlockProjectileSource) { // Check for region crosses if fired by a dispenser
            event.setCancelled(RegionProtection.getDataManager().crossesRegions(((BlockProjectileSource)shooter)
                    .getBlock().getLocation(), event.getEntity().getLocation()));
        }
    }

    /**
     * Handles players attempting to destroy a vehicle (requires build trust).
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onVehicleDamaged(VehicleDamageEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getVehicle().getLocation());
        if(flags == null)
            return;

        if(event.getAttacker() instanceof Player) {
            if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK)
                    .isAllowed(Materials.forEntity(event.getVehicle()))) {
                event.getAttacker().sendMessage(ChatColor.RED + "You can't break that here.");
                event.setCancelled(true);
                return;
            }

            // Build trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getAttacker(), TrustLevel.BUILD, flags)) {
                event.getAttacker().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles players attempting to enter a vehicle (requires access trust).
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onVehicleEntered(VehicleEnterEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getVehicle().getLocation());
        if(flags == null)
            return;

        if(event.getEntered() instanceof Player) {
            // Access trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getEntered(), TrustLevel.ACCESS, flags)) {
                event.getEntered().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles players breaking hanging entities including paintings, item frames, and leash hitches (requires build
     * trust).
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onHangingEntityBrokenByPlayer(HangingBreakByEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if(flags == null)
            return;

        if(!flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_BREAK)
                .isAllowed(Materials.forEntity(event.getEntity()))) {
            if(event.getRemover() instanceof Player)
                event.getRemover().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
            return;
        }

        if(event.getRemover() instanceof Player) {
            // Build trust
            if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getRemover(), TrustLevel.BUILD, flags)) {
                event.getRemover().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
                return;
            }
        }

        // Prevent arrows from breaking these entities
        if(event.getRemover() != null && event.getRemover().getType() == EntityType.ARROW) {
            ProjectileSource shooter = ((Arrow)event.getEntity()).getShooter();
            if(shooter instanceof Player) { // For players check trust
                event.setCancelled(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)shooter,
                        TrustLevel.BUILD, flags));
            }else if(shooter instanceof BlockProjectileSource) { // Check for region crosses if fired by a dispenser
                event.setCancelled(RegionProtection.getDataManager().crossesRegions(((BlockProjectileSource)shooter)
                                .getBlock().getLocation(), event.getEntity().getLocation()));
            }
        }
    }
    
    /**
     * Handles player damage for Invincible flag.
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerDamage(EntityDamageEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        event.setCancelled(event.getEntity() instanceof Player && flags != null &&
                flags.isAllowed(RegionFlag.INVINCIBLE));
    }
    
    /**
     * Handles player hunger for Invincible flag.
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerHunger(FoodLevelChangeEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        event.setCancelled(event.getEntity() instanceof Player && flags != null &&
                flags.isAllowed(RegionFlag.INVINCIBLE));
    }
    
    /**
     * Handles players attempting to enter a bed (requires access trust).
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBed().getLocation());
        if (flags == null)
            return;
        
        if (!flags.isAllowed(RegionFlag.BED_ENTER)) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot use that here.");
            event.setCancelled(true);
            return;
        }
        
        if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handles greetings.
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerMove(PlayerMoveEvent event) {
        FlagContainer fromFlags = RegionProtection.getDataManager().getFlagsAt(event.getFrom());
        FlagContainer toFlags = RegionProtection.getDataManager().getFlagsAt(event.getTo());

        if(!Objects.equals(fromFlags, toFlags) && toFlags != null && toFlags.hasFlag(RegionFlag.GREETING))
            event.getPlayer().spigot().sendMessage(toFlags.<TextMeta>getFlagMeta(RegionFlag.GREETING).getFormatted());
    }
}
