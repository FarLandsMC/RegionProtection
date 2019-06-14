package com.kicas.rp.data;

import com.kicas.rp.util.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Contains the data pertaining to a region. Regions can have child regions as well. These child regions are to some
 * extent managed by the parent region in terms of serialization, deserialization, and deletion. Child regions also have
 * a priority greater than or equal to the parent region. If their priority is equal to the parent region, then they
 * will adopt the flags of the parent region and are updated whenever the parent's flags change.
 */
public class Region extends FlagContainer implements Serializable {
    // Can be null, such as in claims
    private String name;
    private int priority;
    private World world;
    private Location min, max;
    private Region parent;
    private final List<Region> children;

    // Copies the given location
    public Region(String name, int priority, UUID owner, Location min, Location max, Region parent) {
        super(owner);
        this.name = name;
        this.priority = priority;
        this.world = min.getWorld();
        this.min = min.clone();
        this.max = max.clone();
        this.parent = parent;
        this.children = new ArrayList<>();
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
    Region(Region parent) {
        super(parent.getOwner());
        this.name = null;
        this.priority = 0;
        this.world = parent.getMin().getWorld();
        this.min = null;
        this.max = null;
        this.parent = parent;
        this.children = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public World getWorld() {
        return world;
    }

    /**
     * Returns a mutable list of this region's children.
     * @return a mutable list of this region's children.
     */
    public List<Region> getChildren() {
        return children;
    }

    public boolean hasParent() {
        return parent != null;
    }

    /**
     * Returns true if this region's parent is the specified region, or if the given region is a child of this region.
     * @param other the other region.
     * @return true if this region and the given region are associated.
     */
    public boolean isAssociated(Region other) {
        return parent == null ? children.contains(other) : parent.equals(other);
    }

    public Region getParent() {
        return parent;
    }

    public void setParent(Region parent) {
        this.parent = parent;
    }

    /**
     * Returns whether or not the given location is within this region in 3D space, including if the location is on the
     * edge of this region.
     * @param loc the location.
     * @return true if the given location is in this region, false otherwise.
     */
    public boolean contains(Location loc) {
        return loc.getX() >= min.getX() && loc.getY() >= min.getY() && loc.getZ() >= min.getZ() &&
                loc.getX() <= max.getX() && loc.getY() <= max.getY() && loc.getZ() <= max.getZ();
    }

    /**
     * Returns whether or not the given region is completely within this region in 3D space.
     * @param region the region.
     * @return true if the given region is completely within this region, false otherwise.
     */
    public boolean contains(Region region) {
        return contains(region.getMin()) && contains(region.getMax());
    }

    /**
     * Returns whether or not this region and the given region overlap in 3D space, mening this region contains part,
     * or all of the given region.
     * @param other the other region.
     * @return true if this region and the given region overlap, false otherwise.
     */
    public boolean overlaps(Region other) {
        return max.getBlockX() >= other.getMin().getBlockX() && min.getBlockX() <= other.max.getBlockX() &&
                max.getBlockY() >= other.getMin().getBlockY() && min.getBlockY() <= other.max.getBlockY() &&
                max.getBlockZ() >= other.getMin().getBlockZ() && min.getBlockZ() <= other.max.getBlockZ();
    }

    public void expand(BlockFace direction, int amount) {
        switch(direction) {
            case UP:
                max.setY(max.getY() + amount);
                break;
            case DOWN:
                min.setY(min.getY() - amount);
                break;
            case NORTH:
                min.setZ(min.getZ() - amount);
                break;
            case SOUTH:
                max.setZ(max.getZ() + amount);
                break;
            case EAST:
                max.setX(max.getX() + amount);
                break;
            case WEST:
                min.setX(min.getX() - amount);
                break;
        }
    }

    /**
     * Returns whether or not the given location is a corner of this region on the x-z plane excluding y values.
     * @param loc the location.
     * @return true if the given location is a corner of this region, false otherwise.
     */
    public boolean isCorner(Location loc) {
        return (loc.getBlockX() == min.getBlockX() || loc.getBlockX() == max.getBlockX()) &&
                (loc.getBlockZ() == min.getBlockZ() || loc.getBlockZ() == max.getBlockZ());
    }

    public Location getMin() {
        return min;
    }

    public Location getMax() {
        return max;
    }

    /**
     * Returns the bounds of this region in a pair where the first item is the minimum vertex and the second is the
     * maximum.
     * @return the bounds of this region.
     */
    public Pair<Location, Location> getBounds() {
        return new Pair<>(min.clone(), max.clone());
    }

    /**
     * Returns the given location's shortest distance from an edge of this region, or 0 if the location is contained in
     * this region.
     * @param location the location.
     * @return the given location's shortest distance from an edge of this region.
     */
    public double distanceFromEdge(Location location) {
        // There are nine areas to check: inside the region, and the other eight areas the result from extending the
        // borders of this region off to infinity.

        // Check for containment
        if(contains(location))
            return 0;

        // Check the diagonal areas that require less computation
        location = location.clone();
        location.setY(0);
        if(location.getX() < min.getX() && location.getZ() > max.getZ())
            return location.distance(new Location(min.getWorld(), min.getX(), 0, max.getZ()));
        if(location.getX() > max.getX() && location.getZ() < min.getZ())
            return location.distance(new Location(min.getWorld(), max.getX(), 0, min.getZ()));

        // Check the other two diagonal areas
        Location min0 = min.clone(), max0 = max.clone();
        min0.setY(0);
        max0.setY(0);
        if(location.getX() < min.getX() && location.getZ() < min.getZ())
            return location.distance(min0);
        if(location.getX() > max.getX() && location.getZ() > max.getZ())
            return location.distance(max0);

        // Check the other four areas aligned with this region
        if(location.getX() > min.getX() && location.getX() < max.getX())
            return location.getZ() < min.getZ() ? min.getZ() - location.getZ() : location.getZ() - max.getZ();
        if(location.getZ() > min.getZ() && location.getZ() < max.getZ())
            return location.getX() < min.getX() ? min.getX() - location.getX() : location.getX() - max.getX();

        // This is an unreachable statement
        return Double.MAX_VALUE;
    }

    /**
     * Sets the bounds of this region by copying the given minimum and maximum vertices respectively.
     * @param bounds the bounds of the region. Minimum and maximum vertex respectively.
     */
    public void setBounds(Pair<Location, Location> bounds) {
        min = bounds.getFirst().clone();
        max = bounds.getSecond().clone();
    }

    /**
     * Moves any vertex of this claim to the new, given location.
     * @param originalVertex the original vertex location.
     * @param newVertex the new vertex location.
     */
    public void moveVertex(Location originalVertex, Location newVertex) {
        // Update x
        if(min.getBlockX() == originalVertex.getBlockX())
            min.setX(newVertex.getX());
        else
            max.setX(newVertex.getX());

        // Update z
        if(min.getBlockZ() == originalVertex.getBlockZ())
            min.setZ(newVertex.getBlockZ());
        else
            max.setZ(newVertex.getBlockZ());

        // Handle the extraneous case where the player moves the vertex so far that the relative location of the vertex
        // to the other four changes, changing the min and max
        Location oldMin = min.clone();
        min.setX(Math.min(min.getX(), max.getX()));
        min.setZ(Math.min(min.getZ(), max.getZ()));
        max.setX(Math.max(oldMin.getX(), max.getX()));
        max.setZ(Math.max(oldMin.getZ(), max.getZ()));
    }

    /**
     * Returns the x-z planar cross-sectional area of this region (length * width).
     * @return the area of this region.
     */
    public long area() {
        return (long)(max.getBlockX() - min.getBlockX()) * (long)(max.getBlockZ() - min.getBlockZ());
    }

    /**
     * Sets the flag of this region and child regions of the same priority to the given value.
     * @param flag the flag.
     * @param allow the value.
     */
    @Override
    public void setFlag(RegionFlag flag, boolean allow) {
        super.setFlag(flag, allow);
        children.stream().filter(region -> priority == region.getPriority())
                .forEach(region -> region.setFlag(flag, allow));
    }

    /**
     * Sets the flag of this region and child regions of the same priority to the given value.
     * @param flag the flag.
     * @param meta the value.
     */
    @Override
    public void setFlag(RegionFlag flag, Object meta) {
        super.setFlag(flag, meta);
        children.stream().filter(region -> priority == region.getPriority())
                .forEach(region -> region.setFlag(flag, meta));
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeUTF8Raw(name == null ? "" : name);
        encoder.write(priority);
        if(parent == null)
            encoder.writeUuid(owner);

        encoder.writeInt(min.getBlockX());
        encoder.writeInt(min.getBlockY());
        encoder.writeInt(min.getBlockZ());
        encoder.writeInt(max.getBlockX());
        encoder.writeInt(max.getBlockY());
        encoder.writeInt(max.getBlockZ());

        super.serialize(encoder); // flags

        if(parent == null) {
            encoder.writeInt(children.size());
            for(Region region : children)
                region.serialize(encoder);
        }
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        name = decoder.readUTF8Raw();
        priority = decoder.read();
        if(parent == null)
            owner = decoder.readUuid();

        min = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());
        max = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());

        super.deserialize(decoder); // flags

        if(parent == null) {
            int len = decoder.readInt();
            while(len > 0) {
                Region assoc = new Region(this);
                assoc.deserialize(decoder);
                children.add(assoc);
                -- len;
            }
        }
    }
}
