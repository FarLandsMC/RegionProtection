package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Acts as a general container for region flags. All flag containers have an owner which is stored as a UUID. If the
 * most significant bits and least significant bits of the UUID are all 0, then the container is considered admin-owned.
 */
public class FlagContainer {
    protected final Map<RegionFlag, Object> flags;
    protected UUID owner;
    protected final List<UUID> coOwners;

    public FlagContainer(UUID owner, List<UUID> coOwners) {
        this.flags = new HashMap<>();
        this.owner = owner;
        this.coOwners = new ArrayList<>(coOwners);
    }

    public FlagContainer(UUID owner) {
        this.flags = new HashMap<>();
        this.owner = owner;
        this.coOwners = new ArrayList<>();
    }

    /**
     * Constructs a flag container that is admin owned.
     */
    public FlagContainer() {
        this(Utils.UUID_00);
    }

    /**
     * @return true if the region is admin-owned, false otherwise.
     */
    public boolean isAdminOwned() {
        return owner.getMostSignificantBits() == 0 && owner.getLeastSignificantBits() == 0;
    }

    /**
     * @return the UUID of the owner of this region. If the region is admin-owned, then a UUID with all bits set to 0 is
     * returned.
     */
    public UUID getOwner() {
        return owner;
    }

    /**
     * Returns the name of the owner of this container.
     *
     * @return the name of the owner of this container, or "an administrator" if this container is admin owned.
     */
    public String getOwnerName() {
        return isAdminOwned() ? "an administrator" : Bukkit.getOfflinePlayer(owner).getName();
    }

    /**
     * Returns whether or not the given player is an owner of this container. If this container is admin owned, then
     * true will be returned if the given player has OP. If the given player is ignoring trust, then true will also be
     * returned.
     *
     * @param player the player.
     * @return true if this player is an owner, false otherwise.
     */
    public boolean isEffectiveOwner(Player player) {
        return (isAdminOwned() ? player.isOp() : owner.equals(player.getUniqueId())) ||
                RegionProtection.getDataManager().getPlayerSession(player).isIgnoringTrust() ||
                coOwners.contains(player.getUniqueId());
    }

    /**
     * Returns whether or not the given UUID is an actual owner of the container, not taking into account if the
     * associated player is ignoring trust.
     *
     * @param uuid the UUID to check.
     * @return true if the given UUID is an actual owner of this flag container.
     */
    public boolean isOwner(UUID uuid) {
        return (isAdminOwned() ? Bukkit.getOfflinePlayer(uuid).isOp() : owner.equals(uuid));
    }

    /**
     * Sets the UUID of the owner of this container to the given UUID.
     *
     * @param uuid the new owner UUID.
     */
    public void setOwner(UUID uuid) {
        owner = uuid;
    }

    /**
     * Adds the given UUID as a co-owner of this region and any child regions, making them an effective owner.
     *
     * @param owner the new co-owner.
     */
    public void addCoOwner(UUID owner) {
        this.coOwners.add(owner);
    }

    /**
     * Removes the given UUID from the list of co-owners in this region and all child regions.
     *
     * @param owner the co-owner to remove.
     * @return true if the co-owner was removed, false if not.
     */
    public boolean removeCoOwner(UUID owner) {
        return this.coOwners.remove(owner);
    }

    /**
     * @return an unmodifiable list of the co-owners of this region.
     */
    public List<UUID> getCoOwners() {
        return Collections.unmodifiableList(coOwners);
    }

    /**
     * Returns whether or not this container is empty.
     *
     * @return true if this container has no explicitly set flags, false otherwise.
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }

    /**
     * Returns whether or not this container has an explicit setting for the given flag.
     *
     * @param flag the flag.
     * @return true if this container has an explicit setting for the given flag, false otherwise.
     */
    public boolean hasFlag(RegionFlag flag) {
        return flags.containsKey(flag);
    }

    /**
     * Returns the value of this flag, with true being that it is allowed and false dictating that it is not. Note this
     * method does not check whether the given flag's metadata type is boolean.
     *
     * @param flag the flag.
     * @return true if this flag is allowed according to this container, false otherwise.
     */
    public boolean isAllowed(RegionFlag flag) {
        return flags.containsKey(flag) ? (boolean) flags.get(flag) : flag.getRegionDefaultValue();
    }

    /**
     * Sets the value of the given flag to the given meta in this container, overwriting any preexisting value.
     *
     * @param flag the flag.
     * @param meta the flag's metadata.
     */
    public void setFlag(RegionFlag flag, Object meta) {
        flags.put(flag, meta);
    }

    /**
     * Removes any explicitly defined metadata associated with the given flag from this container.
     *
     * @param flag the flag.
     */
    public void deleteFlag(RegionFlag flag) {
        flags.remove(flag);
    }

    /**
     * Gets the metadata associated with the given flag. If there is no metadata explicitly associated with the given
     * flag in this container, then the region default flag value is used.
     *
     * @param flag the flag.
     * @param <T>  the metadata type.
     * @return the metadata associated with this flag, or the region default value if there is no explicitly defined
     * value in this container.
     */
    @SuppressWarnings("unchecked")
    public <T> T getFlagMeta(RegionFlag flag) {
        return flags.containsKey(flag) ? (T) flags.get(flag) : flag.getRegionDefaultValue();
    }

    /**
     * Returns the metadata associated with this flag if it is explicitly defined in this flag container, otherwise new
     * flag metadata is created according to the default value of the flag if it is a boolean, or the default
     * constructor of the flag's metadata type. This new metadata is explicitly set as that flag's metadata in this
     * container, and is then returned.
     *
     * @param flag the flag.
     * @param <T>  the metadata type.
     * @return the metadata associated with the given flag, or new metadata if the flag's metadata is not explicitly
     * defined.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAndCreateFlagMeta(RegionFlag flag) {
        if (flags.containsKey(flag))
            return (T) flags.get(flag);
        else {
            Object meta = flag.isBoolean() ? flag.getRegionDefaultValue()
                    : ReflectionHelper.instantiate(flag.getMetaClass());
            flags.put(flag, meta);
            return (T) meta;
        }
    }

    /**
     * @return an exact copy of the flag-meta value pairs within this class.
     */
    public Map<RegionFlag, Object> getFlags() {
        Map<RegionFlag, Object> copy = new HashMap<>(flags.size());
        flags.forEach((flag, meta) -> copy.put(flag, meta instanceof TrustMeta ? ((TrustMeta) meta).copy() : meta));
        return copy;
    }

    /**
     * Sets this container's flags by copying the specified map's contents into this container's flag map.
     *
     * @param flags the new flags.
     */
    public void setFlags(Map<RegionFlag, Object> flags) {
        this.flags.clear();
        this.flags.putAll(flags);
    }

    /**
     * Returns true if an only if the given object is a flag container, and if the flags in this container match the
     * flags in the given container.
     *
     * @param other the object to test.
     * @return true if and only if the given object is a flag container and equivalent to this container.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof FlagContainer))
            return false;

        FlagContainer flags = (FlagContainer) other;
        return owner.equals(flags.owner) && this.flags.equals(flags.flags);
    }
}
