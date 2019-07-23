package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * This class contains player data this is stored to disk between server instances. This class should not be directly
 * used since it is managed by the plugin internally.
 */
class PersistentPlayerData {
    private UUID uuid;
    private int claimBlocks;

    public PersistentPlayerData(UUID uuid, int claimBlocks) {
        this.uuid = uuid;
        this.claimBlocks = claimBlocks;
    }

    /**
     * Constructs a new instance of <code>PersistentPlayerData</code> with the given UUID and the starting claim block
     * amount specified in the config.
     *
     * @param uuid the associated player's UUID.
     */
    public PersistentPlayerData(UUID uuid) {
        this(uuid, RegionProtection.getRPConfig().getInt("general.starting-claim-blocks"));
    }

    public PersistentPlayerData(Player player) {
        this(player.getUniqueId());
    }

    /**
     * @return the associated player's UUID.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * @return the number of claim blocks the associated player has.
     */
    public int getClaimBlocks() {
        return claimBlocks;
    }

    /**
     * Sets the number of claim blocks the associated player has to the given amount.
     *
     * @param claimBlocks the new claim block amount.
     */
    public void setClaimBlocks(int claimBlocks) {
        this.claimBlocks = claimBlocks;
    }

    /**
     * Adds the given claim block amount to this player's claim block count.
     *
     * @param amount the amount to add.
     */
    public void addClaimBlocks(int amount) {
        claimBlocks += amount;
    }
}
