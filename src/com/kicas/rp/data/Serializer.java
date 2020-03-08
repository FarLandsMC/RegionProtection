package com.kicas.rp.data;

import com.kicas.rp.data.flagdata.*;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Utils;
import org.bukkit.Location;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * This class is a version-specific serializer for region data and persistent player data.
 */
public class Serializer implements AutoCloseable {
    private final Encoder encoder;
    private final int formatVersion;

    public Serializer(File file, int formatVersion) throws IOException {
        this.formatVersion = formatVersion;
        this.encoder = new Encoder(new FileOutputStream(file));
    }

    /**
     * Writes the given world data to the output file.
     *
     * @param worldData the world data to write.
     * @throws IOException if an I/O error occurs.
     */
    public void writeWorldData(Collection<WorldData> worldData) throws IOException {
        encoder.write(formatVersion);

        encoder.write(worldData.size());
        for (WorldData wd : worldData) {
            encoder.writeUuid(wd.getWorldUid());
            writeFlags(wd);
            encoder.writeUintCompressed(wd.getRegions().size());
            for (Region region : wd.getRegions())
                writeRegion(region);
        }

        encoder.close();
    }

    /**
     * Writes the given persistent player data collection to the output file.
     *
     * @param playerData the player data to write.
     * @throws IOException if an I/O error occurs.
     */
    public void writePlayerData(Collection<PersistentPlayerData> playerData) throws IOException {
        encoder.write(formatVersion);

        encoder.writeUintCompressed(playerData.size());
        for (PersistentPlayerData ppd : playerData) {
            encoder.writeUuid(ppd.getUuid());
            encoder.writeUintCompressed(ppd.getClaimBlocks());
        }
    }

    /**
     * Writes a region to the output file.
     *
     * @param region the region to write.
     * @throws IOException if an I/O error occurs.
     */
    private void writeRegion(Region region) throws IOException {
        // Name
        encoder.writeUTF8Raw(region.getRawName() == null ? "" : region.getRawName());

        // Priority and the admin-ownership bit
        int meta = (region.isAdminOwned() ? 0x80 : 0) | Utils.constrain(region.getPriority(), 0, 127);
        encoder.write(meta);

        // Write the actual owner
        if (!region.hasParent() && !region.isAdminOwned())
            encoder.writeUuid(region.getOwner());

        // Bounds
        Location loc = region.getMin();
        encoder.writeIntCompressed(loc.getBlockX());
        encoder.write(Utils.constrain(loc.getBlockY(), 0, 255));
        encoder.writeIntCompressed(loc.getBlockZ());
        loc = region.getMax();
        encoder.writeIntCompressed(loc.getBlockX());
        encoder.write(Utils.constrain(loc.getBlockY(), 0, 255));
        encoder.writeIntCompressed(loc.getBlockZ());

        // Co-owners
        encoder.writeArray(region.getCoOwners(), UUID.class);

        writeFlags(region);

        // Children
        if (!region.hasParent()) {
            encoder.writeUintCompressed(region.getChildren().size());
            for (Region child : region.getChildren())
                writeRegion(child);
        }
    }

    /**
     * Writes the flags in a given flag container to the output file.
     *
     * @param container the container with the flags to write.
     * @throws IOException if an I/O error occurs.
     */
    private void writeFlags(FlagContainer container) throws IOException {
        Map<RegionFlag, Object> flags = container.getFlags();
        encoder.writeUintCompressed(flags.size());
        for (Map.Entry<RegionFlag, Object> entry : flags.entrySet())
            writeFlag(entry.getKey(), entry.getValue());
    }

    /**
     * Writes a given flag-meta pair to the output file.
     *
     * @param flag the flag.
     * @param meta the associated metadata.
     * @throws IOException if an I/O error occurs.
     */
    private void writeFlag(RegionFlag flag, Object meta) throws IOException {
        encoder.writeUintCompressed(flag.ordinal());
        if (flag.isBoolean())
            encoder.writeBoolean((boolean) meta);
        else if (meta instanceof CommandMeta) {
            encoder.writeBoolean(((CommandMeta) meta).runFromConsole());
            encoder.writeUTF8Raw(((CommandMeta) meta).getCommand());
        } else if (meta instanceof EnumFilter) {
            encoder.writeBoolean(((EnumFilter) meta).isWhitelist());
            Set<? extends Enum<?>> filter = ((EnumFilter<? extends Enum<?>>) meta).getFilter();
            encoder.writeUintCompressed(filter.size());
            for (Enum<?> element : filter)
                encoder.writeUintCompressed(element.ordinal());
        } else if (meta instanceof LocationMeta) {
            Location loc = ((LocationMeta) meta).getLocation();
            encoder.writeUuid(loc.getWorld().getUID());
            encoder.writeDouble(loc.getX());
            encoder.writeDouble(loc.getY());
            encoder.writeDouble(loc.getZ());
            encoder.writeFloat(loc.getYaw());
            encoder.writeFloat(loc.getPitch());
        } else if (meta instanceof StringFilter) {
            encoder.writeBoolean(((StringFilter) meta).isWhitelist());
            encoder.writeArray(((StringFilter) meta).getFilter(), String.class);
        } else if (meta instanceof TextMeta)
            encoder.writeUTF8Raw(((TextMeta) meta).getText());
        else if (meta instanceof TrustMeta) {
            encoder.write(((TrustMeta) meta).getPublicTrustLevel().ordinal());
            Map<UUID, TrustLevel> rawTrustData = ((TrustMeta) meta).getRawTrustDataCopy();
            encoder.writeUintCompressed(rawTrustData.size());
            for (Map.Entry<UUID, TrustLevel> trust : rawTrustData.entrySet()) {
                encoder.writeUuid(trust.getKey());
                encoder.write(trust.getValue().ordinal());
            }
        }
    }

    /**
     * Closes the encoder in this serializer.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        encoder.close();
    }
}
