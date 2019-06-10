package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RegionHighlighter {
    private final Player player;
    private final Material lightSource, block;
    private final Map<Location, BlockData> original;
    private final Map<Location, Material> changes;
    private int removalTaskId;
    private boolean complete;

    public RegionHighlighter(Player player, Collection<Region> regions, Material lightSource, Material block) {
        this.player = player;
        this.lightSource = lightSource;
        this.block = block;
        this.original = new HashMap<>();
        this.changes = new HashMap<>();
        this.complete = false;
        initBlocks(regions);
    }
    public RegionHighlighter(Player player, Collection<Region> regions) {
        this(player, regions, Material.GLOWSTONE, Material.GOLD_BLOCK);
    }
    public RegionHighlighter(Player player, Region region, Material lightSource, Material block) {
        this(player, Collections.singleton(region), lightSource, block);
    }
    public RegionHighlighter(Player player, Region region) {
        this(player, Collections.singleton(region));
    }

    public void remove() {
        if(player.isOnline()) {
            original.forEach((loc, data) -> {
                if(loc.getChunk().isLoaded())
                    player.sendBlockChange(loc, data);
            });
        }
        Bukkit.getScheduler().cancelTask(removalTaskId);
    }

    public boolean isComplete() {
        return complete;
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
            for(int i = region.getMin().getBlockX() + 10;i < region.getMax().getBlockX() - 5;i += 10) {
                addChange(new Location(region.getMin().getWorld(), i, 0, region.getMin().getZ()), block);
                addChange(new Location(region.getMin().getWorld(), i, 0, region.getMax().getZ()), block);
            }
            for(int i = region.getMin().getBlockZ() + 10;i < region.getMax().getBlockZ() - 5;i += 10) {
                addChange(new Location(region.getMin().getWorld(), region.getMin().getX(), 0, i), block);
                addChange(new Location(region.getMin().getWorld(), region.getMax().getX(), 0, i), block);
            }
        });
    }

    public void showBlocks() {
        changes.forEach((loc, mat) -> {
            if(loc.getChunk().isLoaded())
                player.sendBlockChange(loc, mat.createBlockData());
        });
        removalTaskId = Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(), () -> {
            if(player.isOnline()) {
                original.forEach((loc, data) -> {
                    if(loc.getChunk().isLoaded())
                        player.sendBlockChange(loc, data);
                });
            }
            setComplete();
        }, 20L * 60L).getTaskId();
    }

    private void setComplete() {
        complete = true;
    }

    private void addChange(Location location, Material replacement) {
        location = findReplacementLocation(location.clone());
        original.put(location, location.getBlock().getBlockData());
        changes.put(location, replacement);
    }
    
    /**
     * Make the block you want to display more likely to be visible to the player
     * @param replacement input block location
     * @return A location with modified Y to be on surface closest to the players foot level
     */
    private Location findReplacementLocation(Location replacement) {
        replacement.setY(Math.min(player.getLocation().getBlockY(), player.getWorld().getMaxHeight()));
        // aligning to .5 so no further action is required for barrier particles
        replacement.setX(replacement.getBlockX() + .5);
        replacement.setZ(replacement.getBlockZ() + .5);
        
        // We don't go from the max height down in case the player is inside a cave
        if (replacement.getBlock().getType().isSolid() || replacement.getBlock().isLiquid()) {
            // Replacement is in the ground, so we need to move up
            while (!replacement.getBlock().getType().isSolid() && !replacement.getBlock().isLiquid()
                    && replacement.getBlockY() < replacement.getWorld().getMaxHeight()) {
                replacement.setY(replacement.getY() + 1);
            }
        } else {
            // Replacement is in the air, so we need to move down
            while (!replacement.getBlock().getType().isSolid() && !replacement.getBlock().isLiquid()
                    && replacement.getBlockY() > 0) {
                replacement.setY(replacement.getY() - 1);
            }
        }
        return replacement;
    }
}
