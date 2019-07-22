package com.kicas.rp.data;

import com.kicas.rp.util.Pair;
import org.bukkit.Location;

import java.util.*;

/**
 * Allows for regions to be quickly found a various locations through a hash-table-style algorithm.
 */
public class RegionLookupTable {
    private Node[] table;
    private int size; // Number of added regions
    private final int scale; // How much to scale down coordinate positions (actual factor: 2^scale)

    public static final double LOAD_FACTOR_THRESHOLD = 0.75;
    public static final double INFLATION_CONSTANT = 1.35;

    public RegionLookupTable(int initialCapacity, int scale) {
        // An initial capacity of at least 3 is required so that upon inflation the table size actually increases
        this.table = new Node[Math.max(initialCapacity, 3)];
        this.size = 0;
        this.scale = scale;
    }

    /**
     * Adds the given region to the lookup table.
     * @param region the region to add.
     */
    public void add(Region region) {
        addSilent(region);
        // Check the load factor threshold
        if((++ size) > table.length * LOAD_FACTOR_THRESHOLD)
            inflate();
    }

    // Add the region without inflating
    private void addSilent(Region region) {
        // Traverse the scaled down version of the region
        for (int x = region.getMin().getBlockX() >> scale; x <= region.getMax().getBlockX() >> scale; ++ x) {
            for (int z = region.getMin().getBlockZ() >> scale; z <= region.getMax().getBlockZ() >> scale; ++ z) {
                // Add the region
                int index = hash(x, z);
                if(table[index] == null)
                    table[index] = new Node(region);
                else
                    table[index].add(region);
            }
        }
    }

    /**
     * Removes any reference to the region at the given old bounds of the region, and re-adds the region with its current
     * bounds.
     * @param region the region to re-add.
     * @param oldBounds the old bounds of the region.
     */
    public void reAdd(Region region, Pair<Location, Location> oldBounds) {
        remove(region, oldBounds);
        add(region);
    }

    /**
     * Removes the given region from the lookup table.
     * @param region the region to remove.
     */
    public void remove(Region region) {
        remove(region, region.getBounds());

        -- size;
    }

    /**
     * Builds and returns a list of regions that are present at the given location.
     * @param loc the location.
     * @return a list of regions that are present at the given location.
     */
    public List<Region> getRegionsAt(Location loc) {
        // Get the root node containing candidate regions
        Node node = table[hash(loc)];
        if(node == null)
            return Collections.emptyList();

        // Build the list
        List<Region> regions = new ArrayList<>();
        do {
            if(node.region.contains(loc))
                regions.add(node.region);
        }while((node = node.link) != null);

        return regions;
    }

    /**
     * Builds and returns a set of the regions colliding with the given region in 3D space not including the given
     * region.
     * @param region the region.
     * @return a set of the regions colliding with the given region.
     */
    public Set<Region> getCollisions(Region region) {
        Set<Region> regions = new HashSet<>();

        // Traverse the scaled down region
        for (int x = region.getMin().getBlockX() >> scale; x <= region.getMax().getBlockX() >> scale; ++ x) {
            for (int z = region.getMin().getBlockZ() >> scale; z <= region.getMax().getBlockZ() >> scale; ++ z) {
                // Get the node at each part of the region
                int index = hash(x, z);
                Node node = table[index];

                // Add the overlapping regions
                if(node != null) {
                    do {
                        if(node.region.overlaps(region) && !node.region.equals(region))
                            regions.add(node.region);
                    }while((node = node.link) != null);
                }
            }
        }

        return regions;
    }

    /**
     * Builds and returns a list of the regions present at the given location that do not have a parent.
     * @param loc the location.
     * @return a list of the regions present at the given location that do not have a parent.
     */
    public List<Region> getParentRegionsAt(Location loc) {
        // Get the root node containing candidate regions
        Node node = table[hash(loc)];
        if(node == null)
            return Collections.emptyList();

        // Build the list
        List<Region> regions = new ArrayList<>();
        do {
            if(node.region.contains(loc) && !node.region.hasParent())
                regions.add(node.region);
        }while((node = node.link) != null);

        return regions;
    }

    /**
     * Builds and returns a list of regions that contain the given location's x and z values.
     * @param loc the location.
     * @return a list of regions that contain the given location's x and z values.
     */
    public List<Region> getRegionsAtIgnoreY(Location loc) {
        // Get the root node containing candidate regions
        Node node = table[hash(loc)];
        if(node == null)
            return Collections.emptyList();

        // Build the list
        List<Region> regions = new ArrayList<>();
        do {
            if(node.region.containsIgnoreY(loc))
                regions.add(node.region);
        }while((node = node.link) != null);

        return regions;
    }

