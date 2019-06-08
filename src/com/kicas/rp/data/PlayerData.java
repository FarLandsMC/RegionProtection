package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;

import java.io.IOException;
import java.util.UUID;

public class PlayerData implements Serializable {
    private UUID uuid;
    private int claimBlocks;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.claimBlocks = 0;
    }

    public PlayerData() {
        this(null);
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getClaimBlocks() {
        return claimBlocks;
    }

    public void addClaimBlocks(int amount) {
        claimBlocks += amount;
    }

    public void subtractClaimBlocks(int amount) {
        claimBlocks -= amount;
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeUuid(uuid);
        encoder.writeInt(claimBlocks);
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        uuid = decoder.readUuid();
        claimBlocks = decoder.readInt();
    }
}
