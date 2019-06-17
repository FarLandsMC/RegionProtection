package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.EnumFilter;
import com.kicas.rp.data.FlagContainer;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.util.Entities;
import com.kicas.rp.util.Materials;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Handles events generally unrelated to entities that take place in the world.
 */
public class WorldEventHandler implements Listener {
    /**
     * Handles fluid flow.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onBlockMove(BlockFromToEvent event) {
        if(RegionProtection.getDataManager().crossesRegions(event.getBlock().getLocation(),
                event.getToBlock().getLocation()))
            event.setCancelled(true);
        else {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());

            if (Material.WATER == event.getBlock().getType()) {
                event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.WATER_FLOW));
            } else {
                event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.LAVA_FLOW));
            }
        }
    }

    /**
     * Handles piston extensions crossing region boundaries.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.setCancelled(event.getBlocks().stream().anyMatch(block -> RegionProtection.getDataManager()
                .crossesRegions(event.getBlock().getLocation(),
                        block.getRelative(event.getDirection()).getLocation())));
    }

    /**
     * Handles piston retractions crossing region boundaries.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.setCancelled(event.getBlocks().stream().anyMatch(block -> RegionProtection.getDataManager()
                .crossesRegions(event.getBlock().getLocation(), block.getLocation())));
    }
    
    /**
     * Handles creature spawn flag restrictions.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        event.setCancelled(flags != null && !Entities.isArtificialSpawn(event.getSpawnReason()) &&
                !flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_SPAWN).isAllowed(event.getEntity().getType()));
    }
    
    /**
     * Handles ice and snow melting or forming.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onBlockFade(BlockFadeEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if(flags == null)
            return;

        if (!flags.isAllowed(RegionFlag.ICE_CHANGE) &&
                (event.getBlock().getType() == Material.ICE || event.getBlock().getType() == Material.FROSTED_ICE))
            event.setCancelled(true);
        else if (!flags.isAllowed(RegionFlag.SNOW_CHANGE) && event.getBlock().getType() == Material.SNOW)
            event.setCancelled(true);
        // 2DO: prevent fire decay
        else if (!flags.isAllowed(RegionFlag.CORAL_DEATH) && Materials.isCoral(event.getBlock().getType()))
            event.setCancelled(true);
    }
    
    /**
     * Handles leaf decay.
     * @param event the event.
     */
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onLeafDecay(LeavesDecayEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.LEAF_DECAY));
    }
}
