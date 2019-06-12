package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

public class WorldEventHandler implements Listener {
    @EventHandler
    public void onBlockMove(BlockFromToEvent event) {
        if(RegionProtection.getDataManager().crossesRegions(event.getBlock().getLocation(), event.getToBlock().getLocation()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if(event.getBlocks().stream().anyMatch(block -> RegionProtection.getDataManager().crossesRegions(event.getBlock().getLocation(),
                block.getRelative(event.getDirection()).getLocation())))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if(event.getBlocks().stream().anyMatch(block -> RegionProtection.getDataManager().crossesRegions(event.getBlock().getLocation(), block.getLocation())))
            event.setCancelled(true);
    }
}
