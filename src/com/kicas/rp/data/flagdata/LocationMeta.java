package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.util.UUID;

public class LocationMeta implements Serializable {
    private UUID world;
    private double x, y, z;
    private float yaw, pitch;

    public LocationMeta(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world.getUID();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public LocationMeta(World world, double x, double y, double z) {
        this(world, x, y, z, 0, 0);
    }

    public LocationMeta(Location loc) {
        this(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    public LocationMeta() {
        this(null, 0, 0, 0, 0F, 0F);
    }

    public Location getLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeUuid(world);
        encoder.writeDouble(x);
        encoder.writeDouble(y);
        encoder.writeDouble(z);
        encoder.writeFloat(yaw);
        encoder.writeFloat(pitch);
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        world = decoder.readUuid();
        x = decoder.readDouble();
        y = decoder.readDouble();
        z = decoder.readDouble();
        yaw = decoder.readFloat();
        pitch = decoder.readFloat();
    }

    @Override
    public String toString() {
        return Utils.doubleToString(x, 3) + "x, " + Utils.doubleToString(y, 3) + "y, " + Utils.doubleToString(z, 3) +
                "z (" + Bukkit.getWorld(world).getName() + ")";
    }
}
