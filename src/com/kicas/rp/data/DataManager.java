package com.kicas.rp.data;

import com.google.gson.*;
import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.event.ClaimCreationEvent;
import com.kicas.rp.event.ClaimResizeEvent;
import com.kicas.rp.util.Pair;
import com.kicas.rp.util.TextUtils;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the storing, querying, serialization, and deserialization of all plugin data except the config.
 */
public class DataManager implements Listener {
    // Plugin data directory
    private final File rootDir;
    // Key: worldUid, value: data about the regions and flags in a world
    private final Map<UUID, WorldData> worlds;
    // Used to create player sessions
    private final Map<UUID, PersistentPlayerData> playerData;
    private final Map<UUID, PlayerSession> playerSessionCache;
    // Cache data received from the Mojang API
    private final Map<String, UUID> ignUuidLookupCache;

    public static int DEFAULT_CLAIM_BOTTOM_Y = 56;

    // These values are used to keep consistency in the serialized data
    public static final byte REGION_FORMAT_VERSION = 4;
    public static final byte PLAYER_DATA_FORMAT_VERSION = 1;
    public static final String GLOBAL_FLAG_NAME = "__global__";

    private static final String MOJANG_API_BASE = "https://api.mojang.com";
    // The larger the number, the more efficient memory usage is but the less efficient lookup is
    private static final int LOOKUP_TABLE_SCALE = 7;
    private static final JsonParser JSON_PARSER = new JsonParser();

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
        this.worlds = new HashMap<>();
        this.playerData = new HashMap<>();
        this.playerSessionCache = new HashMap<>();
        this.ignUuidLookupCache = new HashMap<>();
    }

    /**
     * Fetches the UUID for the given Minecraft username, or returns null if no username could be found. This method
     * will check for an offline player instance with the given username, and will use the UUID attached to that offline
     * player if they have played on this server before. If no such offline player could be found, then this method
     * queries the Mojang API for the UUID associated with the given username.
     *
     * @param username the username.
     * @return the UUID associated with the given username, or null if no such UUID could be found.
     */
    public UUID uuidForUsername(String username) {
        // Check online players
        Player player = Bukkit.getPlayer(username);
        if (player != null)
            return player.getUniqueId();

        // Check to see if they've joined before
        OfflinePlayer op = Stream.of(Bukkit.getOfflinePlayers()).filter(p -> username.equals(p.getName())).findAny()
                .orElse(null);
        if (op != null)
            return op.getUniqueId();

        // Check cache
        if (ignUuidLookupCache.containsKey(username))
            return ignUuidLookupCache.get(username);

        // They have not joined before therefore we'll use the Mojang API
        try {
            // Get the URL and open the stream
            URL url = new URL(MOJANG_API_BASE + "/users/profiles/minecraft/" + username);
            InputStream in = url.openStream();

            // Read the data
            StringBuilder responseBuffer = new StringBuilder();
            int by;
            while ((by = in.read()) > -1)
                responseBuffer.appendCodePoint(by);
            in.close();

            // Invalid username check
            if (responseBuffer.length() == 0)
                return null;

            // Parse the JSON
            JsonElement response = JSON_PARSER.parse(responseBuffer.toString());

            // The provided JSON should be an object
            if (!response.isJsonObject())
                return null;

            // Format the UUID with -'s and parse it
            String unformatted = ((JsonObject) response).get("id").getAsString();
            UUID uuid = UUID.fromString(String.format("%s-%s-%s-%s-%s", unformatted.substring(0, 8),
                    unformatted.substring(8, 12), unformatted.substring(12, 16), unformatted.substring(16, 20),
                    unformatted.substring(20)));

            // Add the lookup to the cache
            ignUuidLookupCache.put(username, uuid);

            return uuid;
        } catch (IOException ex) {
            // Some error occurred, return null
            return null;
        }
    }

    /**
     * Fetches the current username associated with the given UUID. If the given UUID is not associated with an account,
     * then null is returned. If a player with the given UUID has joined this server before, then the name in the
     * offline player class associated with the given UUID is returned. If the a player with the given UUID has not
     * joined the server before, then the Mojang API is used to lookup the current name for the given UUID.
     *
     * @param uuid the uuid.
     * @return the username associated with the given UUID if one could be found, or null if no associated username
     * could be found.
     */
    public String currentUsernameForUuid(UUID uuid) {
        // Check online players
        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            return player.getName();

        // Check to see if they've joined before
        OfflinePlayer op = Stream.of(Bukkit.getOfflinePlayers()).filter(p -> uuid.equals(p.getUniqueId())).findAny()
                .orElse(null);
        if (op != null)
            return op.getName();

        // Check cache
        for (Map.Entry<String, UUID> entry : ignUuidLookupCache.entrySet()) {
            if (entry.getValue().equals(uuid))
                return entry.getKey();
        }

        // They have not joined before therefore we'll use the Mojang API
        try {
            // Get the URL and open the stream
            URL url = new URL(MOJANG_API_BASE + "/user/profiles/" + uuid.toString().replaceAll("-", "") + "/names");
            InputStream in = url.openStream();

            // Read the data
            StringBuilder responseBuffer = new StringBuilder();
            int by;
            while ((by = in.read()) > -1)
                responseBuffer.appendCodePoint(by);
            in.close();

            // Invalid UUID check
            if (responseBuffer.length() == 0)
                return null;

            // Parse the JSON
            JsonElement response = JSON_PARSER.parse(responseBuffer.toString());

            // The provided JSON should be an array
            if (!response.isJsonArray())
                return null;

            // Find the name with the largest change date, IE the current username
            String mostRecentName = null;
            long maxChangedToDate = 0L;
            for (JsonElement nameChange : (JsonArray) response) {
                // Each element should be an object
                if (nameChange.isJsonObject()) {
                    JsonObject nameChangeObject = (JsonObject) nameChange;

                    // The first name doesn't have a date assigned, so we substitute in the value 1 in that case
                    long date = nameChangeObject.has("changedToAt") ? nameChangeObject.get("changedToAt").getAsLong() : 1L;

                    if (date > maxChangedToDate) {
                        mostRecentName = nameChangeObject.get("name").getAsString();
                        maxChangedToDate = date;
                    }
                }
            }

            // This should never happen
            if (mostRecentName == null)
                return null;

            // Add the lookup to the cache
            ignUuidLookupCache.put(mostRecentName, uuid);

            return mostRecentName;
        } catch (IOException ex) {
            // Some error occurred, return null
            return null;
        }
    }

    /**
     * Deletes the cached player session for the given player.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public synchronized void onPlayerQuit(PlayerQuitEvent event) {
        if (playerSessionCache.containsKey(event.getPlayer().getUniqueId())) {
            playerData.get(event.getPlayer().getUniqueId()).setClaimBlocks(playerSessionCache.remove(event.getPlayer()
                    .getUniqueId()).getClaimBlocks());
        }
    }

    /**
     * Returns the world data for the given world or creates and stores a new world data object if one is not already
     * present.
     *
     * @param world the world.
     * @return the world data for the given world.
     */
    public WorldData getWorldData(World world) {
        WorldData worldData = worlds.get(world.getUID());

        if (worldData == null) {
            worldData = new WorldData(world.getUID());
            worldData.generateLookupTable(LOOKUP_TABLE_SCALE);
            worlds.put(world.getUID(), worldData);
        }

        return worldData;
    }

    /**
     * Returns the world data for the given location's world or creates and stores a new world data object if one is not
     * already present. Calling this method is equivalent to calling <code>getWorldData(location.getWorld())</code>
     *
     * @param location the location.
     * @return the world data for the given location's world.
     */
    public WorldData getWorldData(Location location) {
        return getWorldData(location.getWorld());
    }

    /**
     * Sets the second region as a child of the first. The priority of the child region will be modified so it is at
     * least the value of the parent region, and the child region will adopt the ownership of the parent region.
     *
     * @param parent the parent region.
     * @param child  the child region.
     */
    public synchronized void associate(Region parent, Region child) {
        // The child region no longer needs to be in the world region list since it is now managed by the parent
        getWorldData(child.getWorld()).getRegions().remove(child);

        // Modify priority
        if (child.getPriority() < parent.getPriority())
            child.setPriority(parent.getPriority());
        // If the priorities are equal, then the child will mirror the flags of the parent
        if (child.getPriority() == parent.getPriority())
            child.setFlags(parent.getFlags());

        // Adopt ownership and solidify the parent-child relation
        child.setOwner(parent.getOwner());
        child.setParent(parent);
        parent.getChildren().add(child);
    }

    /**
     * Returns all the regions in a given world without a parent.
     *
     * @param world the world.
     * @return all the regions in a given world without a parent.
     */
    public List<Region> getRegionsInWorld(World world) {
        return getWorldData(world).getRegions();
    }

    /**
     * Returns the global flag container for the given world.
     *
     * @param world the world.
     * @return the global flag container for the given world.
     */
    public FlagContainer getWorldFlags(World world) {
        return getWorldData(world);
    }

    /**
     * Returns a list of region names including the names of child regions while excluding names which are null or empty
     * strings. If this method is called with include global name set to trust, then the resulting list will include
     * the name denoting the global flags for a world.
     *
     * @param world             the world.
     * @param includeGlobalName whether or not the include the name for the global flags of a world.
     * @return a list of region names including the names of child regions while excluding names which are null or empty
     * strings.
     */
    public List<String> getRegionNames(World world, boolean includeGlobalName) {
        List<String> names = new ArrayList<>();

        if (includeGlobalName)
            names.add(GLOBAL_FLAG_NAME);

        // Add the filtered names
        getRegionsInWorld(world).stream().filter(region -> region.getRawName() != null &&
                !region.getRawName().isEmpty()).forEach(region -> {
            names.add(region.getRawName());
            // Add the children's names
            region.getChildren().stream().map(Region::getRawName).forEach(names::add);
        });

        return names;
    }

    /**
     * Returns a list of admin region names including the names of child regions while excluding player regions and
     * regions with names which are null or empty strings. If this method is called with include global name set to
     * trust, then the resulting list will include the name denoting the global flags for a world.
     *
     * @param world             the world.
     * @param includeGlobalName whether or not the include the name for the global flags of a world.
     * @return a list of region names including the names of child regions while excluding names which are null or empty
     * strings.
     */
    public List<String> getAdminRegionNames(World world, boolean includeGlobalName) {
        List<String> names = new ArrayList<>();

        if (includeGlobalName)
            names.add(GLOBAL_FLAG_NAME);

        // Add the filtered names
        getRegionsInWorld(world).stream().filter(region -> region.getRawName() != null &&
                !region.getRawName().isEmpty() && region.isAdminOwned()).forEach(region -> {
            names.add(region.getRawName());
            // Add the children's names
            region.getChildren().stream().map(Region::getRawName).forEach(names::add);
        });

        return names;
    }

    /**
     * Returns a list of player region names in specified world, excluding null or empty strings.
     *
     * @param player            The Player
     * @param world             the world.
     * @return a list of region names, excluding names which are null or empty strings.
     */
    public List<String> getPlayerNamedRegions(Player player, World world) {
        List<String> names = new ArrayList<>();

        // Add the filtered names
        getRegionsInWorld(world).stream().filter(region -> region.getRawName() != null &&
                !region.getRawName().isEmpty() && region.owner.equals(player.getUniqueId())).forEach(region -> {
            names.add(region.getRawName());
        });

        return names;
    }

    /**
     * Return a list of all regions owned by the specified player in specified world.
     *
     * @param player The Player
     * @param world  the world.
     * @return a list of regions owned by the player
     */
    public List<Region> getPlayerRegions(Player player, World world) {
        List<Region> names = new ArrayList<>();
        getRegionsInWorld(world).stream().filter(region -> region.owner.equals(player.getUniqueId())).forEach(names::add);
        return names;
    }
    /**
     * Return a list of all regions owned by the specified player in specified world.
     *
     * @param uuid  The Player's UUID
     * @param world the world.
     * @return a list of regions owned by the player
     */
    public List<Region> getPlayerRegions(UUID uuid, World world) {
        List<Region> names = new ArrayList<>();
        getRegionsInWorld(world).stream().filter(region -> region.owner.equals(uuid)).forEach(names::add);
        return names;
    }

    public Region getPlayerRegionByName(Player player, World world, String name){
        for (Region region : getPlayerRegions(player, world)) {
            if(Objects.equals(region.getRawName(), name)){
                return region;
            }
        }
        return null;

    }

    /**
     * Gets a region by the given name and returns it.
     *
     * @param world the world that contains the region.
     * @param name  the name of the region.
     * @return the region with the given name in the given world, or null if it could not be found.
     */
    public synchronized Region getRegionByName(World world, String name) {
        for (Region region : getRegionsInWorld(world)) {
            if (Objects.equals(region.getRawName(), name))
                return region;

            for (Region child : region.getChildren()) {
                if (Objects.equals(child.getRawName(), name))
                    return child;
            }
        }

        return null;
    }

    /**
     * Returns a list of regions that contain the specified location.
     *
     * @param location the location.
     * @return a list of regions that contain the specified location.
     */
    public synchronized List<Region> getRegionsAt(Location location) {
        return getWorldData(location).getLookupTable().getRegionsAt(location);
    }

    /**
     * Identical to the getRegionsAt method except this method does not take into account y-axis restrictions.
     *
     * @param location the location.
     * @return a list of regions that contain the x and z value of the given location.
     */
    public synchronized List<Region> getRegionsAtIgnoreY(Location location) {
        return getWorldData(location).getLookupTable().getRegionsAtIgnoreY(location);
    }

    /**
     * Checks to see if the set of regions at each location differ from each other in any way.
     *
     * @param from the original location.
     * @param to   the destination location.
     * @return true if the set of regions at each location differ, false otherwise.
     */
    public synchronized boolean crossesRegions(Location from, Location to) {
        return !getRegionsAt(from).equals(getRegionsAt(to));
    }

    /**
     * @param location the location.
     * @return the highest priority region at the given location.
     */
    public synchronized Region getHighestPriorityRegionAt(Location location) {
        return getWorldData(location).getLookupTable().getHighestPriorityRegionAt(location);
    }

    /**
     * @param location the location.
     * @return the lowest priority region at the given location.
     */
    public synchronized Region getLowestPriorityRegionAtIgnoreY(Location location) {
        return getWorldData(location).getLookupTable().getLowestPriorityRegionAtIgnoreY(location);
    }

    /**
     * @param location the location.
     * @return the highest priority region at the given location.
     */
    public synchronized Region getHighestPriorityRegionAtIgnoreY(Location location) {
        return getWorldData(location).getLookupTable().getHighestPriorityRegionAtIgnoreY(location);
    }

    /**
     * @param location the location.
     * @return a list of the regions at the given location which do not have a parent.
     */
    public synchronized List<Region> getParentRegionsAt(Location location) {
        return getWorldData(location).getLookupTable().getParentRegionsAt(location);
    }

    /**
     * Gets the flags present at a certain location, accounting for region priorities and global flags.
     *
     * @param location the location.
     * @return the flags at the specified location, or null if no flags are present.
     */
    public synchronized FlagContainer getFlagsAt(Location location) {
        FlagContainer worldFlags = getWorldData(location);
        List<Region> regions = getRegionsAt(location);

        // Quick check for an absence of regions
        if (regions.isEmpty())
            return worldFlags.isEmpty() ? null : worldFlags;

        // Filter out the regions that are near the location but don't contain it
        regions.removeIf(region -> !region.contains(location));
        if (regions.isEmpty())
            return worldFlags.isEmpty() ? null : worldFlags;

        // Another quick check to avoid computation
        if (regions.size() == 1 && worldFlags.isEmpty())
            return regions.get(0);

        // Copy the region flags (highest priority first), and take the ownership of the highest priority region
        FlagContainer flags = new FlagContainer(null);
        regions.stream().sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority())).forEach(region -> {
            region.getFlags().forEach((flag, meta) -> {
                if (!flags.hasFlag(flag))
                    flags.setFlag(flag, meta);
            });

            if (flags.getOwner() == null)
                flags.setOwner(region.getOwner());
            else
                flags.addCoOwner(region.getOwner());

            if (flags.getBounds() == null)
                flags.setBounds(region.getBounds());

            region.getCoOwners().forEach(flags::addCoOwner);
        });

        // Copy the world flags
        worldFlags.getFlags().forEach((flag, meta) -> {
            if (!flags.hasFlag(flag))
                flags.setFlag(flag, meta);
        });

        return flags;
    }

    /**
     * Notifies the given delegate, if not null, with the given input format string and inserted values.
     *
     * @param delegate the delegate to notify, or null if this method should do nothing.
     * @param input    the input format string.
     * @param values   the values to insert into the string.
     */
    private static void notifyDelegate(Player delegate, String input, Object... values) {
        if (delegate != null)
            TextUtils.sendFormatted(delegate, input, values);
    }

    /**
     * Attempts to create a claim with the provided vertices and the specified player as the owner. The provided
     * vertices do not need to be minimums and maximums respectively, however they will be assumed to be diagonally
     * opposite from one another. The created region will extend from y=62 to the maximum world height if in the
     * overworld, else the claim will extend from y=0 to world height. The following conditions are tested to ensure the
     * claim can be safely created:
     * <ul>
     * <li>Collisions with regions that do not allow overlap (excluding child regions)</li>
     * <li>A side being less than three blocks in length</li>
     * <li>The player not having enough claim blocks</li>
     * <li>The claim having a smaller than the minimum claim size</li>
     * </ul>
     * If any of these conditions are found, then the player will be notified with an error message and null will be
     * returned. Upon successful creation of the claim, the player will have the area of the claim subtracted from their
     * claim blocks and the created region will be registered and returned.
     *
     * @param creator   the player creating the claim.
     * @param vertex1   the first claim vertex.
     * @param vertex2   the second claim vertex.
     * @param sendEvent whether or not to dispatch a claim creation event.
     * @return the claim, or null if the claim could not be created.
     */
    public synchronized Region tryCreateClaim(Player creator, Location vertex1, Location vertex2, boolean sendEvent) {
        // Convert the given vertices into a minimum and maximum vertex
        Location min = new Location(
                vertex1.getWorld(),
                Math.min(vertex1.getX(), vertex2.getX()),
                vertex1.getWorld().getEnvironment() == World.Environment.NORMAL
                        ? Math.min(DEFAULT_CLAIM_BOTTOM_Y, Math.min(vertex1.getBlockY(), vertex2.getBlockY()))
                        : 0,
                Math.min(vertex1.getZ(), vertex2.getZ())
        );
        Location max = new Location(
                vertex1.getWorld(),
                Math.max(vertex1.getX(), vertex2.getX()),
                vertex1.getWorld().getEnvironment() == World.Environment.NETHER ? 127 : vertex1.getWorld().getMaxHeight(),
                Math.max(vertex1.getZ(), vertex2.getZ())
        );

        // Create the region
        Region region = new Region(null, 0, creator.getUniqueId(), min, max, null, new ArrayList<>());
        // The default is full-trust, so make sure no one has trust
        region.setFlag(RegionFlag.TRUST, TrustMeta.NO_TRUST.copy());
        // Make sure overlap is not allowed
        region.setFlag(RegionFlag.OVERLAP, false);

        // checkCollisions sends an error message to the creator
        if (failsCollisionCheck(creator, region))
            return null;

        // Also sends the error message
        if (failsSideCheck(creator, region))
            return null;

        // Check claim blocks
        PlayerSession ps = getPlayerSession(creator);
        long area = region.area();
        if (area > ps.getClaimBlocks()) {
            notifyDelegate(creator, "&(red)You need {&(gold)%0} more claim blocks to create this claim.",
                    area - ps.getClaimBlocks());
            return null;
        }

        // Check to make sure it's at least the minimum area
        if (area < RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) {
            notifyDelegate(creator, "&(red)This claim is too small! Your claim must have an area of at least " +
                    "{&(gold)%0} blocks.", RegionProtection.getRPConfig().getInt("general.minimum-claim-size"));
            return null;
        }

        if (sendEvent) {
            ClaimCreationEvent event = new ClaimCreationEvent(creator, region);
            RegionProtection.getInstance().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled())
                return null;
        }

        // Modify claim blocks
        ps.subtractClaimBlocks((int) area);
        // Register the claim
        getWorldData(creator.getWorld()).addRegion(region);

        return region;
    }

    /**
     * Simply creates an administrator-owned region with the given vertices. This method does not register the region,
     * this should be done in the tryRegisterRegion method.
     *
     * @param vertex1 the first vertex.
     * @param vertex2 the second vertex.
     * @return the region, or null if the region could not be created.
     */
    // Note: this method still exists in case checks need to be added to this process
    public Region tryCreateAdminRegion(Location vertex1, Location vertex2) {
        // Convert the given vertices into a minimum and maximum vertex
        Location min = new Location(vertex1.getWorld(), Math.min(vertex1.getX(), vertex2.getX()),
                Math.min(vertex1.getY(), vertex2.getY()), Math.min(vertex1.getZ(), vertex2.getZ()));
        Location max = new Location(vertex1.getWorld(), Math.max(vertex1.getX(), vertex2.getX()),
                Math.max(vertex1.getY(), vertex2.getY()), Math.max(vertex1.getZ(), vertex2.getZ()));

        return new Region(min, max);
    }

    /**
     * Registers a given region with the given parameters and adds it to the region list and lookup table. If the given
     * region should not be assigned to a parent region then the given parent name should be null. If the given parent
     * name is not null and the name is valid, then the given region will be associated to the region with the given
     * parent name according to the associate method in this class. Upon failure to register the region, the given
     * delegate will be notified with an error message and false will be returned.
     *
     * @param delegate   the player registering the region.
     * @param region     the region to register.
     * @param name       the name of the region.
     * @param priority   the priority of the region.
     * @param parentName the name of the parent of the region, or null if the region should not have a parent.
     * @param force      whether of not to force the registration of the given region.
     * @return true if the registration was successful, false otherwise.
     */
    public synchronized boolean tryRegisterRegion(Player delegate, Region region, String name, int priority,
                                                  String parentName, boolean force) {
        // Check the name
        if (isRegionNameInvalid(delegate, region.getWorld(), name))
            return false;

        // Do most of the registration
        region.setName(name);
        region.setPriority(priority);

        // Adding it to the region list is only needed if it's not a child region
        if (parentName != null) { // Have the parent region manage this region
            // Check to make sure the name is valid
            Region parent = getRegionByName(region.getWorld(), parentName);
            if (parent == null) {
                notifyDelegate(delegate, "&(red)Could not find a region with name {&(gold)%0}.", parentName);
                return false;
            }

            if (parent.hasParent()) {
                notifyDelegate(delegate, "&(red)You cannot assign a child to region {&(gold)%0} since that " +
                        "region has a parent.", parentName);
                return false;
            }

            associate(parent, region);
        }

        // checkCollisions sends an error message to the creator
        if (!force && failsCollisionCheck(delegate, region)) {
            if (region.hasParent())
                region.getParent().getChildren().remove(region);

            return false;
        }

        // Add the region to this list if it's not a child region, and add the region to the lookup table
        getWorldData(region.getWorld()).addRegion(region);

        return true;
    }

    /**
     * Has the same effect as combining the create and register methods above.
     *
     * @param vertex1  the first vertex.
     * @param vertex2  the second vertex.
     * @param name     the name of the region.
     * @param priority the priority of the region.
     * @param parent   the parent to register the region with, or null if the region has no parent.
     * @param force    whether or not to force the registration of this region.
     */
    public Region tryCreateAndRegisterRegion(Location vertex1, Location vertex2, String name, int priority,
                                             Region parent, boolean force) {
        // Create it
        Region region = tryCreateAdminRegion(vertex1, vertex2);

        // Try registration
        if (!tryRegisterRegion(null, region, name, priority, null, force))
            return null;

        // Parent-related registration
        if (parent != null && !parent.hasParent())
            associate(parent, region);

        return region;
    }

    /**
     * Attempts to modify the size of a given claim, which can be a sub-claim. This method does not check to ensure that
     * the provided player has permission to modify the size of the claim, this check should be performed outside of
     * this method. The two given locations are the original location and new location of any vertex of the claim. The
     * following conditions are tested to ensure the claim can be resized safely:
     * <ul>
     * <li>Collisions with regions that do not allow overlap (excluding child regions)</li>
     * <li>A side being less than three blocks in length</li>
     * <li>The player not having enough claim blocks</li>
     * <li>The new claim size being smaller than the minimum claim size</li>
     * <li>Any children no longer being fully contained within the parent region</li>
     * </ul>
     * The first two conditions above are tested for all regions, while the rest are only tested for
     * non-administrator-owned parent claims. The only unique check performed to a subdivision is to ensure it is still
     * completely contained within the parent claim. If any of these conditions are present, then the player will be
     * notified with an error message and false will be returned. Upon successful resizing of the claim, the owner of
     * the claim will gain or lose claim blocks depending on the change in area of the claim and true will be returned.
     *
     * @param delegate       the player resizing the claim.
     * @param claim          the claim to resize.
     * @param originalVertex the original vertex of the claim.
     * @param newVertex      the new location of the vertex.
     * @param sendEvent      whether or not to send an event to confirm the resizing.
     * @return true if the resizing was successful, false otherwise.
     */
    public synchronized boolean tryResizeClaim(Player delegate, Region claim, Location originalVertex,
                                               Location newVertex, boolean sendEvent) {
        // Copy the old values for reversion upon failure
        Pair<Location, Location> bounds = claim.getBounds();

        // Resize the claim
        claim.moveVertex(originalVertex, newVertex);

        // Manage collisions, sides, claim blocks, and subdivisions becoming exclaves
        if (failsResizeChecks(delegate, claim, bounds))
            return false;

        // Send an event if necessary
        if (sendEvent) {
            ClaimResizeEvent event = new ClaimResizeEvent(delegate, claim, bounds);
            RegionProtection.getInstance().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                claim.setBounds(bounds);
                return false;
            }
        }

        getWorldData(claim.getWorld()).getLookupTable().reAdd(claim, bounds);

        return true;
    }

    /**
     * Attempts to expand the given region in the given direction by the given amount. This method should only be used
     * for administrator-owned regions.
     *
     * @param delegate  the player expanding the region.
     * @param region    the region to expand.
     * @param direction the direction in which to expand the region.
     * @param amount    the amount by which to expand the region.
     * @return true if the region was successful expanded, false otherwise.
     */
    public synchronized boolean tryExpandRegion(Player delegate, Region region, BlockFace direction, int amount) {
        // Copy old bounds
        Pair<Location, Location> bounds = region.getBounds();

        // Perform the size modification
        switch (direction) {
            case UP:
                region.getMax().add(0, amount, 0);
                break;

            case DOWN:
                region.getMin().subtract(0, amount, 0);
                break;

            case NORTH:
                region.getMin().subtract(0, 0, amount);
                break;

            case SOUTH:
                region.getMax().add(0, 0, amount);
                break;

            case EAST:
                region.getMax().add(amount, 0, 0);
                break;

            case WEST:
                region.getMin().subtract(amount, 0, 0);
                break;

            default:
                notifyDelegate(delegate, "&(red)Invalid direction: {&(gold)%0}. Please only use the four " +
                        "cardinal directions as well as up and down.", direction);
                return false;
        }

        // Manage collisions, sides, claim blocks, and subdivisions becoming exclaves
        if (failsResizeChecks(delegate, region, bounds))
            return false;

        // Make sure the bounds are still correct
        region.reevaluateBounds();
        // Re-add the claim to the lookup table
        getWorldData(region.getWorld()).getLookupTable().reAdd(region, bounds);

        return true;
    }

    /**
     * Attempts to set the bounds of the given region to the given new bounds.
     *
     * @param delegate  the player performing the operation.
     * @param region    the region to modify.
     * @param newBounds the new region bounds.
     * @return true if the resizing was successful, false otherwise.
     */
    public synchronized boolean tryRedefineBounds(Player delegate, Region region, Pair<Location, Location> newBounds) {
        // Save a copy for reversal upon failure
        Pair<Location, Location> oldBounds = region.getBounds();

        // Update bounds
        region.setBounds(newBounds);

        // Manage collisions, sides, claim blocks, and subdivisions becoming exclaves
        if (failsResizeChecks(delegate, region, oldBounds))
            return false;

        getWorldData(region.getWorld()).getLookupTable().reAdd(region, oldBounds);

        return true;
    }

    /**
     * Attempts to create a subdivision of a given claim with the given vertices. This method does not check to ensure
     * that the provided player has permission to subdivide this claim, this check should be performed outside of this
     * method. The provided vertices do not need to be minimums and maximums respectively, however they will be assumed
     * to be diagonally opposite from one another. The created subdivision will adopt the ownership of the given claim,
     * as well as the minimum y-value of the given claim. The following conditions are tested to see if the subdivision
     * cannot be created:
     * <ul>
     * <li>Incomplete containment of the subdivision within the given claim</li>
     * <li>Collisions with other subdivisions</li>
     * <li>A side being less than three blocks in length</li>
     * <li>An area smaller than the minimum subdivision area</li>
     * </ul>
     * If any of these checks fail, then the player will be notified with a message and null will be returned. Upon
     * successful subdividing of the claim, the subdivision will become a child of the given region, and will have a
     * higher priority than the given region.
     *
     * @param delegate the creator of the subdivision.
     * @param claim    the claim to subdivide.
     * @param vertex1  the first vertex of the subdivision.
     * @param vertex2  the second vertex of the subdivision.
     * @return the subdivision, or null if the subdivision could not be created.
     */
    public synchronized Region tryCreateSubdivision(Player delegate, Region claim, Location vertex1, Location vertex2) {
        // Convert the given vertices into a minimum and maximum vertex
        Location min = new Location(vertex1.getWorld(), Math.min(vertex1.getX(), vertex2.getX()),
                claim.getMin().getBlockY(), Math.min(vertex1.getZ(), vertex2.getZ()));
        Location max = new Location(vertex1.getWorld(), Math.max(vertex1.getX(), vertex2.getX()),
                claim.getMax().getY(), Math.max(vertex1.getZ(), vertex2.getZ()));

        // Create the region
        Region subdivision = new Region(null, claim.getPriority() + 1, claim.getOwner(), min, max, claim, claim.getCoOwners());

        // Check for complete containment
        if (!claim.contains(subdivision)) {
            notifyDelegate(delegate, "&(red)A claim subdivision must be completely within the parent claim.");
            return null;
        }

        // Build the list of collisions ignoring the parent claim (this code is slightly different from the regular
        // checkCollisions method)
        Set<Region> collisions = getWorldData(claim.getWorld()).getLookupTable().getCollisions(subdivision);
        collisions.remove(claim);
        collisions.removeIf(r -> r.isAllowed(RegionFlag.OVERLAP));

        // Check collisions
        if (!collisions.isEmpty()) {
            notifyDelegate(delegate, "&(red)You cannot have a claim here since it overlaps other claims.");
            getPlayerSession(delegate).setRegionHighlighter(new RegionHighlighter(delegate,
                    collisions, Material.GLOWSTONE, Material.NETHERRACK, false));
            return null;
        }

        // Make sure the sides are the minimum length
        if (failsSideCheck(delegate, subdivision))
            return null;

        // Area check
        if (subdivision.area() < RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size")) {
            notifyDelegate(delegate, "&(red)A claim subdivision must have an area of at least {&(gold)%0} " +
                    "blocks.", RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size"));
            return null;
        }

        // Register the subdivision
        associate(claim, subdivision);
        getWorldData(subdivision.getWorld()).addRegion(subdivision);

        return subdivision;
    }

    /**
     * Attempts to transfer the ownership of the given region to the new, given owner. A transfer of ownership also
     * involves a transfer of claim blocks where the original owner gains the claim blocks used to create the claim and
     * the new owner has those blocks subtracted. If the new owner does not haven enough claim blocks then the transfer
     * fails and the delegate is notified with a message. Administrator-owned regions cannot have their ownership
     * transferred.
     *
     * @param delegate      the player performing the transfer.
     * @param region        the region to transfer ownership.
     * @param newOwner      the new owner of the region.
     * @param transferTrust whether or not to keep the trust flag through the transfer.
     * @return true if the transfer was successful, false otherwise.
     */
    public boolean tryTransferOwnership(Player delegate, Region region, UUID newOwner, boolean transferTrust) {
        // Don't allow admin regions to have their ownership transferred
        if (region.isAdminOwned()) {
            notifyDelegate(delegate, "&(red)You cannot transfer the ownership of an admin owned region.");
            return false;
        }

        // Check claim blocks
        if (region.area() > getClaimBlocks(newOwner) && !(delegate != null && delegate.isOp())) {
            notifyDelegate(delegate, "&(red)This player does not have enough claim blocks to take ownership of " +
                    "this claim.");
            return false;
        }

        // Reset trust if necessary
        if (!transferTrust) {
            region.setFlag(RegionFlag.TRUST, TrustMeta.NO_TRUST.copy());
            region.getChildren().forEach(child -> child.deleteFlag(RegionFlag.TRUST));
        }

        // Modify claim blocks and transfer the ownership
        modifyClaimBlocks(region.getOwner(), (int) region.area());
        modifyClaimBlocks(newOwner, -(int) region.area());
        region.setOwner(newOwner);

        return true;
    }

    /**
     * Attempts to change the name of the given region to the new name provided. If the new name is already in-use in
     * the given region's world or if that name is reserved.
     *
     * @param delegate the player performing the renaming.
     * @param region   the region to rename.
     * @param newName  the new name to give the region.
     * @return true if the renaming was successful, false otherwise.
     */
    public synchronized boolean tryRenameRegion(Player delegate, Region region, String newName) {
        if (isRegionNameInvalid(delegate, region.getWorld(), newName))
            return false;

        region.setName(newName);
        return true;
    }

    /**
     * Attempts to delete the given region. This will remove the region from the list and the lookup table, and the same
     * operation will be performed on all the region's children if includeChildren is set to true. Upon successful
     * deletion of the region, if the given region has no parent and is not owned by an administrator then the owner
     * will regain the claim blocks used to create the given region.
     *
     * @param delegate        the player deleting the region.
     * @param region          the region.
     * @param includeChildren whether or not to delete the regions children as well.
     * @return true if the region was successfully deleted, false otherwise.
     */
    public synchronized boolean tryDeleteRegion(Player delegate, Region region, boolean includeChildren) {
        return tryDeleteRegion(delegate, region, includeChildren, true);
    }

    /**
     * Attempts to delete the regions in the given world that match the given predicate. Only parent regions are tested
     * with the given filter.
     *
     * @param delegate        the player deleting the regions.
     * @param world           the world to delete the regions from.
     * @param filter          the filter by which to determine which regions should be deleted.
     * @param includeChildren whether or not to include the children of the regions that match the given filter.
     */
    public synchronized void tryDeleteRegions(Player delegate, World world, Predicate<Region> filter,
                                              boolean includeChildren) {
        Iterator<Region> itr = getRegionsInWorld(world).iterator();
        Region region;
        while (itr.hasNext()) {
            region = itr.next();
            // Delete the region if it matches the predicate
            if (filter.test(region) && tryDeleteRegion(delegate, region, includeChildren, false))
                itr.remove();
        }
    }

    /**
     * Actually performs the deletion of the given region. The removal of the given region from the lookup table always
     * occurs however the regions will not be removed from its world's list unless the unregister parameter is true.
     * This also includes the removal of child regions from their parent.
     *
     * @param delegate        the player performing the deletion.
     * @param region          the region to delete.
     * @param includeChildren whether or not to delete the given region's children.
     * @param unregister      whether or not the remove the given region from its world data's list.
     * @return true if the region was deleted successfully, false otherwise.
     */
    private boolean tryDeleteRegion(Player delegate, Region region, boolean includeChildren, boolean unregister) {
        // Delete children
        if (!region.getChildren().isEmpty()) {
            if (includeChildren) {
                region.getChildren().forEach(child -> tryDeleteRegion(delegate, child, false, false));
                region.getChildren().clear();
            } else {
                notifyDelegate(delegate, "&(red)This region has subdivisions which must be removed first.");
                return false;
            }
        }

        // Unregister the region and modify claim blocks
        // Subdivisions
        if (region.hasParent()) {
            if (unregister)
                region.getParent().getChildren().remove(region);
        }
        // Parent claims
        else {
            if (unregister)
                getRegionsInWorld(region.getWorld()).remove(region);

            if (!region.isAdminOwned())
                modifyClaimBlocks(region.getOwner(), (int) region.area());
        }

        // Remove the region from the lookup table
        getWorldData(region.getWorld()).getLookupTable().remove(region);

        return true;
    }

    /**
     * @param delegate the player providing the name.
     * @param world    the world the region to be named is in.
     * @param name     the name to check.
     * @return true if the specified name is the name of another region in the given world or if the name is reserved.
     */
    private boolean isRegionNameInvalid(Player delegate, World world, String name) {
        // Make sure the name is free
        if (getRegionByName(world, name) != null) {
            notifyDelegate(delegate, "&(red)A region with that name already exists.");
            return true;
        }

        // Make sure the __global__ region is not overwritten
        if (GLOBAL_FLAG_NAME.equals(name)) {
            notifyDelegate(delegate, "&(red)This region name is reserved.");
            return true;
        }

        return false;
    }

    /**
     * Makes sure the given region does not violate any constraints after being resized, and resets the region to the
     * given bounds if those constraints are violated. Essentially the same checks that are performed upon a region's
     * creation are performed here, however this method supports parent and child regions and has different checks for
     * both.
     *
     * @param delegate the player resizing the region.
     * @param region   the region to resize.
     * @param bounds   the old bounds of the region.
     * @return true if the region did not pass the resize checks, false otherwise.
     */
    private boolean failsResizeChecks(Player delegate, Region region, Pair<Location, Location> bounds) {
        // checkCollisions sends the error message to the player
        if (failsCollisionCheck(delegate, region)) {
            region.setBounds(bounds);
            return true;
        }

        // Also sends an error message to the delegate
        if (failsSideCheck(delegate, region)) {
            region.setBounds(bounds);
            return true;
        }

        // Admin regions do not require the following checks
        if (region.isAdminOwned())
            return false;

        // If the delegate is a co-owner, use their claim blocks
        UUID claimBlockDelegate = delegate == null
                ? region.getOwner()
                : (region.isEffectiveOwner(delegate) ? delegate.getUniqueId() : region.getOwner());

        // Regular claims
        if (!region.hasParent()) {
            // Check claim blocks
            int claimBlocks = getClaimBlocks(claimBlockDelegate);
            long areaDiff = region.area() - ((long) bounds.getSecond().getBlockX() - bounds.getFirst().getBlockX()) *
                    ((long) bounds.getSecond().getBlockZ() - bounds.getFirst().getBlockZ());
            if (areaDiff > claimBlocks) {
                region.setBounds(bounds);
                notifyDelegate(delegate, "&(red)You need {&(gold)%0} more claim blocks to resize this claim.",
                        areaDiff - claimBlocks);
                return true;
            }

            // Check to make sure it's at least the minimum area
            if (region.area() < RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) {
                region.setBounds(bounds);
                notifyDelegate(delegate, "&(red)This claim is too small! Your claim must have an area of at " +
                        "least {&(gold)%0} blocks.", RegionProtection.getRPConfig().getInt("general.minimum-claim-size"));
                return true;
            }

            // Check to make sure all subdivisions are still within the parent region
            List<Region> exclaves = new ArrayList<>();
            region.getChildren().stream().filter(r -> !region.contains(r)).forEach(exclaves::add);
            if (!exclaves.isEmpty()) {
                region.setBounds(bounds);
                notifyDelegate(delegate, "&(red)You cannot resize your claim here since some subdivisions are " +
                        "not completely within the parent region.");
                getPlayerSession(delegate).setRegionHighlighter(new RegionHighlighter(delegate, exclaves,
                        Material.GLOWSTONE, Material.NETHERRACK, false));
                return true;
            }

            // Modify claim blocks
            modifyClaimBlocks(claimBlockDelegate, -(int) areaDiff);
        }
        // Subdivisions
        else {
            // Check to make sure the subdivision is still completely within the parent claim
            if (!region.getParent().contains(region)) {
                region.setBounds(bounds);
                notifyDelegate(delegate, "&(red)You cannot resize this subdivision here since it exits the " +
                        "parent claim.");
                getPlayerSession(delegate).setRegionHighlighter(new RegionHighlighter(delegate, region.getParent()));
                return true;
            }

            // Check to make sure it's at least the minimum area
            if (region.area() < RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size")) {
                region.setBounds(bounds);
                notifyDelegate(delegate, "&(red)This claim is too small! Your claim must have an area of at " +
                        "least {&(gold)%0} blocks.", RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size"));
                return true;
            }
        }

        return false;
    }

    /**
     * Checks for collisions between the given region and other non-child claims that do not permits overlap. The given
     * delegate will be notified if overlap is detected and those regions will be highlighted.
     *
     * @param delegate the player associated with the region being checked.
     * @param region   the region to check.
     * @return true if collisions are present, false otherwise.
     */
    private boolean failsCollisionCheck(Player delegate, Region region) {
        Set<Region> collisions = getWorldData(region.getWorld()).getLookupTable().getCollisions(region);
        collisions.removeIf(r -> r.isAllowed(RegionFlag.OVERLAP) || r.isAssociated(region));

        if (!collisions.isEmpty()) {
            notifyDelegate(delegate, "&(red)You cannot have a claim or region here since it overlaps other claims and/or regions.");
            getPlayerSession(delegate).setRegionHighlighter(new RegionHighlighter(delegate, collisions,
                    Material.GLOWSTONE, Material.NETHERRACK, false));
            return true;
        }

        return false;
    }

    /**
     * Ensures that each side of the given region is at least three blocks long.
     *
     * @param delegate the player associated with the given region.
     * @param region   the region to check.
     * @return true if the region has improper dimensions, false otherwise.
     */
    private boolean failsSideCheck(Player delegate, Region region) {
        // < 2 is used to counter the off-by-one error (in other words it accounts for the width of the block)
        if (region.getMax().getBlockX() - region.getMin().getBlockX() < 2 ||
                region.getMax().getBlockZ() - region.getMin().getBlockZ() < 2) {
            notifyDelegate(delegate, "&(red)All sides of your claim must be at least three blocks long.");
            return true;
        }

        return false;
    }

    /**
     * Returns a player session for the given player.
     *
     * @param player the player.
     * @return a player session for the given player.
     */
    public synchronized PlayerSession getPlayerSession(Player player) {
        // Get the cached entry
        if (playerSessionCache.containsKey(player.getUniqueId())) {
            return playerSessionCache.get(player.getUniqueId());
        }
        // Enter a new cached entry
        else {
            playerData.putIfAbsent(player.getUniqueId(), new PersistentPlayerData(player));
            PlayerSession ps = new PlayerSession(playerData.get(player.getUniqueId()));
            playerSessionCache.put(player.getUniqueId(), ps);
            return ps;
        }
    }

    /**
     * Returns the current number of claim blocks associated with the given UUID. If no claim blocks are associated with
     * the given uuid, then the default starting amount is returned.
     *
     * @param uuid the player's UUID.
     * @return the number of claim blocks associated with the given UUID, or the default amount if there are no claim
     * blocks associated with the given UUID.
     */
    public synchronized int getClaimBlocks(UUID uuid) {
        // Use the live player session if it exists
        if (playerSessionCache.containsKey(uuid)) {
            return playerSessionCache.get(uuid).getClaimBlocks();
        }
        // Default to the persistent player data if the session is not present
        else if (playerData.containsKey(uuid)) {
            return playerData.get(uuid).getClaimBlocks();
        }
        // If there's no player data either, make new player data
        else {
            PersistentPlayerData ppd = new PersistentPlayerData(uuid);
            playerData.put(uuid, ppd);
            return ppd.getClaimBlocks();
        }
    }

    /**
     * Modifies the number of claim blocks associated with the given UUID by adding the given amount to the current
     * number of claim blocks.
     *
     * @param uuid   the player's UUID.
     * @param amount the amount by which to modify the player's claim blocks.
     */
    public synchronized void modifyClaimBlocks(UUID uuid, int amount) {
        // Use the live player session if it exists
        if (playerSessionCache.containsKey(uuid)) {
            playerSessionCache.get(uuid).addClaimBlocks(amount);
        }
        // Default to the persistent player data if the session is not present
        else if (playerData.containsKey(uuid)) {
            playerData.get(uuid).addClaimBlocks(amount);
        }
        // If there's no player data either, make new player data
        else {
            PersistentPlayerData ppd = new PersistentPlayerData(uuid);
            ppd.addClaimBlocks(amount);
            playerData.put(uuid, ppd);
        }
    }

    /**
     * Loads all data pertaining to the Region Protection plugin. If certain files are missing, then they will be
     * created and initialized.
     */
    public void load() {
        // Load data for each world
        try {
            File regionsFile = new File(rootDir.getAbsolutePath() + File.separator + "regions.dat");
            final Map<UUID, WorldData> deserializedWorldData;
            if (!regionsFile.exists()) {
                if (!regionsFile.createNewFile()) {
                    RegionProtection.error("Failed to create regions.dat upon loading data.");
                    return;
                }

                deserializedWorldData = new HashMap<>();
            } else {
                Deserializer deserializer = new Deserializer(regionsFile, REGION_FORMAT_VERSION);
                deserializedWorldData = deserializer.readWorldData();
            }

            // Initialize world data objects
            Bukkit.getWorlds().stream().map(World::getUID).forEach(uuid ->
                    worlds.put(uuid, deserializedWorldData.getOrDefault(uuid, new WorldData(uuid)))
            );
            deserializedWorldData.entrySet().stream().filter(entry -> !worlds.containsKey(entry.getKey())).forEach(entry ->
                    worlds.put(entry.getKey(), entry.getValue())
            );
        } catch (Throwable ex) {
            RegionProtection.error("Failed to load regions file:\n" + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        }

        // Initialize lookup tables
        worlds.values().forEach(wd -> wd.generateLookupTable(LOOKUP_TABLE_SCALE));

        // Load player data
        try {
            File playerDataFile = new File(rootDir.getAbsolutePath() + File.separator + "playerdata.dat");
            if (!playerDataFile.exists()) {
                if (!playerDataFile.createNewFile()) {
                    RegionProtection.error("Failed to create playerdata.dat upon loading data.");
                    return;
                }
            } else {
                Deserializer deserializer = new Deserializer(playerDataFile, PLAYER_DATA_FORMAT_VERSION);
                playerData.putAll(deserializer.readPlayerData());
            }
        } catch (Throwable ex) {
            RegionProtection.error("Failed to load player data file: " + ex.getMessage());
            ex.printStackTrace();
        }

        RegionProtection.log("Finished loading data.");
    }

    /**
     * Saves all data managed by this class to disk.
     */
    public synchronized void save() {
        // Save world data
        try {
            File regionsFile = new File(rootDir.getAbsolutePath() + File.separator + "regions.dat");
            if (!regionsFile.exists()) {
                if (!regionsFile.createNewFile()) {
                    RegionProtection.error("Failed to create regions.dat upon saving data.");
                    return;
                }
            }
            Serializer serializer = new Serializer(regionsFile, REGION_FORMAT_VERSION);
            serializer.writeWorldData(worlds.values());
        } catch (IOException ex) {
            RegionProtection.error("Failed to save regions file: " + ex.getMessage());
            ex.printStackTrace();
        }

        // Save player data

        // Dump the cache data into the serialized map
        playerSessionCache.forEach((uuid, ps) -> playerData.get(uuid).setClaimBlocks(ps.getClaimBlocks()));
        // Actually save the data to disc
        try {
            File playerDataFile = new File(rootDir.getAbsolutePath() + File.separator + "playerdata.dat");
            if (!playerDataFile.exists()) {
                if (!playerDataFile.createNewFile()) {
                    RegionProtection.error("Failed to create playerdata.dat upon saving data.");
                    return;
                }
            }
            Serializer serializer = new Serializer(playerDataFile, PLAYER_DATA_FORMAT_VERSION);
            serializer.writePlayerData(playerData.values());
        } catch (IOException ex) {
            RegionProtection.error("Failed to save player data file: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
