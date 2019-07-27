package com.kicas.rp.data;

import com.kicas.rp.data.flagdata.*;
import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Pair;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Deserializes plugin data files, including regions.dat and playerdata.dat.
 */
// TODO: Implement a system for reading older format versions and add a corruption detection system
public class Deserializer implements AutoCloseable {
    private final Decoder decoder;
    private final int expectedFormatVersion;

    public Deserializer(File file, int expectedFormatVersion) throws IOException {
        this.decoder = new Decoder(new FileInputStream(file));
        this.expectedFormatVersion = expectedFormatVersion;
    }

    /**
     * Translates the data in the file in the given constructor to region data and flag data.
     *
     * @return a map where the keys are Bukkit world UIDs and te values are the corresponding world data.
     * @throws IOException if and I/O error occurs.
     */
    public Map<UUID, WorldData> readWorldData() throws IOException {
        // Check the format version
        int format = decoder.read();
        if (format != expectedFormatVersion)
            throw new IllegalStateException("Expected format version " + expectedFormatVersion + ". Found " + format);

        // Read each of the individual world data blocks
        int len = decoder.read();
        Map<UUID, WorldData> worldData = new HashMap<>(len);
        while (len > 0) {
            // Read the global flags
            UUID worldUid = decoder.readUuid();
            WorldData wd = new WorldData(worldUid);
            readFlags(wd);

            // Read the parent regions
            World world = Bukkit.getWorld(worldUid);
            int regionCount = decoder.readCompressedUint();
            while (regionCount > 0) {
                wd.getRegions().add(readParentRegion(world));
                --regionCount;
            }

            worldData.put(worldUid, wd);
            --len;
        }

        decoder.close();

        return worldData;
    }

    /**
     * Translates the data in the file in the given constructor to region data and flag data.
     *
     * @return a map where the keys are player UUIDs and the values are corresponding persistent player data objects.
     * @throws IOException if an I/O error occurs.
     */
    public Map<UUID, PersistentPlayerData> readPlayerData() throws IOException {
        // Check the format version
        int format = decoder.read();
        if (format != expectedFormatVersion)
            throw new IllegalStateException("Expected format version " + expectedFormatVersion + ". Found " + format);

        // Read each individual player data object
        int len = decoder.readCompressedUint();
        Map<UUID, PersistentPlayerData> playerData = new HashMap<>(len);
        while (len > 0) {
            UUID uuid = decoder.readUuid();
            playerData.put(uuid, new PersistentPlayerData(uuid, decoder.readCompressedUint()));
            --len;
        }

        return playerData;
    }

    /**
     * Reads a parent region including its children and sets the region's world to the given world.
     *
     * @param world the region's world.
     * @return the parent region.
     * @throws IOException if an I/O error occurs.
     */
    private Region readParentRegion(World world) throws IOException {
        String name = decoder.readUTF8Raw();
        // Contains the priority, and the sign bit is whether or not the region is administrator-owned
        int meta = decoder.read();

        Region region = new Region(name, meta & 0x7F, (meta & 0x80) != 0 ? Utils.UUID_00 : decoder.readUuid(),
                readRegionBound(world), readRegionBound(world), null);

        readFlags(region);
        int len = decoder.readCompressedUint();
        while (len > 0) {
            region.getChildren().add(readChildRegion(region));
            --len;
        }

        return region;
    }

    /**
     * Reads a child region and infers missing fields from the given parent region.
     *
     * @param parent the child region's parent.
     * @return the child region.
     * @throws IOException if an I/O error occurs.
     */
    private Region readChildRegion(Region parent) throws IOException {
        Region region = new Region(decoder.readUTF8Raw(), decoder.read() & 0x7F, parent.getOwner(),
                readRegionBound(parent.getWorld()), readRegionBound(parent.getWorld()), parent);
        readFlags(region);
        return region;
    }

    /**
     * Reads a location with the given world.
     *
     * @param world the location's world.
     * @return the location.
     * @throws IOException if an I/O error occurs.
     */
    private Location readRegionBound(World world) throws IOException {
        return new Location(world, decoder.readCompressedInt(), decoder.read(), decoder.readCompressedInt());
    }

    /**
     * Reads a number of flag-metadata key-value pairs and sets the given container's flags to the read values.
     *
     * @param container the flag container to add flags to.
     * @throws IOException if an I/O error occurs.
     */
    private void readFlags(FlagContainer container) throws IOException {
        int len = decoder.readCompressedUint();
        Map<RegionFlag, Object> flags = new HashMap<>(len);
        while (len > 0) {
            Pair<RegionFlag, Object> flag = readFlag();
            flags.put(flag.getFirst(), flag.getSecond());
            --len;
        }

        container.setFlags(flags);
    }

    /**
     * Reads an individual flag and its metadata.
     *
     * @return a pair with the read flag and its metadata.
     * @throws IOException if an I/O error occurs.
     */
    private Pair<RegionFlag, Object> readFlag() throws IOException {
        RegionFlag flag = readFlagEnum();

        // Reads the flag meta value. Note: perhaps make these individual functions
        Object meta;
        if (flag.isBoolean())
            meta = decoder.readBoolean();
        else if (CommandMeta.class.equals(flag.getMetaClass()))
            meta = new CommandMeta(decoder.readBoolean(), decoder.readUTF8Raw());
        else if (EnumFilter.class.equals(flag.getMetaClass())) {
            boolean isWhitelist = decoder.readBoolean();
            List<Integer> filter = new ArrayList<>();
            int len = decoder.readCompressedUint();
            while (len > 0) {
                filter.add(decoder.readCompressedUint());
                --len;
            }
            meta = new EnumFilter(isWhitelist, filter);
        } else if (LocationMeta.class.equals(flag.getMetaClass())) {
            meta = new LocationMeta(decoder.readUuid(), decoder.readDouble(), decoder.readDouble(),
                    decoder.readDouble(), decoder.readFloat(), decoder.readFloat());
        } else if (StringFilter.class.equals(flag.getMetaClass()))
            meta = new StringFilter(decoder.readBoolean(), decoder.readArrayAsList(String.class));
        else if (TextMeta.class.equals(flag.getMetaClass()))
            meta = new TextMeta(decoder.readUTF8Raw());
        else if (TrustMeta.class.equals(flag.getMetaClass())) {
            TrustMeta trustMeta = new TrustMeta(TrustLevel.VALUES[decoder.read()]);
            int len = decoder.readCompressedUint();
            while (len > 0) {
                trustMeta.trust(decoder.readUuid(), TrustLevel.VALUES[decoder.read()]);
                --len;
            }
            meta = trustMeta;
        } else
            throw new InternalError("Invalid flag meta class: " + flag.getMetaClass().getName());

        return new Pair<>(flag, meta);
    }

    /**
     * Reads a flag.
     *
     * @return the read flag.
     * @throws IOException if an I/O error occurs.
     */
    private RegionFlag readFlagEnum() throws IOException {
        return RegionFlag.VALUES[decoder.readCompressedUint()];
    }

    /**
     * Closes the decoder in this deserializer.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        decoder.close();
    }
}
