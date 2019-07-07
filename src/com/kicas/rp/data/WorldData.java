package com.kicas.rp.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorldData extends FlagContainer {
    private UUID worldUid;
    private final List<Region> regions;

    public WorldData(UUID uuid) {
        this.worldUid = uuid;
        this.regions = new ArrayList<>();
    }

    public UUID getWorldUid() {
        return worldUid;
    }

    public List<Region> getRegions() {
        return regions;
    }
}
