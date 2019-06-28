package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;

import com.kicas.rp.data.flagdata.EnumFilter;
import com.kicas.rp.data.flagdata.TrustMeta;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.*;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Handles events caused or related to non-player entities.
 */
public class EntityEventHandler implements Listener {
    /**
     * Handle enderman grief and fire arrows/dispenser-fired fire balls causing damage by hitting TNT or setting things
     * on fire.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        DataManager dm = RegionProtection.getDataManager();
        FlagContainer flags = dm.getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;

        if((event.getEntityType() == EntityType.ARROW || event.getEntityType() == EntityType.SMALL_FIREBALL)) {
            if(event.getBlock().getType() == Material.TNT && !flags.isAllowed(RegionFlag.TNT)) {
                event.setCancelled(true);
                return;
            }

            ProjectileSource shooter = ((Arrow)event.getEntity()).getShooter();
            if(shooter instanceof Player) { // For players check trust
                event.setCancelled(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)shooter,
                        TrustLevel.BUILD, flags));
            }else if(shooter instanceof BlockProjectileSource) { // Check for region crosses if fired by a dispenser
                event.setCancelled(dm.crossesRegions(((BlockProjectileSource)shooter).getBlock().getLocation(),
                            event.getBlock().getLocation()));
            }
            return;
        }

        event.setCancelled(!flags.isAllowed(RegionFlag.MOB_GRIEF));
    }

    /**
     * Handle frost walker and snow golem trails.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onEntityFormBlock(EntityBlockFormEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;

        if((event.getEntity().getType() == EntityType.SNOWMAN && !flags.isAllowed(RegionFlag.MOB_GRIEF)))
            event.setCancelled(true);
        else if(event.getEntity().getType() == EntityType.PLAYER) {
            if(!flags.isAllowed(RegionFlag.ICE_CHANGE))
                event.setCancelled(true);
            else if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)event.getEntity(),
                    TrustLevel.BUILD, flags)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent entity explosions (including the explosion of the primed TNT entity) from damaging claims.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if(EntityType.PRIMED_TNT.equals(event.getEntityType())) {
            // If the explosion occurs in a location where tnt is not allowed, cancel the event altogether
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLocation());
            if(flags != null && !flags.isAllowed(RegionFlag.TNT)) {
                event.setCancelled(true);
                return;
            }

            // Individual block checks are required since the TNT could have been launched
            Entity igniter = ((TNTPrimed)event.getEntity()).getSource();
            if(igniter instanceof Player) {
                // Trust check for player igniters
                event.blockList().removeIf(block -> {
                    FlagContainer flags0 = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                    return flags0 != null && !flags0.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)igniter,
                            TrustLevel.BUILD, flags0);
                });
            }

            // Just to make sure no damage is done on the border of a region
            event.blockList().removeIf(block -> {
                FlagContainer flags0 = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                return flags0 != null && !flags0.isAllowed(RegionFlag.TNT);
            });
        }else{
            // If the mob explosion occurs in an area where mob grief is not allowed, cancel the event altogether
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLocation());
            if(flags != null && !flags.isAllowed(RegionFlag.MOB_GRIEF)) {
                event.setCancelled(true);
                return;
            }

            // Prevent explosions caused by other entities besides TNT
            event.blockList().removeIf(block -> {
                FlagContainer flags0 = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                return flags0 != null && !flags0.isAllowed(RegionFlag.MOB_GRIEF);
            });
        }
    }
    
    /**
     * Prevent splash potions from splashing. (effect from outside a border may slip in)
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onPotionSplash(PotionSplashEvent event) {
        handlePotionSplash(event);
    }

    /**
     * Prevent lingering splash potions from splashing. (effect from outside a border may slip in)
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onPotionSplash(LingeringPotionSplashEvent event) {
        handlePotionSplash(event);
    }

    // Handles a splash potion, or lingering potion splash event
    private <T extends ProjectileHitEvent & Cancellable> void handlePotionSplash(T event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags != null && !flags.isAllowed(RegionFlag.POTION_SPLASH)) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }
    
    /**
     * Prevent pets teleporting to a region
     *
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getTo());
        if(flags == null)
            return;

        event.setCancelled(event.getEntity() instanceof Tameable && ((Tameable) event.getEntity()).isTamed() &&
                !flags.isAllowed(RegionFlag.FOLLOW));
    }
    
    /**
     * Prevent mobs from becoming aggro towards players inside a region
     *
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() == null)
            return;

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getTarget().getLocation());
        event.setCancelled(flags != null &&
                flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_AGGRO).isAllowed(event.getEntity().getType()));
    }
}
