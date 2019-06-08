package com.kicas.rp.data;

import com.kicas.rp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Region implements Serializable {
    private int id;
    private int priority;
    private ExtendedUuid owner;
    private World world;
    private Location min, max;
    private final Map<RegionFlag, Serializable> flags;

    public Region(int id, int priority, ExtendedUuid owner, Location min, Location max) {
        this.id = id;
        this.priority = priority;
        this.owner = owner;
        this.world = min.getWorld();
        this.min = min;
        this.max = max;
        this.flags = new HashMap<>();
    }

    public Region() {
        this(0, 0, null, null, null);
    }

    public int getId() {
        return id;
    }

    public boolean contains(Location loc) {
        return loc.getX() > min.getX() && loc.getY() > min.getY() && loc.getZ() > min.getZ() &&
                loc.getX() < max.getX() && loc.getY() < max.getY() && loc.getZ() < max.getZ();
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

    public boolean isFlagSet(RegionFlag flag) {
        return flags.containsKey(flag);
    }

    public void setFlag(RegionFlag flag) {
        flags.put(flag, null);
    }

    public void setFlag(RegionFlag flag, Serializable meta) {
        flags.put(flag, meta);
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getFlagMeta(RegionFlag flag) {
        return (T)flags.get(flag);
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeInt(id);
        encoder.write(priority);
        owner.serialize(encoder);
        encoder.writeUuid(world.getUID());
        encoder.writeInt(min.getBlockX());
        encoder.writeInt(min.getBlockY());
        encoder.writeInt(min.getBlockZ());
        encoder.writeInt(max.getBlockX());
        encoder.writeInt(max.getBlockY());
        encoder.writeInt(max.getBlockZ());
        encoder.writeInt(flags.size());
        for(Map.Entry<RegionFlag, Serializable> entry : flags.entrySet()) {
            encoder.write(entry.getKey().ordinal());
            if(entry.getKey().hasMeta())
                entry.getValue().serialize(encoder);
        }
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        id = decoder.readInt();
        priority = decoder.read();
        owner = new ExtendedUuid();
        owner.deserialize(decoder);
        world = Bukkit.getWorld(decoder.readUuid());
        min = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());
        max = new Location(world, decoder.readInt(), decoder.readInt(), decoder.readInt());
        int len = decoder.readInt();
        while(len > 0) {
            RegionFlag flag = RegionFlag.VALUES[decoder.read()];
            Serializable meta = null;
            if(flag.hasMeta()) {
                meta = ReflectionHelper.instantiate(flag.getMetaClass());
                meta.deserialize(decoder);
            }
            flags.put(flag, meta);
        }
    }
}
