package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
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
 * Deserializes plugin data files, including regions.dat and playerdata.dat. The methods and order of read operations in
 * this class are meant to mirror those in the Serializer class. The Serializer class is considered to be "always right"
 * and this class must conform to it and previous versions of it.
 */
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
        if (format < expectedFormatVersion)
            RegionProtection.log("Decoding older data format version (" + format + ") for world data file.");
        else if (format > expectedFormatVersion)
            fail("Invalid format encountered. Please make sure you are using the most recent version of the plugin.");

        Map<UUID, WorldData> worldData = readWorldData(format);
        decoder.close();
        return worldData;
    }

    // Wrapped, format-specific deserialization method
    private Map<UUID, WorldData> readWorldData(int format) throws IOException {
        // Read each of the individual world data blocks
        int len = decoder.read();
        Map<UUID, WorldData> worldData = new HashMap<>(len);
        while (len > 0) {
            if (decoder.isAtEndOfStream())
                failEOF();

            // Read the global flags
            UUID worldUid = decoder.readUuid();
            WorldData wd = new WorldData(worldUid);
            readFlags(wd, format);

            // Read the parent regions
            World world = Bukkit.getWorld(worldUid);
            int regionCount = decoder.readCompressedUint();
            while (regionCount > 0) {
                wd.getRegions().add(readParentRegion(world, format));
                --regionCount;
            }

            worldData.put(worldUid, wd);
            --len;
        }

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
        if (format < expectedFormatVersion)
            RegionProtection.log("Decoding older data format version (" + format + ") for player data file.");
        else if (format > expectedFormatVersion)
            fail("Invalid format encountered. Please make sure you are using the most recent version of the plugin.");

        Map<UUID, PersistentPlayerData> playerData = readPlayerData(format);
        decoder.close();
        return playerData;
    }

    // Wrapped, format-specific deserialization method
    private Map<UUID, PersistentPlayerData> readPlayerData(int format) throws IOException {
        // Read each individual player data object
        int len = decoder.readCompressedUint();
        Map<UUID, PersistentPlayerData> playerData = new HashMap<>(len);
        while (len > 0) {
            if (decoder.isAtEndOfStream())
                failEOF();

            UUID uuid = decoder.readUuid();
            playerData.put(uuid, new PersistentPlayerData(uuid, decoder.readCompressedUint()));
            --len;
        }

        return playerData;
    }

    /**
     * Reads a parent region including its children and sets the region's world to the given world.
     *
     * @param world  the region's world.
     * @param format the format version of the file.
     * @return the parent region.
     * @throws IOException if an I/O error occurs.
     */
    private Region readParentRegion(World world, int format) throws IOException {
        String name = decoder.readUTF8Raw();
        // Contains the priority, and the sign bit is whether or not the region is administrator-owned
        int meta = decoder.read();

        Region region = new Region(name, meta & 0x7F, (meta & 0x80) != 0 ? Utils.UUID_00 : decoder.readUuid(),
                readRegionBound(world), readRegionBound(world), null,
                format > 0 ? decoder.readArrayAsList(UUID.class) : new ArrayList<>());

        readFlags(region, format);
        int len = decoder.readCompressedUint();
        while (len > 0) {
            region.getChildren().add(readChildRegion(region, format));
            --len;
        }

        return region;
    }

    /**
     * Reads a child region and infers missing fields from the given parent region.
     *
     * @param parent the child region's parent.
     * @param format the format version of the file.
     * @return the child region.
     * @throws IOException if an I/O error occurs.
     */
    private Region readChildRegion(Region parent, int format) throws IOException {
        Region region = new Region(decoder.readUTF8Raw(), decoder.read() & 0x7F, parent.getOwner(),
                readRegionBound(parent.getWorld()), readRegionBound(parent.getWorld()), parent,
                format > 0 ? decoder.readArrayAsList(UUID.class) : new ArrayList<>());
        readFlags(region, format);
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
     * @param format    the format version of the file.
     * @throws IOException if an I/O error occurs.
     */
    private void readFlags(FlagContainer container, int format) throws IOException {
        int len = decoder.readCompressedUint();
        Map<RegionFlag, Object> flags = new HashMap<>(len);
        while (len > 0) {
            Pair<RegionFlag, Object> flag = readFlag(format);
            flags.put(flag.getFirst(), flag.getSecond());
            --len;
        }

        container.setFlags(flags);
    }

    /**
     * Reads an individual flag and its metadata.
     *
     * @param format the format version of the file.
     * @return a pair with the read flag and its metadata.
     * @throws IOException if an I/O error occurs.
     */
    private Pair<RegionFlag, Object> readFlag(int format) throws IOException {
        RegionFlag flag = readFlagEnum();

        // Reads the flag meta value. Note: perhaps make these individual functions
        Object meta;
        try {
            if (flag.isBoolean())
                meta = decoder.readBoolean();
            else if (CommandMeta.class.equals(flag.getMetaClass()))
                meta = new CommandMeta(decoder.readBoolean(), decoder.readUTF8Raw());
            else if (EnumFilter.class.equals(flag.getMetaClass())) {
                boolean isWhitelist = decoder.readBoolean();
                Set<Integer> filter = new HashSet<>();
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
                meta = new StringFilter(decoder.readBoolean(), new HashSet<>(decoder.readArrayAsList(String.class)));
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
        }
        // A stray index means we're derailed
        catch (IndexOutOfBoundsException ex) {
            fail("An invalid index was encountered. The region data file is likely corrupted.");
            return null;
        }

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

    /**
     * Logs the error to console and throws an exception with the given message.
     *
     * @param message the error message.
     */
    private void fail(String message) {
        RegionProtection.error("Failed to read data: " + message);
        throw new DeserializationException(message);
    }

    /**
     * Identical to the fail method except the message is already set below.
     */
    private void failEOF() {
        fail("EOF encountered before it was expected.");
    }

    /**
     * Thrown when an error regarding deserialization occurs.
     */
    public static final class DeserializationException extends RuntimeException {
        public DeserializationException(String message) {
            super(message);
        }
    }
}
