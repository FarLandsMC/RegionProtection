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

/**
 * Manages the storing, querying, serialization, and deserialization of all plugin data except the config.
 */
public class DataManager implements Listener {
    // Plugin data directory
    private final File rootDir;
    // Key: worldUid, value: data about the regions and flags in a world
    private final Map<UUID, WorldData> worlds;
    // Used to quickly find the regions in a certain area. Key: worldUid, value: that world's lookup table
    // To generate a key for the lookup table from a certain location, set the 4 most significant bytes to be the
    // location's x-value divided by 128, and set the 4 least significant bytes to be the z-value divided by 128.
    private final Map<UUID, Map<Long, List<Region>>> lookupTable;
    // Key: player UUID, value: transient and persistent data about the player, include claim blocks and such
    private final Map<UUID, PlayerSession> playerSessions;

    // These values are used to keep consistency in the serialized data
    public static final byte REGION_FORMAT_VERSION = 0;
    public static final byte PLAYER_DATA_FORMAT_VERSION = 0;

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
        this.worlds = new HashMap<>();
        this.lookupTable = new HashMap<>();
        this.playerSessions = new HashMap<>();
    }

    /**
     * Creates a new player session for the player if one is not already present.
     * @param event the event.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if(!playerSessions.containsKey(event.getPlayer().getUniqueId()))
            playerSessions.put(event.getPlayer().getUniqueId(), new PlayerSession(event.getPlayer().getUniqueId()));
    }

    /**
     * Sets the second region as a child of the first. The priority of the child region will be modified so it is at
     * least the value of the parent region, and the child region will adopt the ownership of the parent region.
     * @param parent the parent region.
     * @param child the child region.
     */
    public void associate(Region parent, Region child) {
        worlds.get(child.getWorld().getUID()).regions.remove(child);
        if(child.getPriority() < parent.getPriority())
            child.setPriority(parent.getPriority());
        if(child.getPriority() == parent.getPriority())
            child.setFlags(parent.getFlags());
        child.setOwner(parent.getOwner());
        child.setParent(parent);
        parent.getChildren().add(child);
    }

    /**
     * Returns all the regions in a given world.
     * @param world the world.
     * @return all the regions in a given world.
     */
    public List<Region> getRegionsInWorld(World world) {
        return worlds.get(world.getUID()).regions;
    }

    /**
     * Gets a region by the given name and returns it.
     * @param world the world that contains the region.
     * @param name the name of the region.
     * @return the region with the given name in the given world, or null if it could not be found.
     */
    public Region getRegionByName(World world, String name) {
        return worlds.get(world.getUID()).regions.stream().filter(region -> name.equals(region.getName()))
                .findAny().orElse(null);
    }

    /**
     * Returns a list of regions that contain the specified location.
     * @param location the location.
     * @return a list of regions that contain the specified location.
     */
    public List<Region> getRegionsAt(Location location) {
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(
                ((long)(location.getBlockX() >> 7)) << 32 |
                ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL)
        );

        return regions == null ? Collections.emptyList() : regions.stream().filter(region -> region.contains(location))
                .collect(Collectors.toList());
    }

    /**
     * Checks to see if there is a potential conflict of region flags by moving from the first specified location to the
     * second.
     * @param from the original location.
     * @param to the destination location.
     * @return true, if there is a conflict of flags, false otherwise.
     */
    public boolean crossesRegions(Location from, Location to) {
        List<Region> fromRegions = getRegionsAt(from), toRegions = getRegionsAt(to);
        return !toRegions.isEmpty() && toRegions.stream().anyMatch(region -> !fromRegions.contains(region));
    }

    /**
     * Returns the highest priority region at the given location.
     * @param location the location.
     * @return the highest priority region at the given location.
     */
    public Region getHighestPriorityRegionAt(Location location) {
        List<Region> regions = getRegionsAt(location);
        return regions.isEmpty() ? null : regions.stream().max(Comparator.comparingInt(Region::getPriority))
                .orElse(null);
    }

    /**
     * Returns a list of unassociated (non-child, and therefore low priority regions) regions at the given location.
     * @param location the location.
     * @return a list of unassociated regions at the given location.
     */
    public List<Region> getUnassociatedRegionsAt(Location location) {
        List<Region> regions = getRegionsAt(location);
        return regions.stream().filter(region -> !region.hasParent()).collect(Collectors.toList());
    }

    /**
     * Gets the flags present at a certain location, accounting for region priorities and global flags.
     * @param location the location.
     * @return the fkags at the specified location, or null if no flags are present.
     */
    public FlagContainer getFlagsAt(Location location) {
        FlagContainer worldFlags = worlds.get(location.getWorld().getUID());
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(
                ((long)(location.getBlockX() >> 7)) << 32 |
                ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL)
        );

        // Quick check for an absence of regions
        if(regions == null)
            return worldFlags.isEmpty() ? null : worldFlags;

        // Filter out the regions that are near the location but don't contain it
        regions = regions.stream().filter(region -> region.contains(location)).collect(Collectors.toList());
        if(regions.isEmpty())
            return worldFlags.isEmpty() ? null : worldFlags;

        // Another quick check to avoid computation
        if(regions.size() == 1 && worldFlags.isEmpty())
            return regions.get(0);

        // Copy the region flags (highest priority first), and take the ownership of the highest priority region
        FlagContainer flags = new FlagContainer(null);
        regions.stream().sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority())).forEach(region -> {
            region.getFlags().forEach((flag, meta) -> {
                if(!flags.hasFlag(flag))
                    flags.setFlag(flag, meta);
            });
            if(flags.getOwner() == null)
                flags.setOwner(region.getOwner());
        });

        // Copy the world flags
        worldFlags.getFlags().forEach((flag, meta) -> {
            if(!flags.hasFlag(flag))
                flags.setFlag(flag, meta);
        });

        return flags;
    }

    /**
     * Attempts to create a claim with the provided verticies and the specified player as the owner. The provided
     * verticies do not need to be minimums and maximums respecively, however they will be assumed to be diagonally
     * opposite from one another. The created region will extend from y=63 to the maximum world height. The following
     * checks are done to see if the claim can be created:
     * <ul>
     *     <li>Collisions with regions that do not allow overlap (excluding child regions)</li>
     *     <li>The player not having enough claim blocks</li>
     *     <li>The claim is smaller than the minimum claim size</li>
     * </ul>
     * If any of these checks fail, then the player will be notified with a message and null will be returned. Upon
     * successful creation of the claim, the player will have the area of the claim subtracted from their claim blocks
     * and that region will be returned.
     * @param creator the player creating the claim.
     * @param vertex1 the first claim vertex.
     * @param vertex2 the second claim vertex.
     * @return the claim, or null if the claim could not be created.
     */
    public Region tryCreateClaim(Player creator, Location vertex1, Location vertex2) {
        // Convert the given verticies into a minimum and maximum vertex
        Location min = new Location(vertex1.getWorld(), Math.min(vertex1.getX(), vertex2.getX()), 63,
                Math.min(vertex1.getZ(), vertex2.getZ()));
        Location max = new Location(vertex1.getWorld(), Math.max(vertex1.getX(), vertex2.getX()),
                vertex1.getWorld().getMaxHeight(), Math.max(vertex1.getZ(), vertex2.getZ()));

        // Create the region
        Region region = new Region(null, 0, creator.getUniqueId(), min, max, null);

        // checkCollisions sends an error message to the creator
        if(!checkCollisions(creator, region))
            return null;

        // Check claim blocks
        PlayerSession ps = playerSessions.get(creator.getUniqueId());
        long area = region.area();
        if(area > ps.getClaimBlocks()) {
            creator.sendMessage(ChatColor.RED + "You need " + (area - ps.getClaimBlocks()) +
                    " more claim blocks to create this claim.");
            return null;
        }

        // Check to make sure it's at least the minimum area
        if(area < RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) {
            creator.sendMessage(ChatColor.RED + "This claim is too small! Your claim must have an area of at least " +
                    RegionProtection.getRPConfig().getInt("general.minimum-claim-size") + " blocks.");
            return null;
        }

        // Modify claim blocks
        ps.subtractClaimBlocks((int)area);
        // The default is full-trust, so make sure no one has trust
        region.setFlag(RegionFlag.TRUST, TrustMeta.EMPTY_TRUST_META);
        // Register the claim
        worlds.get(creator.getWorld().getUID()).regions.add(region);
        addRegionToLookupTable(region);

        return region;
    }

    /**
     * Attempts to modify the size of a given claim, which can be a sub-claim. This method does not check to ensure that
     * the provided player has permission to modify the size of the claim, this check should be performed outside of
     * this method. The two given locations are the original location and new location of any vertex of the claim. The
     * following checks are done to a claim which has no parent:
     * <ul>
     *     <li>Collisions with regions that do not allow overlap (excluding child regions)</li>
     *     <li>The player not having enough claim blocks</li>
     *     <li>The new claim size is smaller than the minimum claim area</li>
     *     <li>Any children are no longer fully contained within the parent region</li>
     * </ul>
     * The only check performed to a subdivision is to ensure it is still completely contained within the parent claim.
     * If any of these checks fail, then the player will be notified with a message and null will be returned. Upon
     * successful resizing of the claim, the player will gain or lose claim blocks depending on the change in area of
     * the claim and the claim will be returned after its resizing.
     * @param owner the owner of the claim.
     * @param claim the claim to resize.
     * @param originalVertex the original vertex of the claim.
     * @param newVertex the new location of the vertex.
     * @return the resized claim, or null if the claim could not be resized.
     */
    public Region tryResizeClaim(Player owner, Region claim, Location originalVertex, Location newVertex) {
        long oldArea = claim.area();

        // Copy the old values for reversion upon failure
        Pair<Location, Location> bounds = claim.getBounds();

        // Resize the claim
        claim.moveVertex(originalVertex, newVertex);

        // checkCollisions sends the error message to the player
        if(!checkCollisions(owner, claim)) {
            claim.setBounds(bounds);
            return null;
        }

        // Regular claims
        if(!claim.hasParent()) {
            // Check claim blocks
            PlayerSession ps = playerSessions.get(owner.getUniqueId());
            long areaDiff = claim.area() - oldArea;
            if(areaDiff > ps.getClaimBlocks()) {
                claim.setBounds(bounds);
                owner.sendMessage(ChatColor.RED + "You need " + (areaDiff - ps.getClaimBlocks()) +
                        " more claim blocks to resize this claim.");
                return null;
            }

            // Check to make sure it's at least the minimum area
            if(claim.area() < RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) {
                claim.setBounds(bounds);
                owner.sendMessage(ChatColor.RED + "This claim is too small! Your claim must have an area of at least " +
                        RegionProtection.getRPConfig().getInt("general.minimum-claim-size") + " blocks.");
                return null;
            }

            // Check to make sure all subdivisions are still within the parent region
            List<Region> exclaves = new LinkedList<>();
            claim.getChildren().stream().filter(region -> !claim.contains(region)).forEach(exclaves::add);
            if(!exclaves.isEmpty()) {
                claim.setBounds(bounds);
                owner.sendMessage(ChatColor.RED + "You cannot resize your claim here since some subdivisions are not " +
                        "completely within the parent region.");
                playerSessions.get(owner.getUniqueId()).setRegionHighlighter(new RegionHighlighter(owner, exclaves,
                        Material.GLOWSTONE, Material.NETHERRACK, false));
                return null;
            }

            // Modify claim blocks
            ps.subtractClaimBlocks((int)areaDiff);
        }else{ // Subdivisions
            // Check to make sure the subdivision is still completely within the parent claim
            if(!claim.getParent().contains(claim)) {
                claim.setBounds(bounds);
                owner.sendMessage(ChatColor.RED + "You cannot resize this subdivision here since it exits the parent " +
                        "claim.");
                playerSessions.get(owner.getUniqueId()).setRegionHighlighter(new RegionHighlighter(owner,
                        claim.getParent()));
                return null;
            }
        }

        // Remove the old claim from the lookup table
        for(int x = bounds.getFirst().getBlockX() >> 7;x <= bounds.getSecond().getBlockX() >> 7;++ x) {
            for(int z = bounds.getFirst().getBlockZ() >> 7;z <= bounds.getSecond().getBlockZ() >> 7;++ z) {
                lookupTable.get(bounds.getFirst().getWorld().getUID()).get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL))
                        .remove(claim);
            }
        }

        // Re-add the claim to the lookup table
        addRegionToLookupTable(claim);

        return claim;
    }

    /**
     * Attempts to create a subdivision of a given claim with the given verticies. This method does not check to ensure
     * that the provided player has permission to subdivide this claim, this check should be performed outside of this
     * method. The provided verticies do not need to be minimums and maximums respecively, however they will be assumed
     * to be diagonally opposite from one another. The created subdivision will adopt the ownership of the given claim,
     * as well as the minimum y-value of the given claim. The following checks are done to see if the subdivision can be
     * created:
     * <ul>
     *     <li>Incomplete containment of the subdivision within the given claim</li>
     *     <li>Collisions with other subdivisions</li>
     * </ul>
     * If any of these checks fail, then the player will be notified with a message and null will be returned. Upon
     * successful subdividing of the claim, the subdivision will be child with the given region, and will have a
     * higher priority than the given region.
     * @param creator the creator of the subdivision.
     * @param claim the claim to subdivide.
     * @param vertex1 the first vertex of the subdivision.
     * @param vertex2 the second vertex of the subdivision.
     * @return the subdivision, or null if the subdivision could not be created.
     */
    public Region tryCreateSubdivision(Player creator, Region claim, Location vertex1, Location vertex2) {
        // Convert the given verticies into a minimum and maximum vertex
        Location min = new Location(vertex1.getWorld(), Math.min(vertex1.getX(), vertex2.getX()),
                claim.getMin().getBlockY(), Math.min(vertex1.getZ(), vertex2.getZ()));
        Location max = new Location(vertex1.getWorld(), Math.max(vertex1.getX(), vertex2.getX()),
                vertex1.getWorld().getMaxHeight(), Math.max(vertex1.getZ(), vertex2.getZ()));

        // Create the region
        Region region = new Region(null, claim.getPriority() + 1, claim.getOwner(), min, max, claim);

        // Check for complete containment
        if(!claim.contains(region)) {
            creator.sendMessage(ChatColor.RED + "A claim subdivision must be completely withing the parent claim.");
            return null;
        }

        // Check for collisions with other subdivisions
        if(!checkCollisions(creator, region))
            return null;

        // Register the subdivision
        claim.getChildren().add(region);
        addRegionToLookupTable(region);

        return region;
    }

    // Checks for collisions between the given region and other non-child claims that do not permis overlap
    // The given owner will be notified if overlap is detected and those regions will be highlighted
    // Returns true if no collisions were present
    private boolean checkCollisions(Player owner, Region region) {
        List<Region> collisions = new LinkedList<>();
        // Build a list of collisions from the lookup table
        for(int x = region.getMin().getBlockX() >> 7;x <= region.getMax().getBlockX() >> 7;++ x) {
            for(int z = region.getMin().getBlockZ() >> 7;z <= region.getMax().getBlockZ() >> 7;++ z) {
                List<Region> regions = lookupTable.get(region.getWorld().getUID())
                        .get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL));
                // Ignore associations to prevent conflict with subdivisions
                regions.stream().filter(r -> !r.isAllowed(RegionFlag.OVERLAP) && r.overlaps(region) &&
                        !r.equals(region) && !r.isAssociated(region)).forEach(collisions::add);
            }
        }

        if(!collisions.isEmpty()) {
            owner.sendMessage(ChatColor.RED + "You cannot have a claim here since it overlaps other claims.");
            playerSessions.get(owner.getUniqueId()).setRegionHighlighter(new RegionHighlighter(owner, collisions,
                    Material.GLOWSTONE, Material.NETHERRACK, false));
            return false;
        }

        return true;
    }

    /**
     * Returns a player session for the given player.
     * @param player the player.
     * @return a player session for the given player.
     */
    public PlayerSession getPlayerSession(Player player) {
        return playerSessions.get(player.getUniqueId());
    }

    /**
     * Loads all data pertaining to the Region Protection plugin. If certain files are missing, then they will be
     * created and initialized.
     */
    public void load() {
        // Initialize tables
        Bukkit.getWorlds().stream().map(World::getUID).forEach(uuid -> {
            worlds.put(uuid, new WorldData(uuid)); // Just in case a new world is created
            lookupTable.put(uuid, new HashMap<>());
        });

        // Load data for each world
        try {
            File regionsFile = new File(rootDir.getAbsolutePath() + File.separator + "regions.dat");
            if(!regionsFile.exists())
                regionsFile.createNewFile();
            else{
                Decoder decoder = new Decoder(new FileInputStream(regionsFile));
                int format = decoder.read();
                if(format != REGION_FORMAT_VERSION)
                    throw new RuntimeException("Could not load regions file since it uses format version " + format +
                            " and is not up to date.");
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

        // Load player data
        try {
            File playerDataFile = new File(rootDir.getAbsolutePath() + File.separator + "playerdata.dat");
            if(!playerDataFile.exists())
                playerDataFile.createNewFile();
            else{
                Decoder decoder = new Decoder(new FileInputStream(playerDataFile));
                int format = decoder.read();
                if(format != PLAYER_DATA_FORMAT_VERSION)
                    throw new RuntimeException("Could not load player data file since it uses format version " +
                            format + " and is not up to date.");
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

    /**
     * Saves all data managed by this class to disc.
     */
    public void save() {
        // Save world data
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

        // Save player data
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

    // Adds the given region to the lookup table. The region could have references under multiple keys if it is large
    // enough. The specified region will also add its child regions to the table recursively.
    private void addRegionToLookupTable(Region region) {
        Map<Long, List<Region>> worldTable = lookupTable.get(region.getWorld().getUID());
        Long key;
        for(int x = region.getMin().getBlockX() >> 7;x <= region.getMax().getBlockX() >> 7;++ x) {
            for(int z = region.getMin().getBlockZ() >> 7;z <= region.getMax().getBlockZ() >> 7;++ z) {
                key = ((long)x) << 32 | (((long)z & 0xFFFFFFFFL));
                if(!worldTable.containsKey(key))
                    worldTable.put(key, new ArrayList<>());
                worldTable.get(key).add(region);
            }
        }

        region.getChildren().forEach(this::addRegionToLookupTable);
    }

    // Object for storing world data
    private static class WorldData extends FlagContainer {
        UUID worldUid;
        final List<Region> regions;

        WorldData(UUID uuid) {
            super(null);
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