    /**
     * Returns the region with the highest priority at the given location. If two regions have the same priority, then
     * the region with a parent will be considered as having a higher priority. If there are no regions at the given
     * location, then null is returned.
     * @param loc the location.
     * @return the region with the highest priority at the given location, or null if there are no regions at the given
     * location.
     */
    public Region getHighestPriorityRegionAt(Location loc) {
        // Get the root node containing candidate regions
        Node node = table[hash(loc)];
        if(node == null)
            return null;

        // Find the highest priority region
        Region region = null;
        do {
            if(node.region.contains(loc)) {
                if(region == null)
                    region = node.region;
                else {
                    if (node.region.getPriority() > region.getPriority())
                        region = node.region;
                    else if (node.region.getPriority() == region.getPriority() && node.region.hasParent() &&
                            !region.hasParent()) {
                        region = node.region;
                    }
                }
            }
        }while((node = node.link) != null);

        return region;
    }

    /**
     * Returns the region with the highest priority at the given location's x and z values. If two regions have the same
     * priority, then the region with a parent will be considered as having a higher priority.If there are no regions at
     * the given location, then null is returned.
     * @param loc the location.
     * @return the region with the highest priority at the given location's x and z values, or null if there are no
     * regions at the given location.
     */
    public Region getHighestPriorityRegionAtIgnoreY(Location loc) {
        // Get the root node containing candidate regions
        Node node = table[hash(loc)];
        if(node == null)
            return null;

        // Find the highest priority region
        Region region = null;
        do {
            if(node.region.containsIgnoreY(loc)) {
                if(region == null)
                    region = node.region;
                else {
                    if (node.region.getPriority() > region.getPriority())
                        region = node.region;
                    else if (node.region.getPriority() == region.getPriority() && node.region.hasParent() &&
                            !region.hasParent()) {
                        region = node.region;
                    }
                }
            }
        }while((node = node.link) != null);

        return region;
    }

    // Removes the given region reference from the entries associated with the given bounds
    private void remove(Region region, Pair<Location, Location> bounds) {
        // Traverse the bounds
        for (int x = bounds.getFirst().getBlockX() >> scale; x <= bounds.getSecond().getBlockX() >> scale; ++x) {
            for (int z = bounds.getFirst().getBlockZ() >> scale; z <= bounds.getSecond().getBlockZ() >> scale; ++z) {
                // Remove the reference
                int index = hash(x, z);
                if(table[index] != null) {
                    if (table[index].region.equals(region))
                        table[index] = table[index].link;
                    else
                        table[index].remove(region);
                }
            }
        }
    }

    // Increases the size of the inner table by a factor of 1.35
    private void inflate() {
        // Copy the regions to re-add
        Set<Region> regions = new HashSet<>(size);
        for(Node node : table) {
            if(node != null) {
                do {
                    regions.add(node.region);
                }while((node = node.link) != null);
            }
        }

        // Create a new, empty table then add all the regions copied above
        table = new Node[(int)(INFLATION_CONSTANT * table.length)];
        for(Region region : regions)
            addSilent(region);
    }

    // Calculates the table index for the given location
    private int hash(Location loc) {
        return hash(loc.getBlockX() >> scale, loc.getBlockZ() >> scale);
    }

    // Converts the given x and z parts to a valid index for the inner table array
    private int hash(int x, int z) {
        // The "x & Long.MAX_VALUE" sets the sign bit to 0
        return (int)((((long)x << 32 | (long)z & 0xFF_FF_FF_FFL) & Long.MAX_VALUE) % table.length);
    }

    // Represents a node in the inner table, which will contain a region reference and could contain a reference to
    // another node. Every node is part of a linked chain which collectively contains a set of regions such that there
    // are no duplications of the same region.
    private static class Node {
        final Region region;
        Node link;

        Node(Region region) {
            this.region = region;
            this.link = null;
        }

        // Used to avoid duplicating region references
        void add(Region region) {
            if(!this.region.equals(region)) {
                if(link == null)
                    link = new Node(region);
                else
                    link.add(region);
            }
        }

        void remove(Region region) {
            if(link != null) {
                if(link.region.equals(region))
                    link = link.link;
                else
                    link.remove(region);
            }
        }
    }
}
