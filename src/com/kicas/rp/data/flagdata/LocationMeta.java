package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Allows a minecraft location (including its world and rotation values) to be stored. Used for the respawn-location
 * flag.
 */
public class LocationMeta {
    private UUID world;
    private double x, y, z;
    private float yaw, pitch;

    public LocationMeta(UUID uuid, double x, double y, double z, float yaw, float pitch) {
        this.world = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public LocationMeta(World world, double x, double y, double z, float yaw, float pitch) {
        this(world.getUID(), x, y, z, yaw, pitch);
    }

    public LocationMeta(World world, double x, double y, double z) {
        this(world.getUID(), x, y, z, 0, 0);
    }

    public LocationMeta(Location loc) {
        this(loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    public LocationMeta() {
        this.world = null;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.yaw = 0F;
        this.pitch = 0F;
    }

    public Location getLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return Utils.doubleToString(x, 3) + "x, " + Utils.doubleToString(y, 3) + "y, " + Utils.doubleToString(z, 3) +
                "z (" + Bukkit.getWorld(world).getName() + ")";
    }
}
