package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Highlights a region or collection of regions client-side for a certain player.
 */
public class RegionHighlighter {
    private final Player player;
    // Store the original blocks for reversion
    private final Map<Location, Material> changes;
    // Task ID of the delayed task to revert the client-side changes
    private int removalTaskId;
    private boolean complete;

    public RegionHighlighter(Player player, Collection<Region> regions, Material lightSource, Material block,
                             boolean includeChildren) {
        this.player = player;
        this.changes = new HashMap<>();
        this.complete = false;
        initBlocks(regions, lightSource, block, includeChildren);
    }

    public RegionHighlighter(Player player, Collection<Region> regions, boolean includeChildren) {
        this(player, regions, null, null, includeChildren);
    }

    public RegionHighlighter(Player player, Collection<Region> regions) {
        this(player, regions, null, null, false);
    }

    public RegionHighlighter(Player player, Region region, Material lightSource, Material block) {
        this(player, Collections.singleton(region), lightSource, block, false);
    }

    public RegionHighlighter(Player player, Region region, boolean includeChildren) {
        this(player, Collections.singleton(region), null, null, includeChildren);
    }

    public RegionHighlighter(Player player, Region region) {
        this(player, Collections.singleton(region), null, null, false);
    }

    /**
     * Cancels the automatic removal task and removes the client-side changes if the player is online.
     */
    public void remove() {
        if(player.isOnline()) {
            changes.keySet().forEach(loc -> {
                if(player.getLocation().distanceSquared(loc) < 10000) {
                    Block block = loc.getBlock();
                    if(block != null)
                        player.sendBlockChange(loc, block.getBlockData());
                }
            });
        }

        Bukkit.getScheduler().cancelTask(removalTaskId);
    }

    // Initialize the changes and save the original blocks. If the light source or block is null, the default materials
    // will be used.
    private void initBlocks(Collection<Region> regions, Material lightSource, Material block, boolean includeChildren) {
        // Highest priority first
        regions.stream().sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority())).forEach(region -> {
            // Resolve the light source and block if they're not provided
            Material ls = lightSource == null ? (region.hasParent() ? Material.SEA_LANTERN : Material.GLOWSTONE)
                    : lightSource;
            Material bk = block == null ? (region.hasParent() ? Material.IRON_BLOCK : Material.GOLD_BLOCK)
                    : block;

            // Corners
            putChange(region.getMin(), ls);
            putChange(region.getMin().clone().add(1, 0, 0), bk);
            putChange(region.getMin().clone().add(0, 0, 1), bk);

            putChange(region.getMax(), ls);
            putChange(region.getMax().clone().subtract(1, 0, 0), bk);
            putChange(region.getMax().clone().subtract(0, 0, 1), bk);

            Location vertex = new Location(region.getWorld(), region.getMin().getX(), 0,
                    region.getMax().getZ());
            putChange(vertex, ls);
            putChange(vertex.clone().add(1, 0, 0), bk);
            putChange(vertex.clone().subtract(0, 0, 1), bk);

            vertex = new Location(region.getWorld(), region.getMax().getX(), 0, region.getMin().getZ());
            putChange(vertex, ls);
            putChange(vertex.clone().subtract(1, 0, 0), bk);
            putChange(vertex.clone().add(0, 0, 1), bk);

            // Sides
            for(int i = region.getMin().getBlockX() + 10;i < region.getMax().getBlockX() - 5;i += 10) {
                putChange(new Location(region.getWorld(), i, 0, region.getMin().getZ()), bk);
                putChange(new Location(region.getWorld(), i, 0, region.getMax().getZ()), bk);
            }

            for(int i = region.getMin().getBlockZ() + 10;i < region.getMax().getBlockZ() - 5;i += 10) {
                putChange(new Location(region.getWorld(), region.getMin().getX(), 0, i), bk);
                putChange(new Location(region.getWorld(), region.getMax().getX(), 0, i), bk);
            }

            if(includeChildren)
                initBlocks(region.getChildren(), lightSource, block, includeChildren);
        });
    }

    /**
     * Sends the client-side changes to the player this object was initialized with.
     */
    public void showBlocks() {
        changes.forEach((loc, mat) -> {
            if(player.getLocation().distanceSquared(loc) < 10000)
                player.sendBlockChange(loc, mat.createBlockData());
        });

        removalTaskId = Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(), () -> {
            if(player.isOnline()) {
                changes.keySet().forEach(loc -> {
                    if(player.getLocation().distanceSquared(loc) < 10000) {
                        Block block = loc.getBlock();
                        if(block != null)
                            player.sendBlockChange(loc, block.getBlockData());
                    }
                });
            }
            setComplete();
        }, 20L * 60L).getTaskId();
    }

    public boolean isComplete() {
        return complete;
    }

    private void setComplete() {
        complete = true;
    }

    // Adds or overwrites the change for the given location and also stores the original data if it's not already stored
    private void putChange(Location location, Material replacement) {
        if(player.getLocation().distanceSquared(location) > 10000)
            return;

        location = findReplacementLocation(location.clone());
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
            while ((replacement.getBlock().getType().isSolid() || replacement.getBlock().isLiquid())
                    && replacement.getBlockY() < replacement.getWorld().getMaxHeight()) {
                replacement.setY(replacement.getY() + 1);
            }
            replacement.setY(replacement.getY() - 1); // make sure it's in the ground and not above it
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
