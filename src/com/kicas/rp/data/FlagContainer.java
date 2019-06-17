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

/**
 * Acts as a general container for region flags. All flag containers have an owner which is stored as a UUID. If the
 * most significant bits and least significant bits of the UUID are all 0, then the container is considered admin owned.
 */
public class FlagContainer implements Serializable {
    protected final Map<RegionFlag, Object> flags;
    protected UUID owner;

    public FlagContainer(UUID owner) {
        this.flags = new HashMap<>();
        this.owner = owner;
    }

    /**
     * Constructs a flag container that is admin owned.
     */
    public FlagContainer() {
        this(new UUID(0, 0));
    }

    public boolean isAdminOwned() {
        return owner.getMostSignificantBits() == 0 && owner.getLeastSignificantBits() == 0;
    }

    public UUID getOwner() {
        return owner;
    }

    /**
     * Returns the name of the owner of this container.
     * @return the name of the owner of this container, or "an administrator" if this container is admin owned.
     */
    public String getOwnerName() {
        return isAdminOwned() ? "an administrator" : Bukkit.getOfflinePlayer(owner).getName();
    }

    /**
     * Returns whether or not the given player is an owner of this container. If this container is admin owned, then
     * @param player the player.
     * @return true if this player is an owner, false otherwise.
     */
    public boolean isOwner(Player player) {
        return isAdminOwned() ? player.isOp() : owner.equals(player.getUniqueId());
    }

    public void setOwner(UUID uuid) {
        owner = uuid;
    }

    /**
     * Returns whether or not this container is empty.
     * @return true if this container has no explicitly set flags, false otherwise.
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }

    /**
     * Returns whether or not this container has an explicit setting for the given flag.
     * @param flag the flag.
     * @return true if this container has an explicit setting for the given flag, false otherwise.
     */
    public boolean hasFlag(RegionFlag flag) {
        return flags.containsKey(flag);
    }

    /**
     * Returns the value of this flag, with true being that it is allowed and false dictating that it is not. Note this
     * method does not check whether the given flag's metadata type is boolean.
     * @param flag the flag.
     * @return true if this flag is allowed according to this container, false otherwise.
     */
    public boolean isAllowed(RegionFlag flag) {
        return flags.containsKey(flag) ? (boolean)flags.get(flag) : flag.getDefaultValue();
    }

    public void setFlag(RegionFlag flag, boolean allow) {
        flags.put(flag, allow);
    }

    public void setFlag(RegionFlag flag, Object meta) {
        flags.put(flag, meta);
    }

    public void deleteFlag(RegionFlag flag) {
        flags.remove(flag);
    }

    @SuppressWarnings("unchecked")
    public <T> T getFlagMeta(RegionFlag flag) {
        return flags.containsKey(flag) ? (T)flags.get(flag) : flag.getDefaultValue();
    }

    /**
     * Returns the metadata associated with this flag if it is explicitly defined in this flag container, otherwise new
     * flag metadata is created according to the default value of the flag if it is a boolean, or the default
     * constructor of the flag's metadata type. This new metadata is explicitly set as that flag's metadata in this
     * container and is returned.
     * @param flag the flag.
     * @param <T> the metadata type.
     * @return the metadata associated with the given flag, or new metadata if the flag's metadata is not explicitly
     * defined.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAndCreateFlagMeta(RegionFlag flag) {
        if(flags.containsKey(flag))
            return (T)flags.get(flag);
        else{
            Object meta = flag.isBoolean() ? flag.getDefaultValue() : ReflectionHelper.instantiate(flag.getMetaClass());
            flags.put(flag, meta);
            return (T)meta;
        }
    }

    public Map<RegionFlag, Object> getFlags() {
        return flags;
    }

    /**
     * Sets this container's flags by copying the specified map's contents into this container's flag map.
     * @param flags the new flags.
     */
    public void setFlags(Map<RegionFlag, Object> flags) {
        this.flags.clear();
        this.flags.putAll(flags);
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

    @Override
    public boolean equals(Object other) {
        if(other == this)
            return true;

        if(!(other instanceof FlagContainer))
            return false;

        FlagContainer flags = (FlagContainer)other;
        return owner.equals(flags.owner) && this.flags.equals(flags.flags);
    }
}
