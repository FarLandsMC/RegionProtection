package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlayerEventHandler implements Listener {
    @EventHandler
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

    @EventHandler
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
}
