package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class DataManager {
    private final File rootDir;
    private final Map<Integer, Region> regions;
    private final Map<Long, List<Region>> lookupTable;
    private final Map<UUID, PlayerData> playerData;
    private int currentRegionId;

    public static final byte REGION_FORMAT_VERSION = 0;
    public static final byte PLAYER_DATA_FORMAT_VERSION = 0;

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
        this.regions = new HashMap<>();
        this.lookupTable = new HashMap<>();
        this.playerData = new HashMap<>();
    }

    public Region getRegionById(int id) {
        return regions.get(id);
    }

    public Region getRegionAt(Location location) {
        List<Region> regions = lookupTable.get((((long)(location.getBlockX() >> 7)) << 4) | ((long)(location.getBlockZ() >> 7)));
        return regions == null ? null : regions.stream().filter(region -> region.contains(location)).findAny().orElse(null);
    }

    public Region createRegion(Player owner, int priority, Location min, Location max) {
        Region region = new Region(currentRegionId++, priority, new ExtendedUuid(owner), min, max);
        addRegionToLookupTable(region);
        return region;
    }

    public Region createAdminRegion(int priority, Location min, Location max) {
        Region region = new Region(currentRegionId++, priority, new ExtendedUuid(ExtendedUuid.ADMIN), min, max);
        addRegionToLookupTable(region);
        return region;
    }

    public void load() {
        try {
            File regionsFile = new File(rootDir.getAbsolutePath() + File.separator + "regions.dat");
            if(!regionsFile.exists())
                regionsFile.createNewFile();
            else{
                Decoder decoder = new Decoder(new FileInputStream(regionsFile));
                int format = decoder.read();
                if(format != REGION_FORMAT_VERSION)
                    throw new RuntimeException("Could not load regions file since it uses format version " + format + " and is not up to date.");
                currentRegionId = decoder.readInt();
                int regionCount = decoder.readInt();
                for(int i = 0;i < regionCount;++ i) {
                    Region region = new Region();
                    region.deserialize(decoder);
                    regions.put(region.getId(), region);
                }
                RegionProtection.log("Loaded " + regionCount + " region" + (regionCount == 1 ? "." : "s."));
            }
        }catch(IOException ex) {
            RegionProtection.error("Failed to load regions file: " + ex.getMessage());
            ex.printStackTrace();
        }

        // Organize the regions into a lookup table by location
        regions.values().forEach(this::addRegionToLookupTable);

        try {
            File playerDataFile = new File(rootDir.getAbsolutePath() + File.separator + "playerdata.dat");
            if(!playerDataFile.exists())
                playerDataFile.createNewFile();
            else {
                Decoder decoder = new Decoder(new FileInputStream(playerDataFile));
                int format = decoder.read();
                if(format != PLAYER_DATA_FORMAT_VERSION)
                    throw new RuntimeException("Could not load player data file since it uses format version " + format + " and is not up to date.");
                int len = decoder.readInt();
                while(len > 0) {
                    PlayerData pd = new PlayerData();
                    pd.deserialize(decoder);
                    playerData.put(pd.getUuid(), pd);
                }
            }
        }catch(IOException ex) {
            RegionProtection.error("Failed to load regions file: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void save() {
        try {
            File regionsFile = new File(rootDir.getAbsolutePath() + File.separator + "regions.dat");
            if(!regionsFile.exists())
                regionsFile.createNewFile();
            Encoder encoder = new Encoder(new FileOutputStream(regionsFile));
            encoder.write(REGION_FORMAT_VERSION);
            encoder.writeInt(currentRegionId);
            encoder.writeInt(regions.size());
            for(Region region : regions.values())
                region.serialize(encoder);
        }catch(IOException ex) {
            RegionProtection.error("Failed to save regions file: " + ex.getMessage());
            ex.printStackTrace();
        }

        try {
            File playerDataFile = new File(rootDir.getAbsolutePath() + File.separator + "playerdata.dat");
            if(!playerDataFile.exists())
                playerDataFile.createNewFile();
            Encoder encoder = new Encoder(new FileOutputStream(playerDataFile));
            encoder.write(PLAYER_DATA_FORMAT_VERSION);
            encoder.writeInt(playerData.size());
            for(PlayerData pd : playerData.values())
                pd.serialize(encoder);
        }catch(IOException ex) {
            RegionProtection.error("Failed to save player data file: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void addRegionToLookupTable(Region region) {
        Long key;
        for(int x = region.getMin().getBlockX() >> 7;x <= region.getMax().getBlockX() >> 7;++ x) {
            for(int z = region.getMin().getBlockZ() >> 7;z <= region.getMax().getBlockZ() >> 7;++ z) {
                key = (((long)x) << 4) | ((long)z);
                if(!lookupTable.containsKey(key))
                    lookupTable.put(key, new ArrayList<>());
                lookupTable.get(key).add(region);
            }
        }
    }
}
