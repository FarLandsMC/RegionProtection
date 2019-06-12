package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

public class EntityEventHandler implements Listener {
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        DataManager dm = RegionProtection.getDataManager();
        FlagContainer flags = dm.getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;

        if((EntityType.ARROW.equals(event.getEntityType()) || EntityType.SMALL_FIREBALL.equals(event.getEntityType()))
                && Material.TNT.equals(event.getBlock().getType())) {
            ProjectileSource shooter = ((Arrow)event.getEntity()).getShooter();
            if(shooter instanceof Player) {
                if(!flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)shooter, TrustLevel.BUILD, flags))
                    event.setCancelled(true);
            }else if(shooter instanceof BlockProjectileSource) {
                if(dm.crossesRegions(((BlockProjectileSource)shooter).getBlock().getLocation(), event.getBlock().getLocation()))
                    event.setCancelled(true);
            }
            return;
        }

        if(!flags.isAllowed(RegionFlag.MOB_GRIEF) && EntityType.ENDERMAN.equals(event.getEntityType()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        if(EntityType.PRIMED_TNT.equals(event.getEntityType())) {
            Entity igniter = ((TNTPrimed)event.getEntity()).getSource();
            if(!(igniter instanceof Player)) {
                event.blockList().removeIf(block -> {
                    FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                    return flags != null && !flags.isAllowed(RegionFlag.TNT_EXPLOSIONS);
                });
            }else{
                event.blockList().removeIf(block -> {
                    FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                    return flags != null && !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust((Player)igniter, TrustLevel.BUILD, flags);
                });
            }
        }else{
            event.blockList().removeIf(block -> {
                FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(block.getLocation());
                return flags != null && !flags.isAllowed(RegionFlag.MOB_GRIEF);
            });
        }
    }
}
