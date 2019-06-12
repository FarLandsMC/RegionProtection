package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Pair;
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

public class DataManager implements Listener {
    private final File rootDir;
    private final Map<UUID, WorldData> worlds;
    private final Map<UUID, Map<Long, List<Region>>> lookupTable;
    private final Map<UUID, PlayerSession> playerSessions;

    public static final byte REGION_FORMAT_VERSION = 0;
    public static final byte PLAYER_DATA_FORMAT_VERSION = 0;

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
        this.worlds = new HashMap<>();
        this.lookupTable = new HashMap<>();
        this.playerSessions = new HashMap<>();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if(!playerSessions.containsKey(event.getPlayer().getUniqueId()))
            playerSessions.put(event.getPlayer().getUniqueId(), new PlayerSession(event.getPlayer().getUniqueId()));
    }

    public void associate(Region parent, Region associated) {
        worlds.get(associated.getMin().getWorld().getUID()).regions.remove(associated);
        if(associated.getPriority() < parent.getPriority())
            associated.setPriority(parent.getPriority());
        associated.setOwner(parent.getOwner());
        associated.setAssociation(parent);
        parent.getAssociatedRegions().add(associated);
    }

    public List<Region> getRegionsInWorld(World world) {
        return worlds.get(world.getUID()).regions;
    }

    public Region getRegionByName(World world, String name) {
        return worlds.get(world.getUID()).regions.stream().filter(region -> name.equals(region.getName())).findAny().orElse(null);
    }

    public List<Region> getRegionsAt(Location location) {
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(((long)(location.getBlockX() >> 7)) << 32 |
                ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL));
        return regions == null ? Collections.emptyList() : regions.stream().filter(region -> region.contains(location)).collect(Collectors.toList());
    }

    public boolean crossesRegions(Location from, Location to) {
        List<Region> fromRegions = getRegionsAt(from), toRegions = getRegionsAt(to);
        return !toRegions.isEmpty() && !fromRegions.equals(toRegions);
    }

    public Region getHighestPriorityRegionAt(Location location) {
        List<Region> regions = getRegionsAt(location);
        return regions.isEmpty() ? null : regions.stream().max(Comparator.comparingInt(Region::getPriority)).orElse(null);
    }

    public List<Region> getUnassociatedRegionsAt(Location location) {
        List<Region> regions = getRegionsAt(location);
        return regions.stream().filter(region -> !region.hasAssociation()).collect(Collectors.toList());
    }

    public FlagContainer getFlagsAt(Location location) {
        FlagContainer worldFlags = worlds.get(location.getWorld().getUID());
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(((long)(location.getBlockX() >> 7)) << 32 |
                ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL));
        if(regions == null)
            return worldFlags.isEmpty() ? null : worldFlags;
        regions = regions.stream().filter(region -> region.contains(location)).collect(Collectors.toList());
        if(regions.isEmpty())
            return worldFlags.isEmpty() ? null : worldFlags;
        if(regions.size() == 1 && worldFlags.isEmpty())
            return regions.get(0);
        FlagContainer flags = new FlagContainer(worldFlags);
        regions.stream().sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority())).forEach(region -> {
            region.getFlags().forEach((flag, meta) -> {
                if(!flags.hasFlag(flag))
                    flags.setFlag(flag, meta);
            });
            if(flags.getOwner() == null)
                flags.setOwner(region.getOwner());
        });
        return flags;
    }

    public Region tryCreateClaim(Player creator, Location pos1, Location pos2) {
        Location min = new Location(pos1.getWorld(), Math.min(pos1.getX(), pos2.getX()), 63, Math.min(pos1.getZ(), pos2.getZ()));
        Location max = new Location(pos1.getWorld(), Math.max(pos1.getX(), pos2.getX()), pos1.getWorld().getMaxHeight(),
                Math.max(pos1.getZ(), pos2.getZ()));
        Region region = new Region(null, 0, creator.getUniqueId(), min, max, null);
        if(!checkCollisions(creator, region))
            return null;
        PlayerSession ps = playerSessions.get(creator.getUniqueId());
        long area = region.area();
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
        worlds.get(creator.getWorld().getUID()).regions.add(region);
        addRegionToLookupTable(region);
        return region;
    }

    public Region tryResizeClaim(Player player, Region claim, Location from, Location to) {
        long oldArea = claim.area();
        Pair<Location, Location> bounds = claim.getBounds();
        claim.moveVertex(from, to);
        if(!checkCollisions(player, claim)) {
            claim.setBounds(bounds);
            return null;
        }
        PlayerSession ps = playerSessions.get(player.getUniqueId());
        long dArea = claim.area() - oldArea;
        if(dArea > ps.getClaimBlocks()) {
            claim.setBounds(bounds);
            player.sendMessage(ChatColor.RED + "You need " + (dArea - ps.getClaimBlocks()) + " more claim blocks to resize this claim.");
            return null;
        }
        if(claim.area() < RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) {
            claim.setBounds(bounds);
            player.sendMessage(ChatColor.RED + "This claim is too small! Your claim must have an area of at least " +
                    RegionProtection.getRPConfig().getInt("general.minimum-claim-size") + " blocks.");
            return null;
        }
        ps.subtractClaimBlocks((int)dArea);
        // Remove the old claim from the lookup table
        for(int x = bounds.getFirst().getBlockX() >> 7;x <= bounds.getSecond().getBlockX() >> 7;++ x) {
            for(int z = bounds.getFirst().getBlockZ() >> 7;z <= bounds.getSecond().getBlockZ() >> 7;++ z) {
                List<Region> regions = lookupTable.get(bounds.getFirst().getWorld().getUID()).get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL));
                if(regions != null)
                    regions.remove(claim);
            }
        }
        addRegionToLookupTable(claim);
        return claim;
    }

    public Region tryCreateSubdivision(Player creator, Region claim, Location pos1, Location pos2) {
        Location min = new Location(pos1.getWorld(), Math.min(pos1.getX(), pos2.getX()), 63, Math.min(pos1.getZ(), pos2.getZ()));
        Location max = new Location(pos1.getWorld(), Math.max(pos1.getX(), pos2.getX()), pos1.getWorld().getMaxHeight(),
                Math.max(pos1.getZ(), pos2.getZ()));
        Region region = new Region(null, claim.getPriority() + 1, creator.getUniqueId(), min, max, claim);
        if(!claim.contains(region)) {
            creator.sendMessage(ChatColor.RED + "A claim subdivision must be completely withing the surrounding claim.");
            return null;
        }
        List<Region> collisions = new LinkedList<>();
        claim.getAssociatedRegions().stream().filter(r -> r.intersects(region)).forEach(collisions::add);
        if(!collisions.isEmpty()) {
            creator.sendMessage(ChatColor.RED + "This subdivision overlaps with other subdivisions.");
            playerSessions.get(creator.getUniqueId()).setRegionHighlighter(new RegionHighlighter(creator, collisions,
                    Material.GLOWSTONE, Material.NETHERRACK, false));
            return null;
        }
        claim.getAssociatedRegions().add(region);
        addRegionToLookupTable(region);
        return region;
    }

    private boolean checkCollisions(Player owner, Region region) {
        List<Region> collisions = new LinkedList<>();
        for(int x = region.getMin().getBlockX() >> 7;x <= region.getMax().getBlockX() >> 7;++ x) {
            for(int z = region.getMin().getBlockZ() >> 7;z <= region.getMax().getBlockZ() >> 7;++ z) {
                List<Region> regions = lookupTable.get(region.getMin().getWorld().getUID()).get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL));
                if(regions != null)
                    regions.stream().filter(r -> !r.isAllowed(RegionFlag.OVERLAP) && r.intersects(region) && !r.equals(region) &&
                            !r.isAssociated(region)).forEach(collisions::add);
            }
        }
        if(!collisions.isEmpty()) {
            owner.sendMessage(ChatColor.RED + "You cannot have a claim here since it overlaps other claims.");
            playerSessions.get(owner.getUniqueId()).setRegionHighlighter(new RegionHighlighter(owner, collisions, Material.GLOWSTONE, Material.NETHERRACK, false));
            return false;
        }
        return true;
    }

    public PlayerSession getPlayerSession(Player player) {
        return playerSessions.get(player.getUniqueId());
    }

    public void load() {
        Bukkit.getWorlds().stream().map(World::getUID).forEach(uuid -> {
            worlds.put(uuid, new WorldData(uuid));
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
                int worldCount = decoder.read();
                while(worldCount > 0) {
                    WorldData wd = new WorldData(null);
                    wd.deserialize(decoder);
                    wd.regions.forEach(this::addRegionToLookupTable);
                    worlds.put(wd.worldUid, wd);
                    -- worldCount;
                }
            }
        }catch(Throwable ex) {
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
        }catch(Throwable ex) {
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
            encoder.write(worlds.size());
            for(WorldData wd : worlds.values())
                wd.serialize(encoder);
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
        region.getAssociatedRegions().forEach(this::addRegionToLookupTable);
    }

    private static class WorldData extends FlagContainer {
        UUID worldUid;
        final List<Region> regions;

        WorldData(UUID uuid) {
            super((UUID)null);
            this.worldUid = uuid;
            this.regions = new ArrayList<>();
        }

        @Override
        public void serialize(Encoder encoder) throws IOException {
            encoder.writeUuid(worldUid);
            super.serialize(encoder);
            encoder.writeInt(regions.size());
            for(Region region : regions)
                region.serialize(encoder);
        }

        @Override
        public void deserialize(Decoder decoder) throws IOException {
            worldUid = decoder.readUuid();
            super.deserialize(decoder);
            World world = Bukkit.getWorld(worldUid);
            int len = decoder.readInt();
            while(len > 0) {
                Region region = new Region(world);
                region.deserialize(decoder);
                regions.add(region);
                -- len;
            }
        }
    }
}
