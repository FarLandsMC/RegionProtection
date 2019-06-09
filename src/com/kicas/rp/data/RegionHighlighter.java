package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Pair;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RegionHighlighter {
    private final Player player;
    private final Material lightSource, block;
    private final Map<Location, BlockData> original;
    private final Map<Location, Material> changes;
    private int removalTaskId;

    public RegionHighlighter(Player player, Collection<Region> regions, Material lightSource, Material block) {
        this.player = player;
        this.lightSource = lightSource;
        this.block = block;
        this.original = new HashMap<>();
        this.changes = new HashMap<>();
        initBlocks(regions);
        showBlocks();
    }

    public void remove() {
        if(player.isOnline())
            original.forEach(player::sendBlockChange);
        Bukkit.getScheduler().cancelTask(removalTaskId);
    }

    private void initBlocks(Collection<Region> regions) {
        regions.forEach(region -> {
            addChange(region.getMin(), lightSource);
            addChange(region.getMin().clone().add(1, 0, 0), block);
            addChange(region.getMin().clone().add(0, 0, 1), block);
            addChange(region.getMax(), lightSource);
            addChange(region.getMax().clone().subtract(1, 0, 0), block);
            addChange(region.getMax().clone().subtract(0, 0, 1), block);
            Location vertex = new Location(region.getMin().getWorld(), region.getMin().getX(), 0, region.getMax().getZ());
            addChange(vertex, lightSource);
            addChange(vertex.clone().add(1, 0, 0), block);
            addChange(vertex.clone().subtract(0, 0, 1), block);
            vertex = new Location(region.getMin().getWorld(), region.getMax().getX(), 0, region.getMin().getZ());
            addChange(vertex, lightSource);
            addChange(vertex.clone().subtract(1, 0, 0), block);
            addChange(vertex.clone().add(0, 0, 1), block);
            for(int i = region.getMin().getBlockX() + 10;i < region.getMax().getBlockX() - 5;++ i) {
                addChange(new Location(region.getMin().getWorld(), i, 0, region.getMin().getZ()), block);
                addChange(new Location(region.getMin().getWorld(), i, 0, region.getMax().getZ()), block);
            }
            for(int i = region.getMin().getBlockZ() + 10;i < region.getMax().getBlockZ() - 5;++ i) {
                addChange(new Location(region.getMin().getWorld(), region.getMin().getX(), 0, i), block);
                addChange(new Location(region.getMin().getWorld(), region.getMax().getX(), 0, i), block);
            }
        });
    }

    private void showBlocks() {
        changes.forEach((loc, mat) -> player.sendBlockChange(loc, mat.createBlockData()));
        removalTaskId = Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(), () -> {
            if(player.isOnline())
                original.forEach(player::sendBlockChange);
        }, 20L * 60L).getTaskId();
    }

    private void addChange(Location location, Material replacement) {
        location = findReplacementLocation(location);
        original.put(location, location.getBlock() == null ? Material.AIR.createBlockData() : location.getBlock().getBlockData());
        changes.put(location, replacement);
    }

    private Location findReplacementLocation(Location start) {
        return null;
    }
}
