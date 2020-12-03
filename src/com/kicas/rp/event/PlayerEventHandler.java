package com.kicas.rp.event;

import static com.kicas.rp.data.flagdata.EnumFilter.EntityFilter;
import static com.kicas.rp.data.flagdata.EnumFilter.MaterialFilter;
import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.data.flagdata.*;
import com.kicas.rp.util.Entities;
import com.kicas.rp.util.Materials;

import com.kicas.rp.util.Pair;
import com.kicas.rp.util.TextUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;

/**
 * Handle events caused by players.
 */
public class PlayerEventHandler implements Listener {
    /**
     * Tests whether or not the given block type can be broken according to the given flags.
     *
     * @param event the event.
     * @param player the associated player.
     * @param flags the flags.
     * @param blockType the type of block being broken.
     * @return whether or not the event is cancelled.
     */
    private boolean testBreakInteraction(Cancellable event, Player player, FlagContainer flags, Material blockType) {
        if (flags == null || flags.isEffectiveOwner(player))
            return false;

        // Admin flag then trust flag
        if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_BREAK).isBlocked(blockType)) {
            player.sendMessage(ChatColor.RED + "You cannot break that here.");
            event.setCancelled(true);
            return true;
        }
        // Build trust
        else if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player, TrustLevel.BUILD, flags)) {
            player.sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
            return true;
        }

        return false;
    }

    /**
     * Tests whether or not the given held item can be placed according to the given flags.
     *
     * @param event the event.
     * @param player the associated player.
     * @param flags the flags.
     * @param heldItem the item the player is holding.
     * @return whether or not the event is cancelled.
     */
    private boolean testPlaceInteraction(Cancellable event, Player player, EquipmentSlot hand, FlagContainer flags,
                                         Material heldItem) {
        if (flags == null || flags.isEffectiveOwner(player))
            return false;

        // Deny placement of boats, paintings, etc.
        if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_PLACE).isBlocked(heldItem)) {
            if (EquipmentSlot.HAND == hand)
                player.sendMessage(ChatColor.RED + "You cannot place that here.");

            event.setCancelled(true);
            return true;
        }
        // Build trust
        else if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player, TrustLevel.BUILD, flags)) {
            if (EquipmentSlot.HAND == hand)
                player.sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
            return true;
        }

        return false;
    }

    /**
     * Handle players breaking blocks in a region.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        testBreakInteraction(event, event.getPlayer(), flags, event.getBlock().getType());
    }

    /**
     * Handle players placing blocks in a region as well as automatic region extension downward.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());

        // Allow for dynamic region expansion downwards
        if (flags == null || flags instanceof WorldData) {
            // Find the region
            Region region = RegionProtection.getDataManager()
                    .getRegionsAtIgnoreY(event.getBlock().getLocation())
                    .stream()
                    .filter(r -> r.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, r) &&
                            !r.isAdminOwned() && !r.hasParent())
                    .findAny()
                    .orElse(null);

            // Adjust the y-value if the block placed is craftable
            if (region != null && Materials.hasRecipe(event.getBlock().getType()))
                region.getMin().setY(Math.max(0, event.getBlock().getY() - 5));

            return;
        }

        if (testPlaceInteraction(event, event.getPlayer(), event.getHand(), flags, event.getBlock().getType()))
            return;

        // Prompt the player to make a claim if they place a chest and have no claim
        Player player = event.getPlayer();
        if (event.getBlockPlaced().getType() == Material.CHEST && player.hasPermission("rp.claims.create") &&
                RegionProtection.getClaimableWorlds().contains(player.getWorld().getUID())) {
            long claimCount = RegionProtection.getDataManager().getRegionsInWorld(player.getWorld()).stream()
                    .filter(region -> !region.isAdminOwned() && region.isOwner(player.getUniqueId()))
                    .count();

            if (claimCount == 0) {
                TextUtils.sendFormatted(player, "&(gold)You have not made a claim yet! Create one with a claim tool " +
                        "or {&(aqua)/claim} to protect your builds and items.");
            }
        }
    }

    /**
     * Handles interactions concerning the actual block clicked by the player, such as punching a dragon egg or
     * trampling crops.
     *
     * @param event the event.
     * @param blockFlags the flags at the block the player directly interacted with.
     * @param heldItem the item the player is holding.
     * @return whether or not the event is cancelled.
     */
    private boolean handleBlockInteraction(PlayerInteractEvent event, FlagContainer blockFlags, Material heldItem) {
        Material blockType = Materials.blockType(event.getClickedBlock());

        switch (event.getAction()) {
            // Disable dragon egg punching.
            case LEFT_CLICK_BLOCK:
                if (blockType == Material.DRAGON_EGG &&
                        testBreakInteraction(event, event.getPlayer(), blockFlags, Material.DRAGON_EGG)) {
                    return true;
                }
                break;

            // Handle every block related right-click interaction
            case RIGHT_CLICK_BLOCK:
                // Force chest access flag
                if (blockType.name().endsWith("CHEST") && blockType != Material.ENDER_CHEST &&
                        blockFlags.hasFlag(RegionFlag.FORCE_CHEST_ACCESS)) {
                    if (!blockFlags.isAllowed(RegionFlag.FORCE_CHEST_ACCESS)) {
                        event.getPlayer().sendMessage(ChatColor.RED + "You cannot open that here.");
                        event.setCancelled(true);
                        return true;
                    }
                    return false;
                }

                // Block TNT ignition
                if ((heldItem == Material.FLINT_AND_STEEL || heldItem == Material.FIRE_CHARGE) &&
                        blockType == Material.TNT && (!blockFlags.isAllowed(RegionFlag.TNT) ||
                        !blockFlags.isAllowed(RegionFlag.TNT_IGNITION))) {
                    event.getPlayer().sendMessage(ChatColor.RED + "TNT is not allowed here.");
                    event.setCancelled(true);
                    return true;
                }

                // Handle stuff like grass path making
                if (Materials.changesOnUse(blockType, heldItem) &&
                        testBreakInteraction(event, event.getPlayer(), blockFlags, blockType)) {
                    return true;
                }

                if (blockType == Material.CAKE && blockFlags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_ITEM_CONSUMPTION)
                        .isBlocked(Material.CAKE)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot eat that here.");
                    event.setCancelled(true);
                    return true;
                }

                // Handle the opening of block inventory holders
                if (Materials.isInventoryHolder(blockType) || blockType == Material.ANVIL ||
                        blockType == Material.CHIPPED_ANVIL || blockType == Material.DAMAGED_ANVIL) {
                    // Container trust
                    if (!blockFlags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, blockFlags)) {
                        if (EquipmentSlot.HAND == event.getHand())
                            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + blockFlags.getOwnerName() + ".");

                        event.setCancelled(true);
                        return true;
                    }
                }

                // Handle "doors", redstone inputs
                if (blockType.isInteractable()) {
                    if (blockFlags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_BLOCK_USE).isBlocked(blockType)) {
                        if (EquipmentSlot.HAND == event.getHand())
                            event.getPlayer().sendMessage(ChatColor.RED + "You cannot use that here.");

                        event.setCancelled(true);
                        return true;
                    }

                    // Access trust
                    if (!blockFlags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, blockFlags) &&
                            !(blockType == Material.CRAFTING_TABLE || blockType == Material.ENCHANTING_TABLE ||
                                    blockType == Material.ENDER_CHEST || blockType == Material.CAMPFIRE ||
                                    blockType == Material.STONECUTTER || blockType == Material.LOOM ||
                                    blockType == Material.CARTOGRAPHY_TABLE || blockType == Material.GRINDSTONE)) {
                        if (EquipmentSlot.HAND == event.getHand())
                            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + blockFlags.getOwnerName() + ".");

                        event.setCancelled(true);
                        return true;
                    }
                }

                break;
        }

        return false;
    }

    /**
     * Handles an interaction concerning the face of a given block, such as fire extinguishing or armor stand placement.
     *
     * @param event the event.
     * @param faceFlags the flags at the face of the clicked block.
     * @param block the block adjacent to the clicked face.
     * @param heldItem the item the player is holding.
     */
    private void handleBlockFaceInteraction(PlayerInteractEvent event, FlagContainer faceFlags, Block block, Material heldItem) {
        // disable fire extinguishing
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (Materials.blockType(block) == Material.FIRE &&
                    testBreakInteraction(event, event.getPlayer(), faceFlags, Material.FIRE)) {
                // There's a client side glitch where even though the event is cancelled, the fire still disappears
                // for the player, therefore we resend the block so it stays visible for the player.
                Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(),
                        () -> event.getPlayer().sendBlockChange(block.getLocation(), block.getBlockData().clone()), 1L);
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Handle the placing of entities and other items as well as other changes that happen when the player's
            // held item is used on the clicked block.
            if (Materials.isPlaceable(heldItem))
                testPlaceInteraction(event, event.getPlayer(), event.getHand(), faceFlags, heldItem);
        }
    }

    /**
     * Handles trampling, pressure plates, turtle egg stomping, etc.
     *
     * @param event the event.
     * @param flags the flags at the player's location.
     */
    private void handlePhysicalInteraction(PlayerInteractEvent event, FlagContainer flags) {
        Material blockType = Materials.blockType(event.getClickedBlock());

        if (Materials.isPressureSensitive(blockType)) {
            // Handle trampling
            if (
                    (blockType == Material.TURTLE_EGG || blockType == Material.FARMLAND) && (
                            flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_BREAK).isBlocked(blockType) ||
                            !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)
                    )
            ) {
                event.setCancelled(true);
                return;
            }

            if (flags.isEffectiveOwner(event.getPlayer()))
                return;

            // Pressure plates and tripwires require access trust
            if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_BLOCK_USE).isBlocked(blockType) ||
                    !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent players from dumping out buckets in protected areas.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if (flags == null || flags.isEffectiveOwner(event.getPlayer()))
            return;

        testPlaceInteraction(event, event.getPlayer(), EquipmentSlot.HAND, flags, Materials.stackType(event.getItemStack()));
    }

    /**
     * Prevent players from filling buckets in protected areas.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if (flags == null || flags.isEffectiveOwner(event.getPlayer()))
            return;

        testBreakInteraction(event, event.getPlayer(), flags, Materials.blockType(event.getBlock()));
    }

    /**
     * Handles multiple different types of block interactions as detailed below.
     * Left click block:
     * <ul>
     * <li>Fire extinguishing</li>
     * <li>Punching of a dragon egg (causing it to teleport)</li>
     * </ul>
     * Right click block:
     * <ul>
     * <li>Chest opening</li>
     * <li>The placing of "placeables" such as boats, armor stands, etc.</li>
     * <li>The opening of inventory holders, or blocks that contain items.</li>
     * <li>Interactions with blocks that cause a permanent or semi-permanent state change.</li>
     * </ul>
     * Physical:
     * <ul>
     * <li>The breaking of turtle eggs and farmland by jumping on them.</li>
     * <li>The activation of pressure plates and tripwires.</li>
     * </ul>
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Material heldItem = Materials.stackType(Materials.heldItem(event.getPlayer(), event.getHand()));

        // Handle interactions concerning the actual block clicked
        Location location = event.getClickedBlock() == null
                ? event.getPlayer().getLocation()
                : event.getClickedBlock().getLocation();
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(location);
        if (flags != null) {
            if (event.getAction() == Action.PHYSICAL) {
                handlePhysicalInteraction(event, flags);
                return;
            }

            if (flags.isEffectiveOwner(event.getPlayer()))
                return;

            // Generic item-use denial, perhaps make this more robust in the future
            if (!heldItem.isBlock() && flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_ITEM_USE).isBlocked(heldItem)) {
                if (event.getHand() == EquipmentSlot.HAND)
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot use that here.");
                event.setCancelled(true);
                return;
            }

            if (event.useInteractedBlock() == Event.Result.DENY)
                return;

            if (handleBlockInteraction(event, flags, heldItem))
                return;
        }

        if (event.useInteractedBlock() != Event.Result.DENY) {
            // Handle interactions on the face of the block clicked
            Block faceBlock = event.getClickedBlock().getRelative(event.getBlockFace());
            flags = RegionProtection.getDataManager().getFlagsAt(faceBlock.getLocation());
            if (flags != null && !flags.isEffectiveOwner(event.getPlayer()))
                handleBlockFaceInteraction(event, flags, faceBlock, heldItem);
        }
    }

    /**
     * Handles multiple different types of entity interactions as detailed below.
     * <ul>
     * <li>The breaking of leash hitches by right-clicking it.</li>
     * <li>The usage of name tags on entities</li>
     * <li>The accessing of inventory holder entities such as minecart chests.</li>
     * <li>The accessing of interactable entities such as trader entities.</li>
     * </ul>
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Messages are only sent if the main-hand is used to prevent duplicate message sending

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getRightClicked().getLocation());
        if (flags == null || flags.isEffectiveOwner(event.getPlayer()))
            return;

        if (Entities.isChestHolder(event.getRightClicked().getType()) &&
                Materials.stackType(Materials.heldItem(event.getPlayer(), event.getHand())) == Material.CHEST &&
                !flags.isAllowed(RegionFlag.ANIMAL_CONTAINERS)) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot add a chest to this entity here.");
            event.setCancelled(true);
            return;
        }

        if (flags.<EntityFilter>getFlagMeta(RegionFlag.DENY_ENTITY_USE).isBlocked(event.getRightClicked().getType())) {
            if (EquipmentSlot.HAND == event.getHand())
                event.getPlayer().sendMessage(ChatColor.RED + "You cannot use that here.");
            event.setCancelled(true);
            return;
        }

        // Handle breaking leash hitches
        if (event.getRightClicked().getType() == EntityType.LEASH_HITCH) {
            if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_BREAK).isBlocked(Material.LEAD)) {
                if (EquipmentSlot.HAND == event.getHand())
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot break that here.");
                event.setCancelled(true);
                return;
            }

            // Build trust
            if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
                if (EquipmentSlot.HAND == event.getHand())
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
                return;
            }
        }

        Material heldItem = Materials.stackType(Materials.heldItem(event.getPlayer(), event.getHand()));

        if ((heldItem == Material.NAME_TAG ||
                (heldItem == Material.SHEARS && event.getRightClicked().getType() == EntityType.MUSHROOM_COW)) &&
                !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            if (EquipmentSlot.HAND == event.getHand())
                event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");

            event.setCancelled(true);
            return;
        }

        // Handle entity container interactions
        if (Entities.isInventoryHolder(event.getRightClicked().getType())) {
            // Container trust
            if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
                if (EquipmentSlot.HAND == event.getHand())
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");

                event.setCancelled(true);
                return;
            }
        }

        // Handle feedable entities and trader entities
        if (Entities.isInteractable(event.getRightClicked().getType())) {
            // Access trust
            if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.ACCESS, flags)) {
                if (EquipmentSlot.HAND == event.getHand())
                    event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");

                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent players from taking lectern books unless they have container trust and above.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onLecternBookTaken(PlayerTakeLecternBookEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLectern().getLocation());
        if (flags != null && !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "That belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handle the manipulation of an armor stand by a player (requires container trust).
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerManipulateArmorStand(PlayerArmorStandManipulateEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getRightClicked().getLocation());
        if (flags == null)
            return;

        // Container trust
        if (flags.<EntityFilter>getFlagMeta(RegionFlag.DENY_ENTITY_USE).isBlocked(EntityType.ARMOR_STAND) ||
                !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.CONTAINER, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handle the placing of a lead on an entity (requires build trust).
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags == null)
            return;

        // Build trust
        if (flags.<EntityFilter>getFlagMeta(RegionFlag.DENY_ENTITY_USE).isBlocked(event.getEntity().getType()) ||
                !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handle players hitting non-hostile entities inside of a region (requires build trust). Also handles the
     * deny-weapon-use flag.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST) // Highest to force the PVP flag
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags == null)
            return;

        // Only check trust for non-hostile entities
        if (event.getDamager() instanceof Player) {
            if (!flags.isEffectiveOwner((Player) event.getDamager())) {
                Material weapon = Materials.stackType(Materials.heldItem((Player) event.getDamager(), EquipmentSlot.HAND));
                if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_WEAPON_USE).isBlocked(weapon)) {
                    if (weapon != Material.AIR)
                        event.getDamager().sendMessage(ChatColor.RED + "You cannot use that weapon here.");

                    event.setCancelled(true);
                    return;
                }

                if (Entities.isHostile((Player) event.getDamager(), event.getEntity())) {
                    // OP flag to deny damage to hostiles
                    if (!flags.isAllowed(RegionFlag.HOSTILE_DAMAGE)) {
                        event.getDamager().sendMessage(ChatColor.RED + "You cannot damage that here");
                        event.setCancelled(true);
                        return;
                    }
                } else if (Entities.isPassive((Player) event.getDamager(), event.getEntity())) {
                    // OP flag to deny damage to non-hostiles
                    if (flags.hasFlag(RegionFlag.ANIMAL_DAMAGE)) {
                        if (!flags.isAllowed(RegionFlag.ANIMAL_DAMAGE)) {
                            event.getDamager().sendMessage(ChatColor.RED + "You cannot damage that here");
                            event.setCancelled(true);
                        }
                        return;
                    }

                    // Build trust
                    if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) event.getDamager(),
                            TrustLevel.BUILD, flags)) {
                        event.getDamager().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                } else if (Entities.isInventoryHolder(event.getEntityType())) {
                    // Container trust
                    if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) event.getDamager(),
                            TrustLevel.CONTAINER, flags)) {
                        event.getDamager().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // PvP prevention
            event.setCancelled(event.getEntity() instanceof Player && !flags.isAllowed(RegionFlag.PVP));
            return;
        }

        // Prevent arrows, other projectiles, and area effect clouds

        ProjectileSource shooter = null;

        // Determine the shooter
        if (event.getDamager() instanceof Projectile)
            shooter = ((Projectile) event.getDamager()).getShooter();
        else if (event.getDamager() instanceof AreaEffectCloud)
            shooter = ((AreaEffectCloud) event.getDamager()).getSource();

        // For players check trust and PvP
        if (shooter instanceof Player) {
            if (event.getEntity() instanceof Player) {
                event.setCancelled(!flags.isAllowed(RegionFlag.PVP));
            } else {
                event.setCancelled(!Entities.isHostile((Player) shooter, event.getEntity()) &&
                        !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) shooter, TrustLevel.BUILD, flags));
            }
        }
        // Check for region crosses if fired by a dispenser
        else if (shooter instanceof BlockProjectileSource) {
            event.setCancelled(RegionProtection.getDataManager().crossesRegions(((BlockProjectileSource) shooter)
                    .getBlock().getLocation(), event.getEntity().getLocation()));
        }
    }

    /**
     * Handles players attempting to destroy a vehicle (requires build trust).
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDamaged(VehicleDamageEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getVehicle().getLocation());
        if (flags == null)
            return;

        if (event.getAttacker() instanceof Player && !flags.isEffectiveOwner((Player) event.getAttacker())) {
            if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_BREAK)
                    .isBlocked(Materials.forEntity(event.getVehicle()))) {
                event.getAttacker().sendMessage(ChatColor.RED + "You can't break that here.");
                event.setCancelled(true);
                return;
            }

            // Build trust
            if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) event.getAttacker(), TrustLevel.BUILD, flags)) {
                event.getAttacker().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles players attempting to enter a vehicle (requires access trust).
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleEntered(VehicleEnterEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getVehicle().getLocation());
        if (flags == null)
            return;

        if (event.getEntered() instanceof Player) {
            // Access trust
            if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) event.getEntered(), TrustLevel.ACCESS, flags)) {
                event.getEntered().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles players breaking hanging entities including paintings, item frames, and leash hitches (requires build
     * trust).
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingEntityBrokenByPlayer(HangingBreakByEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags == null)
            return;

        if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_BREAK)
                .isBlocked(Materials.forEntity(event.getEntity())) && !(event.getRemover() instanceof Player &&
                flags.isEffectiveOwner((Player) event.getRemover()))) {
            if (event.getRemover() instanceof Player)
                event.getRemover().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");

            event.setCancelled(true);
            return;
        }

        if (event.getRemover() instanceof Player) {
            // Build trust
            if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) event.getRemover(), TrustLevel.BUILD, flags)) {
                event.getRemover().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
                event.setCancelled(true);
                return;
            }
        }

        // Prevent arrows from breaking these entities
        if (event.getRemover() != null && Entities.isPlayerProjectile(event.getRemover().getType())) {
            ProjectileSource shooter = ((Projectile) event.getRemover()).getShooter();
            // For players check trust
            if (shooter instanceof Player) {
                event.setCancelled(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) shooter, TrustLevel.BUILD, flags));
            }
            // Check for region crosses if fired by a dispenser
            else if (shooter instanceof BlockProjectileSource) {
                event.setCancelled(RegionProtection.getDataManager().crossesRegions(((BlockProjectileSource) shooter)
                        .getBlock().getLocation(), event.getEntity().getLocation()));
            }
        }
    }

    /**
     * Handles player damage for invincible flag and fall-damage flag.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
            if (flags == null)
                return;

            // Generic invincibility
            if (flags.isAllowed(RegionFlag.INVINCIBLE)) {
                event.setCancelled(true);
                return;
            }

            // Specifically fall damage
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL)
                event.setCancelled(!flags.isAllowed(RegionFlag.FALL_DAMAGE));
        }
    }

    /**
     * Handle the keep inventory and keep xp flags.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());

        if (flags != null) {
            if (flags.hasFlag(RegionFlag.KEEP_INVENTORY) && flags.isAllowed(RegionFlag.KEEP_INVENTORY)) {
                event.setKeepInventory(true);
                event.getDrops().clear();
            }

            if (flags.hasFlag(RegionFlag.KEEP_XP) && flags.isAllowed(RegionFlag.KEEP_XP)) {
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }

            if(!flags.isEffectiveOwner(event.getEntity()) && flags.hasFlag(RegionFlag.EXIT_GAMEMODE)) {
                event.getEntity().setGameMode(flags.<GameModeMeta>getFlagMeta(RegionFlag.EXIT_GAMEMODE).toGameMode());
            }
        }
    }

    /**
     * Handles artificial respawn location.
     * Handles gamemode changes when respawning.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getPlayer().getLocation());

        if (flags != null){
            if(flags.hasFlag(RegionFlag.RESPAWN_LOCATION)) {
                event.setRespawnLocation(flags.<LocationMeta>getFlagMeta(RegionFlag.RESPAWN_LOCATION).getLocation()); // RESPAWN_LOCATION flag

                FlagContainer respawnFlags = RegionProtection.getDataManager().getFlagsAt(
                        flags.<LocationMeta>getFlagMeta(RegionFlag.RESPAWN_LOCATION).getLocation()); // Flags for region of respawn
                if(respawnFlags != null && respawnFlags.hasFlag(RegionFlag.ENTRY_GAMEMODE)) { // Respawns inside a region, set gamemode to ENTRY_GAMEMODE flag
                    if (!respawnFlags.equals(flags) && !respawnFlags.isEffectiveOwner(event.getPlayer()) && respawnFlags.hasFlag(RegionFlag.ENTRY_GAMEMODE)) { // ENTRY_GAMEMODE flag
                        event.getPlayer().setGameMode(respawnFlags.<GameModeMeta>getFlagMeta(RegionFlag.ENTRY_GAMEMODE).toGameMode());
                    }
                }else{ // Respawns outside a region, set gamemode to EXIT_GAMEMODE flag
                    if (flags.hasFlag(RegionFlag.EXIT_GAMEMODE)) {
                        event.getPlayer().setGameMode(flags.<GameModeMeta>getFlagMeta(RegionFlag.EXIT_GAMEMODE).toGameMode());
                    }
                }
            }

        }

    }

    /**
     * Handles player hunger for Invincible flag.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerHunger(FoodLevelChangeEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags != null && flags.isAllowed(RegionFlag.INVINCIBLE)) {
            ((Player) event.getEntity()).setFoodLevel(20);
            ((Player) event.getEntity()).setSaturation(20.0F);
            event.setCancelled(true);
        }
    }

    /**
     * Handles players attempting to enter a bed (requires access trust).
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBed().getLocation());
        if (flags == null || flags.isEffectiveOwner(event.getPlayer()))
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
     * @see PlayerEventHandler#onPlayerTranslocate(PlayerMoveEvent, boolean)
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        onPlayerTranslocate(event, true);
    }

    /**
     * @see PlayerEventHandler#onPlayerTranslocate(PlayerMoveEvent, boolean)
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerMove(PlayerMoveEvent event) {
        onPlayerTranslocate(event, false);
    }

    /**
     * Handles greetings, farewells, enter commands, exit commands, and the flight flag.
     *
     * @param event the event.
     */
    private void onPlayerTranslocate(PlayerMoveEvent event, boolean isTeleport) {
        FlagContainer fromFlags = RegionProtection.getDataManager().getFlagsAt(event.getFrom());
        FlagContainer toFlags = RegionProtection.getDataManager().getFlagsAt(event.getTo());
        Player player = event.getPlayer();

        if (!isTeleport) {
            // Entrance restriction
            if (toFlags != null && !toFlags.isEffectiveOwner(player) &&
                    toFlags.<BorderPolicy>getFlagMeta(RegionFlag.ENTRANCE_RESTRICTION).getPolicy() == BorderPolicy.Policy.SOFT) {
                Pair<Location, Location> bounds = toFlags.getBounds();
                // Push the player out of the region they're entering
                if (bounds != null) {
                    // Determine which side of the region they player is penetrating and apply an appropriate push

                    Vector min = bounds.getFirst().toVector(),
                            max = bounds.getSecond().toVector();
                    min.setY(0);
                    max.setY(0);
                    max.setX(max.getX() + 1);
                    max.setZ(max.getZ() + 1);

                    Vector playerLoc = player.getLocation().toVector();
                    playerLoc.setY(0);
                    Vector push = new Vector();
                    double dot, dotMax = -1.0;

                    // Lower x-axis
                    dot = Math.abs(playerLoc.clone().subtract(min).normalize().getX());
                    if (dot > dotMax) {
                        dotMax = dot;
                        push = new Vector(0, 0, -1);
                    }

                    // Lower z-axis
                    dot = Math.abs(playerLoc.clone().subtract(min).normalize().getZ());
                    if (dot > dotMax) {
                        dotMax = dot;
                        push = new Vector(-1, 0, 0);
                    }

                    // Upper x-axis
                    dot = Math.abs(playerLoc.clone().subtract(max).normalize().getX());
                    if (dot > dotMax) {
                        dotMax = dot;
                        push = new Vector(0, 0, 1);
                    }

                    // Upper z-axis
                    dot = Math.abs(playerLoc.clone().subtract(max).normalize().getZ());
                    if (dot > dotMax)
                        push = new Vector(1, 0, 0);

                    player.setVelocity(push);
                }
            }
        }

        if (!Objects.equals(fromFlags, toFlags)) {
            if (toFlags != null) {
                if (!toFlags.getFlagMeta(RegionFlag.GREETING).equals(fromFlags == null ? null : fromFlags.getFlagMeta(RegionFlag.GREETING)))
                    toFlags.<TextMeta>getFlagMeta(RegionFlag.GREETING).sendTo(player);

                if (toFlags.hasFlag(RegionFlag.ENTER_COMMAND))
                    toFlags.<CommandMeta>getFlagMeta(RegionFlag.ENTER_COMMAND).execute(player);

                if(!toFlags.isEffectiveOwner(player) && toFlags.hasFlag(RegionFlag.ENTRY_GAMEMODE))
                    player.setGameMode(toFlags.<GameModeMeta>getFlagMeta(RegionFlag.ENTRY_GAMEMODE).toGameMode());

                if (!toFlags.isEffectiveOwner(player) && !toFlags.isAllowed(RegionFlag.ELYTRA_FLIGHT) && player.isGliding())
                    player.setGliding(false);

                if (!toFlags.isAllowed(RegionFlag.PLAYER_COLLISIONS))
                    player.setCollidable(false);

                if (!toFlags.isEffectiveOwner(player)) {
                    BorderPolicy.Policy entrancePolicy = toFlags.<BorderPolicy>getFlagMeta(RegionFlag.ENTRANCE_RESTRICTION).getPolicy();
                    if (isTeleport)
                        event.setCancelled(entrancePolicy != BorderPolicy.Policy.NONE);
                    else
                        event.setCancelled(entrancePolicy == BorderPolicy.Policy.HARD);
                }
            }

            // Flight only applies to players in survival or adventure mode, and only if one of the regions they are
            // crossing has a flight flag.
            if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) &&
                    (toFlags != null && toFlags.hasFlag(RegionFlag.FLIGHT) ||
                    fromFlags != null && fromFlags.hasFlag(RegionFlag.FLIGHT))) {
                boolean allowFlight = toFlags == null ? player.getServer().getAllowFlight()
                        : toFlags.isAllowed(RegionFlag.FLIGHT);
                player.setAllowFlight(allowFlight);

                if (!allowFlight) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 4,
                            false, false, false));
                }
            }

            if (fromFlags != null) {
                if (!fromFlags.getFlagMeta(RegionFlag.FAREWELL).equals(toFlags == null ? null : toFlags.getFlagMeta(RegionFlag.FAREWELL)))
                    fromFlags.<TextMeta>getFlagMeta(RegionFlag.FAREWELL).sendTo(player);

                if (fromFlags.hasFlag(RegionFlag.EXIT_COMMAND))
                    fromFlags.<CommandMeta>getFlagMeta(RegionFlag.EXIT_COMMAND).execute(player);

                if (fromFlags.isAllowed(RegionFlag.PLAYER_COLLISIONS))
                    player.setCollidable(true);

                if(!fromFlags.isEffectiveOwner(player) && fromFlags.hasFlag(RegionFlag.EXIT_GAMEMODE))
                    player.setGameMode(fromFlags.<GameModeMeta>getFlagMeta(RegionFlag.EXIT_GAMEMODE).toGameMode());
            }
        }
    }

    /**
     * Grants the player permission to fly if they log into a region where the flight flag is set to true.
     * Changes the player's gamemode if they log into a region with the entry-gamemode flag
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().isOp()) {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getPlayer().getLocation());
            if (flags != null) {
                event.getPlayer().setAllowFlight(flags.isAllowed(RegionFlag.FLIGHT));

                if (!flags.isEffectiveOwner(event.getPlayer()) && flags.hasFlag(RegionFlag.ENTRY_GAMEMODE)) {
                    event.getPlayer().setGameMode(flags.<GameModeMeta>getFlagMeta(RegionFlag.ENTRY_GAMEMODE).toGameMode());
                }
            }
        }

    }

    /**
     * Handle the deny-command flag.
     * Cancels `/trigger as_trigger...` command when targeting an armour stand
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Remove beginning /'s
        String message = event.getMessage().trim().replaceAll("^(/+)?", "");

        // Go past the namespace if they do something like /minecraft:tp, and also find the end of the command
        int start = message.indexOf(':') + 1, end = message.indexOf(' ');

        // No spaces means that the end of the command is the end of the line
        if (end < 0)
            end = message.length();

        // If start >= end it means we picked up a colon in the arguments, so ignore it and set the start to 0
        if (start >= end)
            start = 0;

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getPlayer().getLocation());
        if (flags != null && !flags.isEffectiveOwner(event.getPlayer())) {

            if (flags.<StringFilter>getFlagMeta(RegionFlag.DENY_COMMAND).isBlocked(message.substring(start, end))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "You cannot use that command here.");
            }

        }

        // If command is /trigger and Armor Statues datapack is installed
        if(message.substring(start, end).equalsIgnoreCase("trigger") &&
                message.split(" ").length > 1 &&
                Bukkit.getServer().getScoreboardManager().getMainScoreboard().getObjective("as_trigger") != null){

            // If objective is as_trigger
            if(message.split(" ")[1].equalsIgnoreCase("as_trigger")){
                // Generate a list of all entities within 3 blocks of the player
                List<Entity> nearbyEntities = event.getPlayer().getNearbyEntities(3, 3, 3);
                if(nearbyEntities.size() > 0) {
                    // Sort (ascending) by distance to the player
                    nearbyEntities.sort((u1, u2) -> {
                        double u1Dist = u1.getLocation().distance(event.getPlayer().getLocation());
                        double u2Dist = u2.getLocation().distance(event.getPlayer().getLocation());
                        return Double.compare(u1Dist, u2Dist);
                    });
                    Entity armorStand = null;
                    // Set armorStand to the entity that matches the selector that Armor Statues uses:
                    // @e[type=armor_stand,distance=..3,tag=!as_locked,sort=nearest,limit=1,nbt=!{Marker:1b},nbt=!{Invulnerable:1b}]
                    for (Entity nearbyEntity : nearbyEntities) {
                        if(nearbyEntity.getType().equals(EntityType.ARMOR_STAND) &&
                                !nearbyEntity.getScoreboardTags().contains("as_locked") &&
                                !((ArmorStand) nearbyEntity).isMarker() &&
                                !nearbyEntity.isInvulnerable()){
                            armorStand = nearbyEntity;
                            break;
                        }
                    }

                    // If there's an armor stand that matches the selector
                    if(armorStand != null){
                        FlagContainer armorStandFlags = RegionProtection.getDataManager().getFlagsAt(armorStand.getLocation());
                        if(armorStandFlags != null){
                            // If the player doesn't have build trust
                            if(!armorStandFlags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, armorStandFlags)) {
                                event.setCancelled(true);
                                event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + armorStandFlags.getOwnerName() + ".");
                            }
                            // If it's an admin claim and the player isn't an owner
                            if(armorStandFlags.isAdminOwned() &&
                                    !armorStandFlags.isEffectiveOwner(event.getPlayer()) &&
                                    !armorStandFlags.isAllowed(RegionFlag.MODIFY_ARMOR_STANDS)){
                                event.setCancelled(true);
                                event.getPlayer().sendMessage(ChatColor.RED + "You cannot modify that here.");
                            }
                        }
                    }
                }

            }
        }
    }

    /**
     * Handles the deny-item-consumption flag.
     *
     * @param event the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getPlayer().getLocation());
        if (flags != null && !flags.isEffectiveOwner(event.getPlayer()) &&
                flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_ITEM_CONSUMPTION).isBlocked(event.getItem().getType())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot eat that here.");
            event.setCancelled(true);
        }
    }

    /**
     * Handles deny-item-use for totems.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(player.getLocation());
        if (flags != null && !flags.isEffectiveOwner(player) &&
                flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_ITEM_USE).isBlocked(Material.TOTEM_OF_UNDYING)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles deny-item-use for fishing rods.
     *         entity pulling with fishing rods.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerUseFishingRod(PlayerFishEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getPlayer().getLocation());

        if (flags != null && !flags.isEffectiveOwner(event.getPlayer()) &&
                flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_ITEM_USE).isBlocked(Material.FISHING_ROD)) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot use that here.");
            event.setCancelled(true);
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || event.getCaught() == null)
            return;

        flags = RegionProtection.getDataManager().getFlagsAt(event.getCaught().getLocation());
        if (flags != null && !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(event.getPlayer(), TrustLevel.BUILD, flags)) {
            event.getPlayer().sendMessage(ChatColor.RED + "This belongs to " + flags.getOwnerName() + ".");
            event.setCancelled(true);
        }
    }

    /**
     * Handles deny-item-use for ender pearls.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onProjectileLaunched(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(player.getLocation());

            if (flags == null || flags.isEffectiveOwner(player))
                return;

            Material thrownMaterial = Materials.forEntity(event.getEntity());
            if (flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_ITEM_USE).isBlocked(thrownMaterial) ||
                    flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_WEAPON_USE).isBlocked(thrownMaterial)) {
                player.sendMessage(ChatColor.RED + "You cannot use that here.");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles deny-weapon-use for bows and crossbows.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(player.getLocation());

            if (flags != null && !flags.isEffectiveOwner(player) &&
                    flags.<MaterialFilter>getFlagMeta(RegionFlag.DENY_WEAPON_USE).isBlocked(event.getBow().getType())) {
                event.setCancelled(true);

                // There's a bug where crossbow arrows are not replaced when the event is cancelled, so fix that
                if (event.getBow().getType() == Material.CROSSBOW) {
                    if (!(event.getProjectile() instanceof AbstractArrow))
                        return;

                    Entity projectile = event.getProjectile();
                    if (!(projectile instanceof AbstractArrow) ||
                            ((AbstractArrow) projectile).getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED) {
                        ItemStack replacement = ((CrossbowMeta)event.getBow().getItemMeta()).getChargedProjectiles().get(0).clone();
                        replacement.setAmount(1);
                        player.getInventory().addItem(replacement);
                        player.sendMessage(ChatColor.RED + "You cannot use that here.");
                    }
                } else
                    player.sendMessage(ChatColor.RED + "You cannot use that here.");
            }
        }
    }

    /**
     * Handles elytra-flight flag.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onElytraToggled(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(player.getLocation());
            event.setCancelled(flags != null && !flags.isEffectiveOwner(player) &&
                    !flags.isAllowed(RegionFlag.ELYTRA_FLIGHT) && event.isGliding());
        }
    }

    /**
     * Handles the item-damage flag.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getPlayer().getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.ITEM_DAMAGE));
    }

    /**
     * Handles the riptide flag.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        final Player player = event.getPlayer();
        final Location location = player.getLocation().clone();

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(location);
        if (flags != null && !flags.isAllowed(RegionFlag.RIPTIDE)) {
            // Since riptiding is handled client-side we have to cancel manually
            Bukkit.getScheduler().runTask(RegionProtection.getInstance(), () -> {
                player.teleport(location);
                player.setVelocity(new Vector(0.0, 0.0, 0.0));
                ((CraftPlayer) player).getHandle().stopRiding();
            });
        }
    }
}
