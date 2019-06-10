package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataManager implements Listener {
    private final File rootDir;
    private final Map<UUID, List<Region>> regions;
    private final Map<UUID, Map<Long, List<Region>>> lookupTable;
    private final Map<UUID, PlayerSession> playerSessions;

    public static final byte REGION_FORMAT_VERSION = 0;
    public static final byte PLAYER_DATA_FORMAT_VERSION = 0;

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
        this.regions = new HashMap<>();
        this.lookupTable = new HashMap<>();
        this.playerSessions = new HashMap<>();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if(!playerSessions.containsKey(event.getPlayer().getUniqueId()))
            playerSessions.put(event.getPlayer().getUniqueId(), new PlayerSession(event.getPlayer().getUniqueId()));
    }

    public List<Region> getRegionsInWorld(World world) {
        return regions.get(world.getUID());
    }

    public Region getRegionByName(World world, String name) {
        return regions.get(world.getUID()).stream().filter(region -> name.equals(region.getName())).findAny().orElse(null);
    }

    public List<Region> getRegionsAt(Location location) {
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(((long)(location.getBlockX() >> 7)) << 32 |
                ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL));
        return regions == null ? Collections.emptyList() : regions.stream().filter(region -> region.contains(location)).collect(Collectors.toList());
    }

    public FlagContainer getFlagsAt(Location location) {
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(((long)(location.getBlockX() >> 7)) << 32 |
                ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL));
        if(regions == null)
            return null;
        regions = regions.stream().filter(region -> region.contains(location)).collect(Collectors.toList());
        if(regions.size() == 1)
            return regions.get(0);
        FlagContainer flags = new FlagContainer();
        regions.stream().sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority())).forEach(region -> {
            region.getFlags().forEach((flag, meta) -> {
                if(!flags.hasFlag(flag))
                    flags.setFlag(flag, meta);
            });
        });
        return flags.isEmpty() ? null : flags;
    }

    public Region tryCreateAdminRegion(String name, Player creator, int priority, Location min, Location max) {
        if(getRegionByName(creator.getWorld(), name) != null) {
            creator.sendMessage(ChatColor.RED + "A region with this name already exists.");
            return null;
        }
        Region region = new Region(name, priority, new UUID(0, 0), min, max);
        addRegionToLookupTable(region);
        return region;
    }

    public Region tryCreateClaim(Player creator, Location pos1, Location pos2) {
        Location min = new Location(pos1.getWorld(), Math.min(pos1.getX(), pos2.getX()), 63, Math.min(pos1.getZ(), pos2.getZ()));
        Location max = new Location(pos1.getWorld(), Math.max(pos1.getX(), pos2.getX()), pos1.getWorld().getMaxHeight(),
                Math.max(pos1.getZ(), pos2.getZ()));
        Region region = new Region(null, 0, creator.getUniqueId(), min, max);
        List<Region> collisions = new LinkedList<>();
        for(int x = min.getBlockX() >> 7;x <= max.getBlockX() >> 7;++ x) {
            for(int z = min.getBlockZ() >> 7;z <= max.getBlockZ() >> 7;++ z) {
                List<Region> regions = lookupTable.get(pos1.getWorld().getUID()).get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL));
                if(regions != null)
                    regions.stream().filter(r -> !r.isAllowed(RegionFlag.OVERLAP) && r.intersects(region)).forEach(collisions::add);
            }
        }
        if(!collisions.isEmpty()) {
            creator.sendMessage(ChatColor.RED + "You cannot create a claim here since it overlaps other claims.");
            playerSessions.get(creator.getUniqueId()).setRegionHighlighter(new RegionHighlighter(creator, collisions, Material.GLOWSTONE, Material.NETHERRACK));
            return null;
        }
        PlayerSession ps = playerSessions.get(creator.getUniqueId());
        long area = (max.getBlockX() - min.getBlockX()) * (max.getBlockZ() - min.getBlockZ());
        if(area > ps.getClaimBlocks()) {
            creator.sendMessage(ChatColor.RED + "You need " + (area - ps.getClaimBlocks()) + " more claim blocks to create this claim.");
            return null;
        }
        if(area < RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) {
            creator.sendMessage(ChatColor.RED + "This claim is too small! Your claim must have an area of at least " +
                    RegionProtection.getRPConfig().getInt("general.minimum-claim-size") + " blocks.");
            return null;
        }
        ps.subtractClaimBlocks((int)area);
        regions.get(creator.getWorld().getUID()).add(region);
        addRegionToLookupTable(region);
        return region;
    }

    public PlayerSession getPlayerSession(Player player) {
        return playerSessions.get(player.getUniqueId());
    }

    public void load() {
        Bukkit.getWorlds().stream().map(World::getUID).forEach(uuid -> {
            regions.put(uuid, new ArrayList<>());
            lookupTable.put(uuid, new HashMap<>());
        });

        try {
            File regionsFile = new File(rootDir.getAbsolutePath() + File.separator + "regions.dat");
            if(!regionsFile.exists())
                regionsFile.createNewFile();
            else{
                Decoder decoder = new Decoder(new FileInputStream(regionsFile));
                int format = decoder.read();
                if(format != REGION_FORMAT_VERSION)
                    throw new RuntimeException("Could not load regions file since it uses format version " + format + " and is not up to date.");
                int worldCount = Bukkit.getWorlds().size();
                while(worldCount > 0) {
                    World world = Bukkit.getWorld(decoder.readUuid());
                    int regionCount = decoder.readInt();
                    while(regionCount > 0) {
                        Region region = new Region(world);
                        region.deserialize(decoder);
                        regions.get(world.getUID()).add(region);
                        addRegionToLookupTable(region);
                        -- regionCount;
                    }
                    -- worldCount;
                }
            }
        }catch(IOException ex) {
            RegionProtection.error("Failed to load regions file: " + ex.getMessage());
            ex.printStackTrace();
        }

        try {
            File playerDataFile = new File(rootDir.getAbsolutePath() + File.separator + "playerdata.dat");
            if(!playerDataFile.exists())
                playerDataFile.createNewFile();
            else{
                Decoder decoder = new Decoder(new FileInputStream(playerDataFile));
                int format = decoder.read();
                if(format != PLAYER_DATA_FORMAT_VERSION)
                    throw new RuntimeException("Could not load player data file since it uses format version " + format + " and is not up to date.");
                int len = decoder.readInt();
                while(len > 0) {
                    PlayerSession pd = new PlayerSession();
                    pd.deserialize(decoder);
                    playerSessions.put(pd.getUuid(), pd);
                    -- len;
                }
            }
        }catch(IOException ex) {
            RegionProtection.error("Failed to load regions file: " + ex.getMessage());
            ex.printStackTrace();
        }

        RegionProtection.log("Finished loading data.");
    }

    public void save() {
        try {
            File regionsFile = new File(rootDir.getAbsolutePath() + File.separator + "regions.dat");
            if(!regionsFile.exists())
                regionsFile.createNewFile();
            Encoder encoder = new Encoder(new FileOutputStream(regionsFile));
            encoder.write(REGION_FORMAT_VERSION);
            for(Map.Entry<UUID, List<Region>> world : regions.entrySet()) {
                encoder.writeUuid(world.getKey());
                encoder.writeInt(world.getValue().size());
                for(Region region : world.getValue())
                    region.serialize(encoder);
            }
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
        Map<Long, List<Region>> worldTable = lookupTable.get(region.getMin().getWorld().getUID());
        Long key;
        for(int x = region.getMin().getBlockX() >> 7;x <= region.getMax().getBlockX() >> 7;++ x) {
            for(int z = region.getMin().getBlockZ() >> 7;z <= region.getMax().getBlockZ() >> 7;++ z) {
                key = ((long)x) << 32 | (((long)z & 0xFFFFFFFFL));
                if(!worldTable.containsKey(key))
                    worldTable.put(key, new ArrayList<>());
                worldTable.get(key).add(region);
            }
        }
    }
}
