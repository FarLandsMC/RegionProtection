package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Pair;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
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
    // Used to create player sessions
    private final Map<UUID, Integer> playerClaimBlocks;
    private final Map<UUID, PlayerSession> playerSessionCache;

    // These values are used to keep consistency in the serialized data
    public static final byte REGION_FORMAT_VERSION = 0;
    public static final byte PLAYER_DATA_FORMAT_VERSION = 0;
    public static final String WORLD_REGION_NAME = "__global__";

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
        this.worlds = new HashMap<>();
        this.lookupTable = new HashMap<>();
        this.playerClaimBlocks = new HashMap<>();
        this.playerSessionCache = new HashMap<>();
    }

    /**
     * Creates a new player session for the player if one is not already present.
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public synchronized void onPlayerPreJoin(AsyncPlayerPreLoginEvent event) {
        playerClaimBlocks.putIfAbsent(event.getUniqueId(), 0);
        playerSessionCache.put(event.getUniqueId(), new PlayerSession(event.getUniqueId(),
                playerClaimBlocks.get(event.getUniqueId())));
    }

    /**
     * Deletes the cached player session for the given player.
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public synchronized void onPlayerQuit(PlayerQuitEvent event) {
        playerClaimBlocks.put(event.getPlayer().getUniqueId(), playerSessionCache.remove(event.getPlayer()
                .getUniqueId()).getClaimBlocks());
    }

    /**
     * Sets the second region as a child of the first. The priority of the child region will be modified so it is at
     * least the value of the parent region, and the child region will adopt the ownership of the parent region.
     * @param parent the parent region.
     * @param child the child region.
     */
    public synchronized void associate(Region parent, Region child) {
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
     * Returns all the regions in a given world without a parent.
     * @param world the world.
     * @return all the regions in a given world without a parent.
     */
    public List<Region> getRegionsInWorld(World world) {
        return worlds.get(world.getUID()).regions;
    }

    public FlagContainer getWorldFlags(World world) {
        return worlds.get(world.getUID());
    }

    public List<String> getRegionNames(World world) {
        List<String> names = new LinkedList<>();
        names.add(WORLD_REGION_NAME);
        worlds.get(world.getUID()).regions.forEach(region -> {
            if(region.getRawName() != null && !region.getRawName().isEmpty())
            names.add(region.getRawName());
            region.getChildren().stream().map(Region::getRawName).forEach(names::add);
        });
        return names;
    }

    /**
     * Gets a region by the given name and returns it.
     * @param world the world that contains the region.
     * @param name the name of the region.
     * @return the region with the given name in the given world, or null if it could not be found.
     */
    public synchronized Region getRegionByName(World world, String name) {
        for(Region region : worlds.get(world.getUID()).regions) {
            if(region.getRawName().equals(name))
                return region;

            for(Region child : region.getChildren()) {
                if(child.getRawName().equals(name))
                    return child;
            }
        }

        return null;
    }

    /**
     * Returns a list of regions that contain the specified location.
     * @param location the location.
     * @return a list of regions that contain the specified location.
     */
    public synchronized List<Region> getRegionsAt(Location location) {
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(
                ((long)(location.getBlockX() >> 7)) << 32 |
                ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL)
        );

        return regions == null ? Collections.emptyList() : regions.stream().filter(region -> region.contains(location))
                .collect(Collectors.toList());
    }

    /**
     * Identical to the getRegionsAt method except this method does not take into account y-axis restrictions.
     * @param location the location.
     * @return a list of regions the contain the x and z value of the given location.
     */
    public synchronized List<Region> getRegionsAtIgnoreY(Location location) {
        List<Region> regions = lookupTable.get(location.getWorld().getUID()).get(
                ((long)(location.getBlockX() >> 7)) << 32 |
                        ((long)(location.getBlockZ() >> 7) & 0xFFFFFFFFL)
        );

        return regions == null ? Collections.emptyList() : regions.stream().filter(region ->
                region.containsIgnoreY(location)).collect(Collectors.toList());
    }

    /**
     * Checks to see if there is a potential conflict of region flags by moving from the first specified location to the
     * second.
     * @param from the original location.
     * @param to the destination location.
     * @return true, if there is a conflict of flags, false otherwise.
     */
    public synchronized boolean crossesRegions(Location from, Location to) {
        return !getRegionsAt(from).equals(getRegionsAt(to));
    }

    /**
     * Returns the highest priority region at the given location.
     * @param location the location.
     * @return the highest priority region at the given location.
     */
    public synchronized Region getHighestPriorityRegionAt(Location location) {
        List<Region> regions = getRegionsAt(location);
        return regions.isEmpty() ? null : regions.stream().max(Comparator.comparingInt(Region::getPriority))
                .orElse(null);
    }

    /**
     * Returns a list of the regions at the given location which do not have a parent.
     * @param location the location.
     * @return a list of the regions at the given location which do not have a parent.
     */
    public synchronized List<Region> getParentRegionsAt(Location location) {
        List<Region> regions = getRegionsAt(location);
        return regions.stream().filter(region -> !region.hasParent()).collect(Collectors.toList());
    }

    /**
     * Gets the flags present at a certain location, accounting for region priorities and global flags.
     * @param location the location.
     * @return the fkags at the specified location, or null if no flags are present.
     */
    public synchronized FlagContainer getFlagsAt(Location location) {
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
     * opposite from one another. The created region will extend from y=62 to the maximum world height (if the claim is
     * not in the overworld the claim will automatically extend from y=0 to world height). The following checks are done
     * to see if the claim can be created:
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
    public synchronized Region tryCreateClaim(Player creator, Location vertex1, Location vertex2) {
        // Convert the given verticies into a minimum and maximum vertex
        Location min = new Location(vertex1.getWorld(), Math.min(vertex1.getX(), vertex2.getX()),
                "world".equals(vertex1.getWorld().getName()) ? 62 : 0,
                Math.min(vertex1.getZ(), vertex2.getZ()));
        Location max = new Location(vertex1.getWorld(), Math.max(vertex1.getX(), vertex2.getX()),
                vertex1.getWorld().getMaxHeight(), Math.max(vertex1.getZ(), vertex2.getZ()));

        // Create the region
        Region region = new Region(null, 0, creator.getUniqueId(), min, max, null);

        // checkCollisions sends an error message to the creator
        if(!checkCollisions(creator, region))
            return null;

        // Check claim blocks
        PlayerSession ps = playerSessionCache.get(creator.getUniqueId());
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
        // Make sure overlap is not allowed
        region.setFlag(RegionFlag.OVERLAP, false);
        // Register the claim
        worlds.get(creator.getWorld().getUID()).regions.add(region);
        addRegionToLookupTable(region, true);

        return region;
    }

    /**
     * Simply creates an administrator-owned region with the given vertices. This method checks to ensure that there is
     * no overlap with other regions that do not allow overlap. This method does not register the region, this should be
     * done in the tryRegisterRegion method.
     * @param delegate the creator of the region.
     * @param vertex1 the first vertex.
     * @param vertex2 the second vertex.
     * @return the region, or null if the region could not be created.
     */
    public Region tryCreateAdminRegion(Player delegate, Location vertex1, Location vertex2) {
        // Convert the given verticies into a minimum and maximum vertex
        Location min = new Location(vertex1.getWorld(), Math.min(vertex1.getX(), vertex2.getX()),
                Math.min(vertex1.getY(), vertex2.getY()), Math.min(vertex1.getZ(), vertex2.getZ()));
        Location max = new Location(vertex1.getWorld(), Math.max(vertex1.getX(), vertex2.getX()),
                Math.max(vertex1.getY(), vertex2.getY()), Math.max(vertex1.getZ(), vertex2.getZ()));

        // Create the region
        Region region = new Region(min, max);

        // checkCollisions sends an error message to the creator
        if(!checkCollisions(delegate, region))
            return null;

        return region;
    }

    /**
     * Registers a given region with the given parameters and adds it to the region list and lookup table. If the given
     * region should not be assigned to a parent region then the given parent name should be null. If the given parent
     * name is not null and the name is valid, then the given region will be associated to the region with the given
     * parent name according to the associate method in this class. Upon failure to register the region, the given
     * delegate will be notified with an error message and null will be returned.
     * @param delegate the player registering the region.
     * @param region the region to register.
     * @param name the name of the region.
     * @param priority the priority of the region.
     * @param parentName the name of the parent of the region, or null if the region should not have a parent.
     * @return true if the registration was successful, false otherwise.
     */
    public synchronized boolean tryRegisterRegion(Player delegate, Region region, String name, int priority,
                                                  String parentName) {
        // Make sure the name is free
        if(getRegionByName(region.getWorld(), name) != null) {
            delegate.sendMessage(ChatColor.RED + "A region with that name already exists.");
            return false;
        }

        // Make sure the __global__ region is not overwritten
        if(WORLD_REGION_NAME.equals(name)) {
            delegate.sendMessage(ChatColor.RED + "This region name is reserved.");
            return false;
        }

        // Do most of the registration
        region.setName(name);
        region.setPriority(priority);
        addRegionToLookupTable(region, true);

        // Adding it to the region list is only needed if it's not a child region
        if(parentName == null)
            worlds.get(region.getWorld().getUID()).regions.add(region);
        else{ // Have the parent region manage this region
            // Check to make sure the name is valid
            Region parent = getRegionByName(region.getWorld(), parentName);
            if(parent == null) {
                delegate.sendMessage(ChatColor.RED + "Could not find a region with name \n" + parentName + "\".");
                return false;
            }

            associate(parent, region);
        }

        return true;
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
     * If any of these checks fail, then the player will be notified with a message and false will be returned. Upon
     * successful resizing of the claim, the owner of the claim will gain or lose claim blocks depending on the change
     * in area of the claim and true will be returned.
     * @param delegate the player resizing the claim.
     * @param claim the claim to resize.
     * @param originalVertex the original vertex of the claim.
     * @param newVertex the new location of the vertex.
     * @return true if the resizing was successful, false otherwise.
     */
    public synchronized boolean tryResizeClaim(Player delegate, Region claim, Location originalVertex,
                                               Location newVertex) {
        // Copy the old values for reversion upon failure
        Pair<Location, Location> bounds = claim.getBounds();

        // Resize the claim
        claim.moveVertex(originalVertex, newVertex);

        // Manage collisions, claim blocks, exclaving of subdivisions
        if(!resizeChecks(delegate, claim, bounds))
            return false;

        readdRegionToLookupTable(claim, bounds);

        return true;
    }

    /**
     * Attempts to expand the given region in the given direction by the given amount.
     * @param delegate the player expanding the region.
     * @param region the region to expand.
     * @param direction the direction in which to expand the region.
     * @param amount the amount by which to expand the region.
     * @return true if the region was successfull expanded, false otherwise.
     */
    public synchronized boolean tryExpandRegion(Player delegate, Region region, BlockFace direction, int amount) {
        Pair<Location, Location> bounds = region.getBounds();

        // Perform the size modification
        switch(direction) {
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
                delegate.sendMessage(ChatColor.RED + "Invalid direction: " + direction + ". Please only use the " +
                        "four cardinal directions as well as up and down.");
                return false;
        }

        if(!resizeChecks(delegate, region, bounds))
            return false;

        // Make sure the bounds are still correct
        region.reevaluateBounds();
        // Re-add the claim to the lookup table
        readdRegionToLookupTable(region, bounds);

        return true;
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
     *     <li>An area smaller than the minimum subdivision area</li>
     * </ul>
     * If any of these checks fail, then the player will be notified with a message and null will be returned. Upon
     * successful subdividing of the claim, the subdivision will be child with the given region, and will have a
     * higher priority than the given region.
     * @param delegate the creator of the subdivision.
     * @param claim the claim to subdivide.
     * @param vertex1 the first vertex of the subdivision.
     * @param vertex2 the second vertex of the subdivision.
     * @return the subdivision, or null if the subdivision could not be created.
     */
    public synchronized Region tryCreateSubdivision(Player delegate, Region claim, Location vertex1, Location vertex2) {
        // Convert the given verticies into a minimum and maximum vertex
        Location min = new Location(vertex1.getWorld(), Math.min(vertex1.getX(), vertex2.getX()),
                claim.getMin().getBlockY(), Math.min(vertex1.getZ(), vertex2.getZ()));
        Location max = new Location(vertex1.getWorld(), Math.max(vertex1.getX(), vertex2.getX()),
                vertex1.getWorld().getMaxHeight(), Math.max(vertex1.getZ(), vertex2.getZ()));

        // Create the region add associate it
        Region region = new Region(null, claim.getPriority() + 1, claim.getOwner(), min, max, claim);
        associate(claim, region);

        // Check for complete containment
        if(!claim.contains(region)) {
            delegate.sendMessage(ChatColor.RED + "A claim subdivision must be completely within the parent claim.");
            return null;
        }

        // Check for collisions with other subdivisions
        if(!checkCollisions(delegate, region))
            return null;

        // Area check
        if(region.area() < RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size")) {
            delegate.sendMessage(ChatColor.RED + "A claim subdivision must have an area of at least " +
                    RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size") + " blocks.");
            return null;
        }

        // Register the subdivision
        addRegionToLookupTable(region, false);

        return region;
    }

    public boolean tryTransferOwnership(Player delegate, Region region, UUID newOwner, boolean transferTrust) {
        if(region.isAdminOwned()) {
            delegate.sendMessage(ChatColor.RED + "You cannot transfer the ownership of an admin owned region.");
            return false;
        }

        if(region.area() > getClaimBlocks(newOwner)) {
            delegate.sendMessage(ChatColor.RED + "This player does not have enough claim blocks to take ownership of " +
                    "this claim.");
            return false;
        }

        if(!transferTrust) {
            region.setFlag(RegionFlag.TRUST, TrustMeta.EMPTY_TRUST_META);
            region.getChildren().forEach(child -> child.deleteFlag(RegionFlag.TRUST));
        }

        modifyClaimBlocks(region.getOwner(), (int)region.area());
        modifyClaimBlocks(newOwner, -(int)region.area());
        region.setOwner(newOwner);

        return true;
    }

    /**
     * Deletes the given region. This will remove the region from the list and the lookup table, and the same operation
     * will be performed on all the regions children if includeChildren is set to true. Upon successful deletion of the
     * region, if the given region has no parent and is not owned by an administrator then the owner will regain the
     * claim blocks used to create the given region.
     * @param delegate the player deleting the region.
     * @param region the region.
     * @param includeChildren whether or not to delete the regions children as well.
     * @return true if the region was successfully deleted, false otherwise.
     */
    public synchronized boolean tryDeleteRegion(Player delegate, Region region, boolean includeChildren) {
        return tryDeleteRegion(delegate, region, includeChildren, true);
    }

    public synchronized void tryDeleteRegions(Player delegate, Predicate<Region> filter, boolean includeChildren) {
        Iterator<Region> itr = worlds.get(delegate.getWorld().getUID()).regions.iterator();
        Region region;
        while(itr.hasNext()) {
            region = itr.next();
            if(filter.test(region) && tryDeleteRegion(delegate, region, includeChildren, false))
                itr.remove();
        }
    }

    private boolean tryDeleteRegion(Player delegate, Region region, boolean includeChildren, boolean unregister) {
        if(!region.getChildren().isEmpty()) {
            if(includeChildren) {
                region.getChildren().forEach(child -> tryDeleteRegion(delegate, child, false, false));
                region.getChildren().clear();
            }else{
                delegate.sendMessage(ChatColor.RED + "This region has subdivisions which must be removed first.");
                return false;
            }
        }

        // Unregister the region and modify claim blocks
        if(region.hasParent()) {
            if(unregister)
                region.getParent().getChildren().remove(region);
        }else {
            if(unregister)
                worlds.get(region.getWorld().getUID()).regions.remove(region);
            if(!region.isAdminOwned())
                modifyClaimBlocks(region.getOwner(), (int) region.area());
        }

        // Remove the region from the lookup table
        for(int x = region.getMin().getBlockX() >> 7;x <= region.getMax().getBlockX() >> 7;++ x) {
            for(int z = region.getMin().getBlockZ() >> 7;z <= region.getMax().getBlockZ() >> 7;++ z) {
                lookupTable.get(region.getWorld().getUID()).get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL))
                        .remove(region);
            }
        }

        return true;
    }

    // Makes sure the given region does not violate any constraints after being resized, and resets the region to the
    // given bounds if those constraints are violated
    private boolean resizeChecks(Player delegate, Region region, Pair<Location, Location> bounds) {
        // checkCollisions sends the error message to the player
        if(!checkCollisions(delegate, region)) {
            region.setBounds(bounds);
            return false;
        }

        // Admin regions do not require the following checks
        if(region.isAdminOwned())
            return true;

        // Regular claims
        if (!region.hasParent()) {
            // Check claim blocks
            int claimBlocks = getClaimBlocks(region.getOwner());
            long areaDiff = region.area() - ((long)bounds.getSecond().getBlockX() - bounds.getFirst().getBlockX()) *
                    ((long)bounds.getSecond().getBlockZ() - bounds.getFirst().getBlockZ());
            if (areaDiff > claimBlocks) {
                region.setBounds(bounds);
                delegate.sendMessage(ChatColor.RED + "You need " + (areaDiff - claimBlocks) +
                        " more claim blocks to resize this claim.");
                return false;
            }

            // Check to make sure it's at least the minimum area
            if (region.area() < RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) {
                region.setBounds(bounds);
                delegate.sendMessage(ChatColor.RED + "This claim is too small! Your claim must have an area of at " +
                        "least " + RegionProtection.getRPConfig().getInt("general.minimum-claim-size") + " blocks.");
                return false;
            }

            // Check to make sure all subdivisions are still within the parent region
            List<Region> exclaves = new LinkedList<>();
            region.getChildren().stream().filter(r -> !region.contains(r)).forEach(exclaves::add);
            if (!exclaves.isEmpty()) {
                region.setBounds(bounds);
                delegate.sendMessage(ChatColor.RED + "You cannot resize your claim here since some subdivisions are " +
                        "not completely within the parent region.");
                playerSessionCache.get(delegate.getUniqueId()).setRegionHighlighter(new RegionHighlighter(delegate,
                        exclaves, Material.GLOWSTONE, Material.NETHERRACK, false));
                return false;
            }

            // Modify claim blocks
            modifyClaimBlocks(region.getOwner(), -(int)areaDiff);
        } else { // Subdivisions
            // Check to make sure the subdivision is still completely within the parent claim
            if (!region.getParent().contains(region)) {
                region.setBounds(bounds);
                delegate.sendMessage(ChatColor.RED + "You cannot resize this subdivision here since it exits the " +
                        "parent claim.");
                playerSessionCache.get(delegate.getUniqueId()).setRegionHighlighter(new RegionHighlighter(delegate,
                        region.getParent()));
                return false;
            }

            // Check to make sure it's at least the minimum area
            if (region.area() < RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size")) {
                region.setBounds(bounds);
                delegate.sendMessage(ChatColor.RED + "This claim is too small! Your claim must have an area of at " +
                        "least " + RegionProtection.getRPConfig().getInt("general.minimum-subdivision-size") +
                        " blocks.");
                return false;
            }
        }

        return true;
    }

    // Checks for collisions between the given region and other non-child claims that do not permis overlap
    // The given delegate will be notified if overlap is detected and those regions will be highlighted
    // Returns true if no collisions were present
    private boolean checkCollisions(Player delegate, Region region) {
        List<Region> collisions = new LinkedList<>();
        // Build a list of collisions from the lookup table
        for(int x = region.getMin().getBlockX() >> 7;x <= region.getMax().getBlockX() >> 7;++ x) {
            for(int z = region.getMin().getBlockZ() >> 7;z <= region.getMax().getBlockZ() >> 7;++ z) {
                List<Region> regions = lookupTable.get(region.getWorld().getUID())
                        .get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL));
                if(regions != null) {
                    // Ignore associations to prevent conflict with subdivisions
                    regions.stream().filter(r -> !r.isAllowed(RegionFlag.OVERLAP) && r.overlaps(region) &&
                            !r.equals(region) && !r.isAssociated(region)).forEach(collisions::add);
                }
            }
        }

        if(!collisions.isEmpty()) {
            delegate.sendMessage(ChatColor.RED + "You cannot have a claim here since it overlaps other claims.");
            playerSessionCache.get(delegate.getUniqueId()).setRegionHighlighter(new RegionHighlighter(delegate,
                    collisions, Material.GLOWSTONE, Material.NETHERRACK, false));
            return false;
        }

        return true;
    }

    /**
     * Returns a player session for the given player.
     * @param player the player.
     * @return a player session for the given player.
     */
    public synchronized PlayerSession getPlayerSession(Player player) {
        return playerSessionCache.get(player.getUniqueId());
    }

    public synchronized int getClaimBlocks(UUID uuid) {
        return playerSessionCache.containsKey(uuid) ? playerSessionCache.get(uuid).getClaimBlocks()
                : playerClaimBlocks.getOrDefault(uuid, 0);
    }

    public synchronized void modifyClaimBlocks(UUID uuid, int amount) {
        if(playerSessionCache.containsKey(uuid))
            playerSessionCache.get(uuid).addClaimBlocks(amount);
        else
            playerClaimBlocks.put(uuid, playerClaimBlocks.getOrDefault(uuid, 0) + amount);
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
                    wd.regions.forEach(region -> addRegionToLookupTable(region, true));
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
                    playerClaimBlocks.put(decoder.readUuid(), decoder.readInt());
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
    public synchronized void save() {
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
        // Dump the cache data into the serialized map
        playerSessionCache.forEach((uuid, ps) -> playerClaimBlocks.put(uuid, ps.getClaimBlocks()));

        // Actually save the data to disc
        try {
            File playerDataFile = new File(rootDir.getAbsolutePath() + File.separator + "playerdata.dat");
            if(!playerDataFile.exists())
                playerDataFile.createNewFile();
            Encoder encoder = new Encoder(new FileOutputStream(playerDataFile));
            encoder.write(PLAYER_DATA_FORMAT_VERSION);
            encoder.writeInt(playerClaimBlocks.size());
            for(Map.Entry<UUID, Integer> entry : playerClaimBlocks.entrySet()) {
                encoder.writeUuid(entry.getKey());
                encoder.writeInt(entry.getValue());
            }
        }catch(IOException ex) {
            RegionProtection.error("Failed to save player data file: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Adds the given region to the lookup table. The region could have references under multiple keys if it is large
    // enough. The specified region will also add its child regions to the table recursively.
    private void addRegionToLookupTable(Region region, boolean includeChildren) {
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

        if(includeChildren)
            region.getChildren().forEach(r -> addRegionToLookupTable(r, false));
    }

    private void readdRegionToLookupTable(Region region, Pair<Location, Location> oldBounds) {
        // Remove the old claim from the lookup table
        for(int x = oldBounds.getFirst().getBlockX() >> 7;x <= oldBounds.getSecond().getBlockX() >> 7;++ x) {
            for(int z = oldBounds.getFirst().getBlockZ() >> 7;z <= oldBounds.getSecond().getBlockZ() >> 7;++ z) {
                lookupTable.get(region.getWorld().getUID()).get((((long)x) << 32) | ((long)z & 0xFFFFFFFFL))
                        .remove(region);
            }
        }

        // Re-add the claim to the lookup table
        addRegionToLookupTable(region, false);
    }

    // Object for storing world data
    private static class WorldData extends FlagContainer {
        UUID worldUid;
        final List<Region> regions;

        WorldData(UUID uuid) {
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
