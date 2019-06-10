package com.kicas.rp.data;

import com.kicas.rp.util.*;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.util.Map;

public class Region extends FlagContainer implements Serializable {
    private String name;
    private int priority;
    private ExtendedUuid owner;
    private World world;
    private Location min, max;

    public Region(String name, int priority, ExtendedUuid owner, Location min, Location max) {
        this.name = name;
        this.priority = priority;
        this.owner = owner;
        this.world = min.getWorld();
        this.min = min.clone();
        this.max = max.clone();
    }

    public Region(World world) {
        this.name = null;
        this.priority = 0;
        this.owner = null;
        this.world = world;
        this.min = null;
        this.max = null;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public ExtendedUuid getOwner() {
        return owner;
    }

    public boolean isAdminOwned() {
        return owner.isAdmin();
    }

    public boolean contains(Location loc) {
        return loc.getX() >= min.getX() && loc.getY() >= min.getY() && loc.getZ() >= min.getZ() &&
                loc.getX() <= max.getX() && loc.getY() <= max.getY() && loc.getZ() <= max.getZ();
    }

    public boolean intersects(Region other) {
        return max.getBlockX() >= other.getMin().getBlockX() && min.getBlockX() <= other.max.getBlockX() &&
                max.getBlockY() >= other.getMin().getBlockY() && min.getBlockY() <= other.max.getBlockY() &&
                max.getBlockZ() >= other.getMin().getBlockZ() && min.getBlockZ() <= other.max.getBlockZ();
    }

    public Location getMin() {
        return min;
    }

    public Location getMax() {
        return max;
    }

    public void resetVertex(Location newVertex) {
        if(newVertex.getX() < min.getX())
            min.setX(newVertex.getX());
        else
            max.setX(newVertex.getX());
        if(newVertex.getY() < min.getY())
            min.setY(newVertex.getY());
        else
            max.setY(newVertex.getY());
        if(newVertex.getZ() < min.getZ())
            min.setZ(newVertex.getZ());
        else
            max.setZ(newVertex.getZ());
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeUTF8Raw(name == null ? "" : name);
        encoder.write(priority);
        owner.serialize(encoder);
        encoder.writeInt(min.getBlockX());
        encoder.writeInt(min.getBlockY());
        encoder.writeInt(min.getBlockZ());
        encoder.writeInt(max.getBlockX());
        encoder.writeInt(max.getBlockY());
        encoder.writeInt(max.getBlockZ());
        encoder.writeInt(flags.size());
        for(Map.Entry<RegionFlag, Object> entry : flags.entrySet()) {
            encoder.write(entry.getKey().ordinal());
            if(entry.getKey().isBoolean())
                encoder.writeBoolean((boolean)entry.getValue());
            else
                ((Serializable)entry.getValue()).serialize(encoder);
        }
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        name = decoder.readUTF8Raw();
        priority = decoder.read();
        owner = new ExtendedUuid();
        owner.deserialize(decoder);
        min = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());
        max = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());
        int len = decoder.readInt();
        while(len > 0) {
            RegionFlag flag = RegionFlag.VALUES[decoder.read()];
            Object meta;
            if(flag.isBoolean())
                meta = decoder.readBoolean();
            else{
                meta = ReflectionHelper.instantiate(flag.getMetaClass());
                ((Serializable)meta).deserialize(decoder);
            }
            flags.put(flag, meta);
        }
    }
}
