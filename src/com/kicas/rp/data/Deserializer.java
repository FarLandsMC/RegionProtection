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

public class Deserializer {
    private final Decoder decoder;
    private final int expectedFormatVersion;

    public Deserializer(File file, int expectedFormatVersion) throws IOException {
        this.decoder = new Decoder(new FileInputStream(file));
        this.expectedFormatVersion = expectedFormatVersion;
    }

    public Map<UUID, WorldData> readWorldData() throws IOException {
        int format = decoder.read();
        if(format != expectedFormatVersion)
            throw new IllegalStateException("Expected format version " + expectedFormatVersion + ". Found " + format);

        int len = decoder.read();
        Map<UUID, WorldData> worldData = new HashMap<>(len);
        while(len > 0) {
            UUID worldUid = decoder.readUuid();
            WorldData wd = new WorldData(worldUid);
            readFlags(wd);

            World world = Bukkit.getWorld(worldUid);
            int regionCount = decoder.readCompressedUint();
            while(regionCount > 0) {
                wd.getRegions().add(readParentRegion(world));
                -- regionCount;
            }

            worldData.put(worldUid, wd);
            -- len;
        }

        decoder.close();

        return worldData;
    }

    public Map<UUID, PersistentPlayerData> readPlayerData() throws IOException {
        int format = decoder.read();
        if(format != expectedFormatVersion)
            throw new IllegalStateException("Expected format version " + expectedFormatVersion + ". Found " + format);

        int len = decoder.readCompressedUint();
        Map<UUID, PersistentPlayerData> playerData = new HashMap<>(len);
        while(len > 0) {
            UUID uuid = decoder.readUuid();
            playerData.put(uuid, new PersistentPlayerData(uuid, decoder.readCompressedUint()));
            -- len;
        }

        return playerData;
    }

    private Region readParentRegion(World world) throws IOException {
        String name = decoder.readUTF8Raw();
        int meta = decoder.read();
        Region region = new Region(name, meta & 0x7F, (meta & 0x80) != 0 ? Utils.UUID_00 : decoder.readUuid(),
                readRegionBound(world), readRegionBound(world), null);
        readFlags(region);
        int len = decoder.readCompressedUint();
        while(len > 0) {
            region.getChildren().add(readChildRegion(world, region));
            -- len;
        }
        return region;
    }

    private Region readChildRegion(World world, Region parent) throws IOException {
        Region region = new Region(decoder.readUTF8Raw(), decoder.read(), parent.getOwner(), readRegionBound(world),
                readRegionBound(world), parent);
        readFlags(region);
        return region;
    }

    private Location readRegionBound(World world) throws IOException {
        return new Location(world, decoder.readCompressedInt(), decoder.read(), decoder.readCompressedInt());
    }

    private void readFlags(FlagContainer container) throws IOException {
        int len = decoder.readCompressedUint();
        Map<RegionFlag, Object> flags = new HashMap<>(len);
        while(len > 0) {
            Pair<RegionFlag, Object> flag = readFlag();
            flags.put(flag.getFirst(), flag.getSecond());
            -- len;
        }
        container.setFlags(flags);
    }

    private Pair<RegionFlag, Object> readFlag() throws IOException {
        RegionFlag flag = RegionFlag.VALUES[decoder.readCompressedUint()];
        Object meta;
        if(flag.isBoolean())
            meta = decoder.readBoolean();
        else if(CommandMeta.class.equals(flag.getMetaClass()))
            meta = new CommandMeta(decoder.readBoolean(), decoder.readUTF8Raw());
        else if(EnumFilter.class.equals(flag.getMetaClass())) {
            boolean isWhitelist = decoder.readBoolean();
            List<Integer> filter = new ArrayList<>();
            int len = decoder.readCompressedUint();
            while(len > 0) {
                filter.add(decoder.readCompressedUint());
                -- len;
            }
            meta = new EnumFilter(isWhitelist, filter);
        }else if(LocationMeta.class.equals(flag.getMetaClass())) {
            meta = new LocationMeta(decoder.readUuid(), decoder.readDouble(), decoder.readDouble(),
                    decoder.readDouble(), decoder.readFloat(), decoder.readFloat());
        }else if(StringFilter.class.equals(flag.getMetaClass()))
            meta = new StringFilter(decoder.readBoolean(), decoder.readArrayAsList(String.class));
        else if(TextMeta.class.equals(flag.getMetaClass()))
            meta = new TextMeta(decoder.readUTF8Raw());
        else if(TrustMeta.class.equals(flag.getMetaClass())) {
            TrustMeta trustMeta = new TrustMeta(TrustLevel.VALUES[decoder.read()]);
            int len = decoder.readCompressedUint();
            while(len > 0) {
                trustMeta.trust(decoder.readUuid(), TrustLevel.VALUES[decoder.read()]);
                -- len;
            }
            meta = trustMeta;
        }else
            throw new InternalError("Invalid flag meta class: " + flag.getMetaClass().getName());
        return new Pair<>(flag, meta);
    }
}
