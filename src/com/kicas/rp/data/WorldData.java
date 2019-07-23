package com.kicas.rp.data;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Contains the data for a specific world, including the parent regions list, global flags, and transient lookup table.
 */
public class WorldData extends FlagContainer {
    private UUID worldUid;
    private final List<Region> regions;
    private RegionLookupTable lookupTable;

    public WorldData(UUID uuid) {
        this.worldUid = uuid;
        this.regions = new ArrayList<>();
        this.lookupTable = null;
    }

    /**
     * @return the world's UID.
     */
    public UUID getWorldUid() {
        return worldUid;
    }

    /**
     * @return the list of the parent regions within this world.
     */
    public List<Region> getRegions() {
        return regions;
    }

    /**
     * Returns whether or not the given flag's meta is true within the global flag set, using the world default values
     * rather than the region defaults.
     *
     * @param flag the flag.
     * @return true if the given flag is allowed, false otherwise.
     */
    @Override
    public boolean isAllowed(RegionFlag flag) {
        return flags.containsKey(flag) ? (boolean) flags.get(flag)
                : flag.getWorldDefaultValue(Bukkit.getWorld(worldUid));
    }

    /**
     * Adds the given region (parent or child) to the lookup table, and adds the region to the region list if it is a
     * parent region.
     *
     * @param region the region to add.
     */
    public void addRegion(Region region) {
        lookupTable.add(region);
        if (!region.hasParent())
            regions.add(region);
    }

    /**
     * @return this world's region lookup table.
     */
    public RegionLookupTable getLookupTable() {
        return lookupTable;
    }

    /**
     * Generates a new lookup table with the given scale and adds all the regions in the associated world to the lookup
     * table. The scale value should be set depending on the average size of the regions in the world to optimize
     * efficiency, with larger regions having a larger scale, and smaller regions a smaller scale.
     *
     * @param scale the lookup table scale.
     */
    public void generateLookupTable(int scale) {
        lookupTable = new RegionLookupTable((int) (regions.size() * RegionLookupTable.INFLATION_CONSTANT), scale);
        regions.forEach(region -> {
            lookupTable.add(region);
            region.getChildren().forEach(lookupTable::add);
        });
    }
}
