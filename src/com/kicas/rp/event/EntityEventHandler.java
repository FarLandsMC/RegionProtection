package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
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
            ProjectileSource shooter = ((Arrow)event.getEntity()).getShooter();
            if(shooter instanceof Player) { // For players check trust
                event.setCancelled(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)shooter, TrustLevel.BUILD, flags));
            }else if(shooter instanceof BlockProjectileSource) { // Check for region crosses if fired by a dispenser
                event.setCancelled(dm.crossesRegions(((BlockProjectileSource)shooter).getBlock().getLocation(),
                            event.getBlock().getLocation()));
            }
            return;
        }

        // Prevent enderman grief
        if (event.getEntityType() == EntityType.ENDERMAN && !flags.isAllowed(RegionFlag.ENDERMAN_BLOCK_DAMAGE))
            event.setCancelled(true);
        else
            event.setCancelled(!flags.isAllowed(RegionFlag.MOB_GRIEF));
    }

    /**
     * Prevent entity explosions (including the explosion of the primed TNT entity) from damaging claims.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if(EntityType.PRIMED_TNT.equals(event.getEntityType())) {
            // Prevent the TNT from exploding at all if tnt explosions are turned off
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLocation());
            if(!flags.isAllowed(RegionFlag.TNT_EXPLOSIONS)) {
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
                return flags0 != null && !flags0.isAllowed(RegionFlag.TNT_EXPLOSIONS);
            });
        }else{
            // Prevent explosions caused by other entities besides TNT
            event.blockList().removeIf(block -> {
                FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                return flags != null && !flags.isAllowed(RegionFlag.MOB_GRIEF);
            });
        }
    }
    
    /**
     * Prevent splash potions from splashing. (effect from outside a border may slip in)
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onPotionSplash(PotionSplashEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags == null)
            return;
        if (!flags.isAllowed(RegionFlag.POTION_SPLASH)) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }
}
