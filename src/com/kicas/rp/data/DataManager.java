package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DataManager {
    private final File rootDir;
    private final List<Region> regions;
    private final Map<Long, List<Region>> lookupTable;
    private final Map<UUID, PlayerSession> playerSessions;

    public static final byte REGION_FORMAT_VERSION = 0;
    public static final byte PLAYER_DATA_FORMAT_VERSION = 0;

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
        this.regions = new ArrayList<>();
        this.lookupTable = new HashMap<>();
        this.playerSessions = new HashMap<>();
    }

    public Region getRegionByName(String name) {
        return regions.stream().filter(region -> name.equals(region.getName())).findAny().orElse(null);
    }

    public List<Region> getRegionsAt(Location location) {
        List<Region> regions = lookupTable.get((((long)(location.getBlockX() >> 7)) << 4) | ((long)(location.getBlockZ() >> 7)));
        return regions == null ? Collections.emptyList() : regions.stream().filter(region -> region.contains(location)).collect(Collectors.toList());
    }

    public FlagContainer getFlagsAt(Location location) {
        FlagContainer flags = new FlagContainer();
        getRegionsAt(location).stream().sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority())).forEach(region -> {
            region.getFlags().forEach((flag, meta) -> {
                if(!flags.hasFlag(flag))
                    flags.setFlag(flag, meta);
            });
        });
        return flags;
    }

    public Region tryCreateAdminRegion(String name, Player creator, int priority, Location min, Location max) {
        if(getRegionByName(name) != null) {
            creator.sendMessage(ChatColor.RED + "A region with this name already exists.");
            return null;
        }
        Region region = new Region(name, priority, new ExtendedUuid(ExtendedUuid.ADMIN), min, max);
        addRegionToLookupTable(region);
        return region;
    }

    public Region tryCreateClaim(Player creator, Location pos1, Location pos2) {
        Location min = new Location(pos1.getWorld(), Math.min(pos1.getX(), pos2.getX()), 63, Math.min(pos1.getZ(), pos2.getZ()));
        Location max = new Location(pos1.getWorld(), Math.max(pos1.getX(), pos2.getX()), pos1.getWorld().getMaxHeight(),
                Math.max(pos1.getZ(), pos2.getZ()));
        Region region = new Region(null, 0, new ExtendedUuid(creator), min, max);
        List<Region> collisions = new LinkedList<>();
        for(int x = min.getBlockX() >> 7;x <= max.getBlockX() >> 7;++ x) {
            for(int z = min.getBlockZ() >> 7;z <= max.getBlockZ() >> 7;++ z) {
                List<Region> regions = lookupTable.get((((long)x) << 4) | ((long)z));
                if(regions != null)
                    regions.stream().filter(r -> !r.isAllowed(RegionFlag.OVERLAP)).forEach(collisions::add);
            }
        }
        if(!collisions.isEmpty()) {
            creator.sendMessage(ChatColor.RED + "You cannot create a claim here since it overlaps other claims.");
            // Highlight claims
            return null;
        }
        PlayerSession pd = playerSessions.get(creator.getUniqueId());
        long area = (max.getBlockX() - min.getBlockX()) * (max.getBlockZ() - min.getBlockZ());
        if(area > pd.getClaimBlocks()) {
            creator.sendMessage(ChatColor.RED + "You need " + (area - pd.getClaimBlocks()) + " more claim blocks to create this claim.");
            return null;
        }
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
                int regionCount = decoder.readInt();
                for(int i = 0;i < regionCount;++ i) {
                    Region region = new Region();
                    region.deserialize(decoder);
                    regions.add(region);
                }
                RegionProtection.log("Loaded " + regionCount + " region" + (regionCount == 1 ? "." : "s."));
            }
        }catch(IOException ex) {
            RegionProtection.error("Failed to load regions file: " + ex.getMessage());
            ex.printStackTrace();
        }

        // Organize the regions into a lookup table by location
        regions.forEach(this::addRegionToLookupTable);

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
                    PlayerSession pd = new PlayerSession();
                    pd.deserialize(decoder);
                    playerSessions.put(pd.getUuid(), pd);
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
            encoder.writeInt(regions.size());
            for(Region region : regions)
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
            encoder.writeInt(playerSessions.size());
            for(PlayerSession pd : playerSessions.values())
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
