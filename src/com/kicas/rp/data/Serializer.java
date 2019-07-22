package com.kicas.rp.data;

import com.kicas.rp.data.flagdata.*;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Utils;
import org.bukkit.Location;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Serializer {
    private final Encoder encoder;
    private final int formatVersion;

    public Serializer(File file, int formatVersion) throws IOException {
        this.formatVersion = formatVersion;
        this.encoder = new Encoder(new FileOutputStream(file));
    }

    public void writeWorldData(Collection<WorldData> worldData) throws IOException {
        encoder.write(formatVersion);

        encoder.write(worldData.size());
        for(WorldData wd : worldData) {
            encoder.writeUuid(wd.getWorldUid());
            writeFlags(wd);
            encoder.writeUintCompressed(wd.getRegions().size());
            for (Region region : wd.getRegions())
                writeRegion(region);
        }

        encoder.close();
    }

    public void writePlayerData(Collection<PersistentPlayerData> playerData) throws IOException {
        encoder.write(formatVersion);

        encoder.writeUintCompressed(playerData.size());
        for(PersistentPlayerData ppd : playerData) {
            encoder.writeUuid(ppd.getUuid());
            encoder.writeUintCompressed(ppd.getClaimBlocks());
        }

        encoder.close();
    }

    private void writeRegion(Region region) throws IOException {
        encoder.writeUTF8Raw(region.getRawName() == null ? "" : region.getRawName());
        int meta = (region.isAdminOwned() ? 0x80 : 0) | Utils.constrain(region.getPriority(), 0, 127);
        encoder.write(meta);
        if(!region.hasParent() && !region.isAdminOwned())
            encoder.writeUuid(region.getOwner());

        Location loc = region.getMin();
        encoder.writeIntCompressed(loc.getBlockX());
        encoder.write(Utils.constrain(loc.getBlockY(), 0, 255));
        encoder.writeIntCompressed(loc.getBlockZ());
        loc = region.getMax();
        encoder.writeIntCompressed(loc.getBlockX());
        encoder.write(Utils.constrain(loc.getBlockY(), 0, 255));
        encoder.writeIntCompressed(loc.getBlockZ());

        writeFlags(region);

        if(!region.hasParent()) {
            encoder.writeUintCompressed(region.getChildren().size());
            for(Region child : region.getChildren())
                writeRegion(child);
        }
    }

    private void writeFlags(FlagContainer container) throws IOException {
        Map<RegionFlag, Object> flags = container.getFlags();
        encoder.writeUintCompressed(flags.size());
        for(Map.Entry<RegionFlag, Object> entry : flags.entrySet())
            writeFlag(entry.getKey(), entry.getValue());
    }

    private void writeFlag(RegionFlag flag, Object meta) throws IOException {
        encoder.writeUintCompressed(flag.ordinal());
        if(flag.isBoolean())
            encoder.writeBoolean((boolean)meta);
        else{
            if(meta instanceof CommandMeta) {
                encoder.writeBoolean(((CommandMeta)meta).runFromConsole());
                encoder.writeUTF8Raw(((CommandMeta)meta).getCommand());
            }else if(meta instanceof EnumFilter) {
                encoder.writeBoolean(((EnumFilter)meta).isWhitelist());
                List<Integer> filter = ((EnumFilter)meta).getFilterCopy();
                encoder.writeUintCompressed(filter.size());
                for(Integer integer : filter)
                    encoder.writeUintCompressed(integer);
            }else if(meta instanceof LocationMeta) {
                Location loc = ((LocationMeta)meta).getLocation();
                encoder.writeUuid(loc.getWorld().getUID());
                encoder.writeDouble(loc.getX());
                encoder.writeDouble(loc.getY());
                encoder.writeDouble(loc.getZ());
                encoder.writeFloat(loc.getYaw());
                encoder.writeFloat(loc.getPitch());
            }else if(meta instanceof StringFilter) {
                encoder.writeBoolean(((StringFilter)meta).isWhitelist());
                encoder.writeArray(((StringFilter)meta).getFilterCopy(), String.class);
            }else if(meta instanceof TextMeta)
                encoder.writeUTF8Raw(((TextMeta)meta).getText());
            else if(meta instanceof TrustMeta) {
                encoder.write(((TrustMeta)meta).getPublicTrustLevel().ordinal());
                Map<UUID, TrustLevel> rawTrustData = ((TrustMeta)meta).getRawTrustDataCopy();
                encoder.writeUintCompressed(rawTrustData.size());
                for(Map.Entry<UUID, TrustLevel> trust : rawTrustData.entrySet()) {
                    encoder.writeUuid(trust.getKey());
                    encoder.write(trust.getValue().ordinal());
                }
            }
        }
    }
}
