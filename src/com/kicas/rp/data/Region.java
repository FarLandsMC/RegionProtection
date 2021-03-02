package com.kicas.rp.data;

import com.kicas.rp.data.flagdata.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.*;

/**
 * Contains the data pertaining to a region. Regions can have child regions as well. These child regions are, to some
 * extent, managed by the parent region in terms of serialization, deserialization, and deletion. Child regions also
 * have a priority greater than or equal to the parent region. If their priority is equal to the parent region, then
 * they will adopt the flags of the parent region and are updated whenever the parent's flags change.
 */
public class Region extends FlagContainer {
    // Can be null, such as in default claims
    private String name;
    private int priority;
    private final World world;
    private Location min, max;
    private Region parent;
    private boolean recentlyStolen;
    private final List<Region> children;

    // Copies the given location
    public Region(String name, int priority, UUID owner, Location min, Location max, Region parent, List<UUID> coOwners) {
        super(owner, coOwners);
        this.name = name;
        this.priority = priority;
        this.world = min.getWorld();
        this.min = min.clone();
        this.max = max.clone();
        this.parent = parent;
        this.recentlyStolen = false;
        this.children = new ArrayList<>();
    }

    // Create an admin region
    public Region(Location min, Location max) {
        this(null, 0, Utils.UUID_00, min, max, null, new ArrayList<>());
    }

    // For deserialization
    public Region(World world) {
        super(null);
        this.name = null;
        this.priority = 0;
        this.world = world;
        this.min = null;
        this.max = null;
        this.parent = null;
        this.children = new ArrayList<>();
    }

    // For child creation in deserialization
    public Region(Region parent, List<UUID> coOwners) {
        super(parent.getOwner(), coOwners);
        this.name = null;
        this.priority = 0;
        this.world = parent.getWorld();
        this.min = null;
        this.max = null;
        this.parent = parent;
        this.children = new ArrayList<>();
    }

    public String getRawName() {
        return name;
    }

    /**
     * Returns the display name of this region. If the actual name of the region is not null, then that string is
     * returned, otherwise "[Owned by &lt;ownerName&gt;]" is returned.
     *
     * @return the display name of this region.
     */
    public String getDisplayName() {
        return name == null || name.isEmpty() ? "[Owned by " + getOwnerName() + "]" : name;
    }

    /**
     * Sets this region's name to the given string.
     *
     * @param name the new name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return this region's numeric priority, with a higher number meaning a higher priority.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the region's numeric priority, with a higher number meaning a higher priority.
     *
     * @param priority the new priority.
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * @return the world this region is in.
     */
    public World getWorld() {
        return world;
    }

    /**
     * Set's the region's recently stolen status to a specific boolean
     *
     * @param recentlyStolen the value to set to
     */
    public void setRecentlyStolen(boolean recentlyStolen) {
        this.recentlyStolen = recentlyStolen;
    }

    /**
     * @return if the region has been stolen
     */
    public boolean isRecentlyStolen() {
        return recentlyStolen;
    }

    /**
     * @return a mutable list of this region's children.
     */
    public List<Region> getChildren() {
        return children;
    }

    /**
     * @return true if this region has a parent, false otherwise.
     */
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * Returns true if this region's parent is the specified region, or if the given region is a child of this region.
     *
     * @param other the other region.
     * @return true if this region and the given region are associated.
     */
    public boolean isAssociated(Region other) {
        return parent == null ? children.contains(other) : parent.equals(other);
    }

    /**
     * @return the parent of this region, or null if this region has no parent.
     */
    public Region getParent() {
        return parent;
    }

    /**
     * Sets this region's parent to the given region. This method does not check to ensure that the provided region can
     * be a valid parent to this region, and does not modify the child list of the given region.
     *
     * @param parent the parent region.
     */
    public void setParent(Region parent) {
        this.parent = parent;
    }

    /**
     * Sets the owner of this region to the given UUID, and also sets the owner of any child regions to the given UUID.
     *
     * @param owner the new owner.
     */
    @Override
    public void setOwner(UUID owner) {
        super.setOwner(owner);
        children.forEach(child -> child.setOwner(owner));
    }

    /**
     * Adds the given UUID as a co-owner of this region and any child regions, making them an effective owner.
     *
     * @param owner the new co-owner.
     */
    public void addCoOwner(UUID owner) {
        this.coOwners.add(owner);
        children.forEach(child -> child.addCoOwner(owner));
    }

