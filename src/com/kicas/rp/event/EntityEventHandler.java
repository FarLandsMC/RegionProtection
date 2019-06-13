package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
    @EventHandler(ignoreCancelled=true, priority= EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        DataManager dm = RegionProtection.getDataManager();
        FlagContainer flags = dm.getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;

        if((event.getEntityType() == EntityType.ARROW || event.getEntityType() == EntityType.SMALL_FIREBALL)) {
            ProjectileSource shooter = ((Arrow)event.getEntity()).getShooter();
            if(shooter instanceof Player) { // For players check trust
                if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)shooter, TrustLevel.BUILD, flags))
                    event.setCancelled(true);
            }else if(shooter instanceof BlockProjectileSource) { // Check for region crosses if fired by a dispenser
                if(dm.crossesRegions(((BlockProjectileSource)shooter).getBlock().getLocation(),
                        event.getBlock().getLocation())) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // Prevent enderman grief
        if(!flags.isAllowed(RegionFlag.MOB_GRIEF) && event.getEntityType() == EntityType.ENDERMAN)
            event.setCancelled(true);
    }

    /**
     * Prevent entity explosions (including the explosion of the primed TNT entity) from damaging claims.
     * @param event the event.
     */
    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if(EntityType.PRIMED_TNT.equals(event.getEntityType())) {
            // Individual block checks are required since the TNT could have been launched

            Entity igniter = ((TNTPrimed)event.getEntity()).getSource();
            if(igniter instanceof Player) {
                // Trust check for player igniters
                event.blockList().removeIf(block -> {
                    FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                    return flags != null && !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)igniter,
                            TrustLevel.BUILD, flags);
                });
            }else{
                // If the igniter is unknown do a generic TNT explosion flag check
                event.blockList().removeIf(block -> {
                    FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                    return flags != null && !flags.isAllowed(RegionFlag.TNT_EXPLOSIONS);
                });
            }
        }else{
            // Prevent explosions caused by other entities besides TNT
            event.blockList().removeIf(block -> {
                FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                return flags != null && !flags.isAllowed(RegionFlag.MOB_GRIEF);
            });
        }
    }
}
