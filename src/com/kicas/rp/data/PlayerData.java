package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;

import java.io.IOException;
import java.util.List;

public class PlayerData implements Serializable {
    private int claimBlocks;
    private List<Region> claims;

    @Override
    public void serialize(Encoder encoder) throws IOException {

    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {

    }
}
