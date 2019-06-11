package com.kicas.rp.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlagContainer {
    protected final Map<RegionFlag, Object> flags;
    protected UUID owner;

    public FlagContainer(UUID owner) {
        this.flags = new HashMap<>();
        this.owner = owner;
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
}
