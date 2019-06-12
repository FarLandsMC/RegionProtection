package com.kicas.rp.data;

import com.kicas.rp.util.*;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Region extends FlagContainer implements Serializable {
    private String name;
    private int priority;
    private World world;
    private Location min, max;
    private Region association;
    private final List<Region> associatedRegions;

    public Region(String name, int priority, UUID owner, Location min, Location max, Region association) {
        super(owner);
        this.name = name;
        this.priority = priority;
        this.world = min.getWorld();
        this.min = min.clone();
        this.max = max.clone();
        this.association = association;
        this.associatedRegions = new ArrayList<>();
    }

    public Region(World world) {
        super((UUID)null);
        this.name = null;
        this.priority = 0;
        this.world = world;
        this.min = null;
        this.max = null;
        this.association = null;
        this.associatedRegions = new ArrayList<>();
    }

    public Region(Region association) {
        super(association.getOwner());
        this.name = null;
        this.priority = 0;
        this.world = association.getMin().getWorld();
        this.min = null;
        this.max = null;
        this.association = association;
        this.associatedRegions = new ArrayList<>();
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

    public List<Region> getAssociatedRegions() {
        return associatedRegions;
    }

    public boolean hasAssociation() {
        return association != null;
    }

    public boolean isAssociated(Region other) {
        return association == null ? associatedRegions.contains(other) : association.equals(other);
    }

    public Region getAssociation() {
        return association;
    }

    public void setAssociation(Region association) {
        this.association = association;
    }

    public boolean contains(Location loc) {
        return loc.getX() >= min.getX() && loc.getY() >= min.getY() && loc.getZ() >= min.getZ() &&
                loc.getX() <= max.getX() && loc.getY() <= max.getY() && loc.getZ() <= max.getZ();
    }

    public boolean contains(Region region) {
        return contains(region.getMin()) && contains(region.getMax());
    }

    public boolean intersects(Region other) {
        return max.getBlockX() >= other.getMin().getBlockX() && min.getBlockX() <= other.max.getBlockX() &&
                max.getBlockY() >= other.getMin().getBlockY() && min.getBlockY() <= other.max.getBlockY() &&
                max.getBlockZ() >= other.getMin().getBlockZ() && min.getBlockZ() <= other.max.getBlockZ();
    }

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

    public Pair<Location, Location> getBounds() {
        return new Pair<>(min.clone(), max.clone());
    }

    public double distanceFromEdge(Location location) {
        if(contains(location))
            return 0;
        location = location.clone();
        location.setY(0);
        if(location.getX() < min.getX() && location.getZ() > max.getZ())
            return location.distance(new Location(min.getWorld(), min.getX(), 0, max.getZ()));
        if(location.getX() > max.getX() && location.getZ() < min.getZ())
            return location.distance(new Location(min.getWorld(), max.getX(), 0, min.getZ()));
        Location min0 = min.clone(), max0 = max.clone();
        min0.setY(0);
        max0.setY(0);
        if(location.getX() < min.getX() && location.getZ() < min.getZ())
            return location.distance(min0);
        if(location.getX() > max.getX() && location.getZ() > max.getZ())
            return location.distance(max0);
        if(location.getX() > min.getX() && location.getX() < max.getX())
            return location.getZ() < min.getZ() ? min.getZ() - location.getZ() : location.getZ() - max.getZ();
        if(location.getZ() > min.getZ() && location.getZ() < max.getZ())
            return location.getX() < min.getX() ? min.getX() - location.getX() : location.getX() - max.getX();
        return Double.MAX_VALUE;
    }

    public void setBounds(Pair<Location, Location> bounds) {
        min = bounds.getFirst().clone();
        max = bounds.getSecond().clone();
    }

    public void moveVertex(Location from, Location to) {
        if(min.getBlockX() == from.getBlockX())
            min.setX(to.getX());
        else
            max.setX(to.getX());
        if(min.getBlockZ() == from.getBlockZ())
            min.setZ(to.getBlockZ());
        else
            max.setZ(to.getBlockZ());
        Location oldMin = min.clone();
        min.setX(Math.min(min.getX(), max.getX()));
        min.setZ(Math.min(min.getZ(), max.getZ()));
        max.setX(Math.max(oldMin.getX(), max.getX()));
        max.setZ(Math.max(oldMin.getZ(), max.getZ()));
    }

    public long area() {
        return (long)(max.getBlockX() - min.getBlockX()) * (long)(max.getBlockZ() - min.getBlockZ());
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeUTF8Raw(name == null ? "" : name);
        encoder.write(priority);
        if(association == null)
            encoder.writeUuid(owner);
        encoder.writeInt(min.getBlockX());
        encoder.writeInt(min.getBlockY());
        encoder.writeInt(min.getBlockZ());
        encoder.writeInt(max.getBlockX());
        encoder.writeInt(max.getBlockY());
        encoder.writeInt(max.getBlockZ());
        super.serialize(encoder);
        if(association == null) {
            encoder.writeInt(associatedRegions.size());
            for(Region region : associatedRegions)
                region.serialize(encoder);
        }
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        name = decoder.readUTF8Raw();
        priority = decoder.read();
        if(association == null)
            owner = decoder.readUuid();
        min = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());
        max = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());
        super.deserialize(decoder);
        if(association == null) {
            int len = decoder.readInt();
            while(len > 0) {
                Region assoc = new Region(this);
                assoc.deserialize(decoder);
                associatedRegions.add(assoc);
                -- len;
            }
        }
    }
}
