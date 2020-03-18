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
public class LocationMeta extends FlagMeta {
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

    /**
     * @return a new Bukkit location based on the data in this meta class.
     */
    public Location getLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    /**
     * Updates this location meta based on the given string. Valid inputs include:
     * <ul>
     *     <li>x y z</li>
     *     <li>x y z world</li>
     *     <li>x y z yaw pitch</li>
     *     <li>x y z yaw pitch world</li>
     * </ul>
     *
     * If no world is provided, the parser will default to the overworld.
     *
     * @param metaString the metadata in string form.
     */
    @Override
    public void readMetaString(String metaString) {
        String[] args = metaString.split(" ");

        if (args.length == 1)
            throw new IllegalArgumentException("Please provide a y and z value.");
        else if (args.length == 2)
            throw new IllegalArgumentException("Please provide a z value.");

        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Failed to parse coordinate values.");
        }

        if (args.length <= 4) {
            World world = Bukkit.getWorld(args.length == 4 ? Utils.getWorldName(args[3]) : "world");
            if (world == null)
                throw new IllegalArgumentException("Invalid world name: " + args[3]);
            this.world = world.getUID();
            return;
        }

        try {
            yaw = Float.parseFloat(args[3]);
            pitch = Float.parseFloat(args[4]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Failed to parse rotation values.");
        }

        World world = Bukkit.getWorld(args.length == 6 ? Utils.getWorldName(args[5]) : "world");
        if (world == null)
            throw new IllegalArgumentException("Invalid world name: " + args[5]);
        this.world = world.getUID();
    }

    /**
     * Converts this location meta to a string form. The resulting string will only contain the x, y, and z parts of
     * this meta and each number will be truncated to three decimal points.
     *
     * @return this location meta in string form.
     */
    @Override
    public String toMetaString() {
        return Utils.doubleToString(x, 3) + "x, " + Utils.doubleToString(y, 3) + "y, " + Utils.doubleToString(z, 3) +
                "z (" + Bukkit.getWorld(world).getName() + ")";
    }
}