    /**
     * Removes the given UUID from the list of co-owners in this region and all child regions.
     *
     * @param owner the co-owner to remove.
     * @return true if the co-owner was removed, false if not.
     */
    public boolean removeCoOwner(UUID owner) {
        if (!this.coOwners.remove(owner))
            return false;

        children.forEach(child -> child.removeCoOwner(owner));
        return true;
    }

    /**
     * Returns whether or not the given location is within this region in 3D space, including if the location is on the
     * edge of this region.
     *
     * @param loc the location.
     * @return true if the given location is in this region, false otherwise.
     */
    public boolean contains(Location loc) {
        return  min.getBlockX() <= loc.getBlockX() && loc.getBlockX() <= max.getBlockX() &&
                min.getBlockY() <= loc.getBlockY() && loc.getBlockY() <= max.getBlockY() &&
                min.getBlockZ() <= loc.getBlockZ() && loc.getBlockZ() <= max.getBlockZ();
    }

    /**
     * Returns whether or not the given location is within this region ignoring any restriction in the y-axis.
     *
     * @param loc the location.
     * @return true if the given location is in this region ignoring the y-axis, false otherwise.
     */
    public boolean containsIgnoreY(Location loc) {
        return  min.getBlockX() <= loc.getBlockX() && loc.getBlockX() <= max.getBlockX() &&
                min.getBlockZ() <= loc.getBlockZ() && loc.getBlockZ() <= max.getBlockZ();
    }

    /**
     * Returns whether or not the given region is completely within this region in 3D space.
     *
     * @param region the region.
     * @return true if the given region is completely within this region, false otherwise.
     */
    public boolean contains(Region region) {
        return contains(region.getMin()) && contains(region.getMax());
    }

    /**
     * Returns whether or not this region and the given region overlap in 3D space, meaning this region contains part,
     * or all of the given region.
     *
     * @param other the other region.
     * @return true if this region and the given region overlap, false otherwise.
     */
    public boolean overlaps(Region other) {
        return max.getBlockX() >= other.getMin().getBlockX() && min.getBlockX() <= other.max.getBlockX() &&
                max.getBlockY() >= other.getMin().getBlockY() && min.getBlockY() <= other.max.getBlockY() &&
                max.getBlockZ() >= other.getMin().getBlockZ() && min.getBlockZ() <= other.max.getBlockZ();
    }

    /**
     * Returns whether or not the given location is a corner of this region on the x-z plane excluding y values.
     *
     * @param loc the location.
     * @return true if the given location is a corner of this region, false otherwise.
     */
    public boolean isCorner(Location loc) {
        return (loc.getBlockX() == min.getBlockX() || loc.getBlockX() == max.getBlockX()) &&
                (loc.getBlockZ() == min.getBlockZ() || loc.getBlockZ() == max.getBlockZ());
    }

    /**
     * @return the minimum (more negative) vertex of this region.
     */
    public Location getMin() {
        return min;
    }

    /**
     * @return the maximum (more positive) vertex of this region.
     */
    public Location getMax() {
        return max;
    }

    /**
     * Returns the bounds of this region in a pair where the first item is the minimum vertex and the second is the
     * maximum.
     *
     * @return the bounds of this region.
     */
    @Override
    public Pair<Location, Location> getBounds() {
        return new Pair<>(min.clone(), max.clone());
    }

    /**
     * Returns the given location's shortest distance from an edge of this region, or 0 if the location is contained in
     * this region.
     *
     * @param location the location.
     * @return the given location's shortest distance from an edge of this region.
     */
    public double distanceFromEdge(Location location) {
        // There are nine areas to check: inside the region, and the other eight areas the result from extending the
        // borders of this region off to infinity.

        // Check for containment
        if (containsIgnoreY(location))
            return 0;

        // Check the diagonal areas that require less computation
        location = location.clone();
        location.setY(0);
        if (location.getX() < min.getX() && location.getZ() > max.getZ())
            return location.distance(new Location(min.getWorld(), min.getX(), 0, max.getZ()));
        if (location.getX() > max.getX() && location.getZ() < min.getZ())
            return location.distance(new Location(min.getWorld(), max.getX(), 0, min.getZ()));

        // Check the other two diagonal areas
        Location min0 = min.clone(), max0 = max.clone();
        min0.setY(0);
        max0.setY(0);
        if (location.getX() < min.getX() && location.getZ() < min.getZ())
            return location.distance(min0);
        if (location.getX() > max.getX() && location.getZ() > max.getZ())
            return location.distance(max0);

        // Check the other four areas aligned with this region
        if (location.getX() > min.getX() && location.getX() < max.getX())
            return location.getZ() < min.getZ() ? min.getZ() - location.getZ() : location.getZ() - max.getZ();
        if (location.getZ() > min.getZ() && location.getZ() < max.getZ())
            return location.getX() < min.getX() ? min.getX() - location.getX() : location.getX() - max.getX();

        // This is an unreachable statement
        return Double.MAX_VALUE;
    }

