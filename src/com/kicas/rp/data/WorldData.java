package com.kicas.rp.data;

import org.bukkit.Bukkit;

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

    @Override
    public boolean isAllowed(RegionFlag flag) {
        return flags.containsKey(flag) ? (boolean)flags.get(flag)
                : flag.getWorldDefaultValue(Bukkit.getWorld(worldUid));
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
        lookupTable = new RegionLookupTable((int)(regions.size() * RegionLookupTable.INFLATION_CONSTANT), scale);
        regions.forEach(region -> {
            lookupTable.add(region);
            region.getChildren().forEach(lookupTable::add);
        });
    }
}
