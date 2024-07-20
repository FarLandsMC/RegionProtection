package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;

import com.kicas.rp.data.flagdata.EnumFilter;
import com.kicas.rp.data.flagdata.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.Entities;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Objects;

/**
 * Handles events caused or related to non-player entities.
 */
public class EntityEventHandler implements Listener {
    /**
     * Handle enderman grief and fire arrows/dispenser-fired fire balls causing damage by hitting TNT or setting things
     * on fire. Also handles the animal-grief-blocks and hostile-grief-blocks flags.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.FALLING_BLOCK)
            return;

        DataManager dm = RegionProtection.getDataManager();
        FlagContainer flags = dm.getFlagsAt(event.getBlock().getLocation());
        if (flags == null)
            return;

        if (event.getEntityType() == EntityType.ARROW || event.getEntityType() == EntityType.SMALL_FIREBALL) {
            if (event.getBlock().getType() == Material.TNT && (!flags.isAllowed(RegionFlag.TNT) ||
                    !flags.isAllowed(RegionFlag.TNT_IGNITION))) {
                event.setCancelled(true);
                return;
            }

            ProjectileSource shooter = ((Projectile) event.getEntity()).getShooter();
            // For players check trust
            if (shooter instanceof Player) {
                event.setCancelled(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) shooter,
                        TrustLevel.BUILD, flags));
                return;
            }
            // Check for region crosses if fired by a dispenser
            else if (shooter instanceof BlockProjectileSource) {
                event.setCancelled(dm.crossesRegions(((BlockProjectileSource) shooter).getBlock().getLocation(),
                        event.getBlock().getLocation()));
                return;
            }
        }

        event.setCancelled(
                !flags.isAllowed(RegionFlag.ANIMAL_GRIEF_BLOCKS) && Entities.isPassive(event.getEntityType()) ||
                !flags.isAllowed(RegionFlag.HOSTILE_GRIEF_BLOCKS) && (
                        Entities.isMonster(event.getEntityType()) ||
                        !event.getEntityType().isAlive()
                )
        );
    }

    /**
     * Handles the hostile-grief-entities flag.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        // event.getEntityType() returns the damagee
        if (flags == null || event.getDamager() instanceof Player || event.getEntity() instanceof Player ||
                flags.isAllowed(RegionFlag.HOSTILE_GRIEF_ENTITIES) || Entities.isMonster(event.getEntityType())) {
            return;
        }

        if (event.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
            if (!(shooter instanceof Player || shooter instanceof BlockProjectileSource))
                event.setCancelled(shooter instanceof LivingEntity && Entities.isMonster(((LivingEntity) shooter).getType()));
        } else
            event.setCancelled(true);
    }

    /**
     * Handles the tnt-entity-damage flag.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags == null)
            return;

        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (!flags.isAllowed(RegionFlag.TNT_ENTITY_DAMAGE)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (!(flags.isAllowed(RegionFlag.HOSTILE_GRIEF_ENTITIES) ||
                    Entities.isMonster(event.getEntityType()) || EntityType.PLAYER == event.getEntityType())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handle frost walker and snow golem trails.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityFormBlock(EntityBlockFormEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if (flags == null)
            return;

        if ((event.getEntity().getType() == EntityType.SNOW_GOLEM && !flags.isAllowed(RegionFlag.ANIMAL_GRIEF_BLOCKS)))
            event.setCancelled(true);
        else if (event.getEntity().getType() == EntityType.PLAYER) {
            if (!flags.isAllowed(RegionFlag.ICE_CHANGE))
                event.setCancelled(true);
            else if (!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) event.getEntity(),
                    TrustLevel.BUILD, flags)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent entity explosions (including the explosion of the primed TNT entity) from damaging claims.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (EntityType.TNT.equals(event.getEntityType())) {
            // If the explosion occurs in a location where tnt is not allowed, cancel the event altogether
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLocation());
            if (flags != null && !flags.isAllowed(RegionFlag.TNT)) {
                event.setCancelled(true);
                return;
            }

            // Individual block checks are required since the TNT could have been launched
            Entity igniter = ((TNTPrimed) event.getEntity()).getSource();
            if (igniter instanceof Player) {
                // Trust check for player igniters
                event.blockList().removeIf(block -> {
                    FlagContainer flags0 = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                    return flags0 != null && !flags0.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player) igniter,
                            TrustLevel.BUILD, flags0);
                });
            }

            // Just to make sure no damage is done on the border of a region
            event.blockList().removeIf(block -> {
                FlagContainer flags0 = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                return flags0 != null && (!flags0.isAllowed(RegionFlag.TNT) || !flags0.isAllowed(RegionFlag.TNT_BLOCK_DAMAGE));
            });
        } else {
            // If the mob explosion occurs in an area where mob grief is not allowed, cancel the event altogether
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLocation());
            if (flags != null && !flags.isAllowed(RegionFlag.HOSTILE_GRIEF_ENTITIES)) {
                event.setCancelled(true);
                return;
            }

            // Prevent explosions caused by entities other than TNT
            event.blockList().removeIf(block -> {
                FlagContainer flags0 = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                return flags0 != null && !flags0.isAllowed(RegionFlag.HOSTILE_GRIEF_BLOCKS);
            });
        }
    }

    /**
     * Prevent entities teleporting to another dimension via portal.
     *
     * @param event the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPortalEvent(EntityPortalEvent event){
        FlagContainer flagsAt = RegionProtection.getDataManager().getFlagsAt(event.getTo());
        if (flagsAt == null)
            return;
        if (flagsAt.<EnumFilter.EntityFilter>getFlagMeta(RegionFlag.DENY_ENTITY_TELEPORT)
                .isBlocked(event.getEntityType())) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles hostile entity explosions damaging hanging entities.
     *
     * @param event the event.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingEntityBroken(HangingBreakEvent event) {
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
            if (flags != null && !flags.isAllowed(RegionFlag.HOSTILE_GRIEF_ENTITIES))
                event.setCancelled(true);
        }
    }

    /**
     * Prevent splash potions from splashing. Note: effects from outside a border may slip in.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        handlePotionSplash(event);
    }

    /**
     * Prevent lingering splash potions from splashing. Note: effects from outside a border may slip in.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(LingeringPotionSplashEvent event) {
        handlePotionSplash(event);
    }

    /**
     * Handles a splash potion, or lingering potion splash event.
     *
     * @param event the event.
     * @param <T>   the event type.
     */
    private <T extends ProjectileHitEvent & Cancellable> void handlePotionSplash(T event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags != null && !flags.isAllowed(RegionFlag.POTION_SPLASH)) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    /**
     * Prevent pets teleporting to a region.
     * Prevents Armor Stand from teleporting to new region
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        FlagContainer toFlags = RegionProtection.getDataManager().getFlagsAt(event.getTo());
        FlagContainer fromFlags = RegionProtection.getDataManager().getFlagsAt(event.getFrom());
        if (toFlags == null)
            return;

        if (toFlags.<EnumFilter.EntityFilter>getFlagMeta(RegionFlag.DENY_ENTITY_TELEPORT).isBlocked(event.getEntityType())) {
            event.setCancelled(true);
            return;
        }
        // Stop all armor stands teleporting into another region when the armor statues datapack is installed
        if(event.getEntity().getType().equals(EntityType.ARMOR_STAND) &&
                !Objects.equals(toFlags, fromFlags) &&
                Bukkit.getServer().getScoreboardManager().getMainScoreboard().getObjective("as_trigger") != null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(event.getEntity() instanceof Tameable && ((Tameable) event.getEntity()).isTamed() &&
                !toFlags.isAllowed(RegionFlag.FOLLOW));
    }

    /**
     * Prevent mobs from becoming aggro towards players inside a region.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() == null)
            return;

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getTarget().getLocation());
        event.setCancelled(flags != null &&
                flags.<EnumFilter.EntityFilter>getFlagMeta(RegionFlag.DENY_AGGRO).isBlocked(event.getEntity().getType()));
    }

    /**
     * Handle hostile entities igniting fireballs in claims.
     *
     * @param event the event;
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockIgnited(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL) {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
            if (flags != null && !flags.isAllowed(RegionFlag.HOSTILE_GRIEF_ENTITIES))
                event.setCancelled(true);
        }
    }

    /**
     * Handle entities picking up items
     *
     * @param event the event;
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if(event.getEntityType() != EntityType.PLAYER) {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());

            if(flags != null &&
                flags.hasFlag(RegionFlag.DENY_ENTITY_PICKUP) &&
                flags.<EnumFilter.MaterialFilter>getFlagMeta(RegionFlag.DENY_ENTITY_PICKUP).isBlocked(event.getItem().getItemStack().getType())
            ) {
                event.setCancelled(true);
            }

        }
    }
}
