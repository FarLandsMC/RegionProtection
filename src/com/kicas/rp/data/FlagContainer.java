package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.ReflectionHelper;
import com.kicas.rp.util.Serializable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlagContainer implements Serializable {
    protected final Map<RegionFlag, Object> flags;
    protected UUID owner;

    public FlagContainer(UUID owner) {
        this.flags = new HashMap<>();
        this.owner = owner;
    }

    public FlagContainer(FlagContainer other) {
        this.flags = new HashMap<>(other.flags);
        this.owner = other.owner;
    }

    public FlagContainer() {
        this(new UUID(0, 0));
    }

    public boolean isAdminOwned() {
        return owner.getMostSignificantBits() == 0 && owner.getLeastSignificantBits() == 0;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return isAdminOwned() ? "an administrator" : Bukkit.getOfflinePlayer(owner).getName();
    }

    public boolean isOwner(Player player) {
        return isAdminOwned() ? player.isOp() : owner.equals(player.getUniqueId());
    }

    public void setOwner(UUID uuid) {
        owner = uuid;
    }

    public boolean isEmpty() {
        return flags.isEmpty();
    }

    public boolean hasFlag(RegionFlag flag) {
        return flags.containsKey(flag);
    }

    public boolean isAllowed(RegionFlag flag) {
        return flags.containsKey(flag) ? (boolean)flags.get(flag) : flag.getDefaultValue();
    }

    public void setFlag(RegionFlag flag, boolean allow) {
        flags.put(flag, allow);
    }

    public void setFlag(RegionFlag flag, Object meta) {
        flags.put(flag, meta);
    }

    @SuppressWarnings("unchecked")
    public <T> T getFlagMeta(RegionFlag flag) {
        return flags.containsKey(flag) ? (T)flags.get(flag) : flag.getDefaultValue();
    }

    public Map<RegionFlag, Object> getFlags() {
        return flags;
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
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
            -- len;
        }
    }
}
