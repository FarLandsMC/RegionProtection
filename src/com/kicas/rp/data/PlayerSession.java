package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;

import java.io.IOException;
import java.util.UUID;

public class PlayerSession implements Serializable {
    private UUID uuid;
    private int claimBlocks;
    private RegionHighlighter currentHighlighter;

    public PlayerSession(UUID uuid) {
        this.uuid = uuid;
        this.claimBlocks = 0;
        this.currentHighlighter = null;
    }

    public PlayerSession() {
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

    public void setRegionHighlighter(RegionHighlighter highlighter) {
        if(currentHighlighter != null && !currentHighlighter.isComplete())
            currentHighlighter.remove();
        currentHighlighter = highlighter;
        currentHighlighter.showBlocks();
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
