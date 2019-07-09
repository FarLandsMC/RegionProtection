package com.kicas.rp.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorldData extends FlagContainer {
    private UUID worldUid;
    private final List<Region> regions;
    private RegionLookupTable lookupTable;

    public WorldData(UUID uuid) {
        this.worldUid = uuid;
        this.regions = new ArrayList<>();
        this.lookupTable = null;
    }

    public UUID getWorldUid() {
        return worldUid;
    }

    public List<Region> getRegions() {
        return regions;
    }

    public void addRegion(Region region) {
        if(!region.hasParent())
            regions.add(region);
        lookupTable.add(region);
    }

    public RegionLookupTable getLookupTable() {
        return lookupTable;
    }

    public void generateLookupTable(int scale) {
        lookupTable = new RegionLookupTable(Math.max(16, regions.size()), scale);
        regions.forEach(region -> {
            lookupTable.add(region);
            region.getChildren().forEach(lookupTable::add);
        });
    }
}
