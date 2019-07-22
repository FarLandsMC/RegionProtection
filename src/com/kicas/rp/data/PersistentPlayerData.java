package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * This class contains player data this is stored to disk between server instances.
 */
public class PersistentPlayerData {
    private UUID uuid;
    private int claimBlocks;

    public PersistentPlayerData(UUID uuid, int claimBlocks) {
        this.uuid = uuid;
        this.claimBlocks = claimBlocks;
    }

    public PersistentPlayerData(UUID uuid) {
        this(uuid, RegionProtection.getRPConfig().getInt("general.starting-claim-blocks"));
    }

    public PersistentPlayerData(Player player) {
        this(player.getUniqueId());
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getClaimBlocks() {
        return claimBlocks;
    }

    public void setClaimBlocks(int claimBlocks) {
        this.claimBlocks = claimBlocks;
    }

    public void addClaimBlocks(int amount) {
        claimBlocks += amount;
    }
}