    /**
     * Sets the bounds of this region by copying the given minimum and maximum vertices respectively.
     *
     * @param bounds the bounds of the region. Minimum and maximum vertex respectively.
     */
    @Override
    public void setBounds(Pair<Location, Location> bounds) {
        min = bounds.getFirst().clone();
        max = bounds.getSecond().clone();
    }

    /**
     * Moves any vertex of this claim to the new, given location.
     *
     * @param originalVertex the original vertex location.
     * @param newVertex      the new vertex location.
     */
    public void moveVertex(Location originalVertex, Location newVertex) {
        // Update x
        if (min.getBlockX() == originalVertex.getBlockX())
            min.setX(newVertex.getX());
        else
            max.setX(newVertex.getX());

        // Update z
        if (min.getBlockZ() == originalVertex.getBlockZ())
            min.setZ(newVertex.getBlockZ());
        else
            max.setZ(newVertex.getBlockZ());

        // Handle the extraneous case where the player moves the vertex so far that the relative location of the vertex
        // to the other four changes, changing the min and max
        reevaluateBounds();
    }

    /**
     * Re-evaluates the minimum and maximum locations of the region. This method should be called if the region could
     * have been contorted to such an extent that the original minimum or maximum location is not anymore.
     */
    public void reevaluateBounds() {
        Location oldMin = min.clone();
        min.setX(Math.min(min.getX(), max.getX()));
        min.setZ(Math.min(min.getZ(), max.getZ()));
        max.setX(Math.max(oldMin.getX(), max.getX()));
        max.setZ(Math.max(oldMin.getZ(), max.getZ()));
    }

    /**
     * Returns the x-z planar cross-sectional area of this region (length * width).
     *
     * @return the area of this region.
     */
    public long area() {
        return (long) (1 + max.getBlockX() - min.getBlockX()) * (long) (1 + max.getBlockZ() - min.getBlockZ());
    }

    /**
     * Returns whether or not the time since the last login of a trustee with at least container trust is greater than
     * the given time in milliseconds. Admin owned claims will always return false when then method is called.
     *
     * @param expirationTime the expiration time.
     * @return true if the condition specified above is met.
     */
    public boolean hasExpired(long expirationTime) {
        // Obviously exempt admin regions
        if (isAdminOwned() || expirationTime == 0)
            return false;

        // Find the most recent login
        Map<TrustLevel, List<UUID>> trustList = this.<TrustMeta>getFlagMeta(RegionFlag.TRUST).getTrustList();
        long mostRecentLogin = 0;
        for (Map.Entry<TrustLevel, List<UUID>> entry : trustList.entrySet()) {
            // Trust check
            if (!entry.getKey().isAtLeast(TrustLevel.CONTAINER))
                continue;

            for (UUID uuid : entry.getValue()) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                if (offlinePlayer.isOnline())
                    return false;
                if (offlinePlayer.getLastPlayed() > mostRecentLogin)
                    mostRecentLogin = offlinePlayer.getLastPlayed();
            }
        }

        // Check co-owners
        for (UUID coOwner : coOwners) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(coOwner);
            if (offlinePlayer.isOnline())
                return false;
            if (offlinePlayer.getLastPlayed() > mostRecentLogin)
                mostRecentLogin = offlinePlayer.getLastPlayed();
        }

        // Don't forget the owner
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(owner);
        if (offlinePlayer.isOnline())
            return false;
        if (offlinePlayer.getLastPlayed() > mostRecentLogin)
            mostRecentLogin = offlinePlayer.getLastPlayed();

        // Perform the check
        return (System.currentTimeMillis() - mostRecentLogin) > expirationTime;
    }

    /**
     * Sets the flag of this region and child regions of the same priority to the given value.
     *
     * @param flag the flag.
     * @param meta the value.
     */
    @Override
    public void setFlag(RegionFlag flag, Object meta) {
        super.setFlag(flag, meta);
        children.stream().filter(region -> priority == region.getPriority())
                .forEach(region -> region.setFlag(flag, meta));
    }

    /**
     * Returns true if and only if this region and the given object are the same object. This method is equivalent to
     * using the == operator.
     *
     * @param other the object to test.
     * @return true if and only if this region and the given object are the same object.
     */
    @Override
    public boolean equals(Object other) {
        return other == this;
    }
}
