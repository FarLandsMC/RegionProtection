package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrustMeta implements Serializable {
    private final Map<UUID, TrustLevel> trustData;
    private TrustLevel publicTrustLevel;

    public static final TrustMeta EMPTY_TRUST_META = new TrustMeta();

    public TrustMeta() {
        this.trustData = new HashMap<>();
        this.publicTrustLevel = TrustLevel.NONE;
    }

    public boolean hasTrust(Player player, TrustLevel trust) {
        return trustData.containsKey(player.getUniqueId()) ? trustData.get(player.getUniqueId()).isAtLeast(trust) : publicTrustLevel.isAtLeast(trust);
    }

    public void trust(Player player, TrustLevel trust) {
        trustData.put(player.getUniqueId(), trust);
    }

    public void untrust(Player player) {
        trustData.remove(player.getUniqueId());
    }

    public void trustPublic(TrustLevel trust) {
        publicTrustLevel = trust;
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.write(publicTrustLevel.ordinal());
        encoder.writeInt(trustData.size());
        for(Map.Entry<UUID, TrustLevel> trust : trustData.entrySet()) {
            encoder.writeUuid(trust.getKey());
            encoder.write(trust.getValue().ordinal());
        }
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        publicTrustLevel = TrustLevel.VALUES[decoder.read()];
        int len = decoder.readInt();
        while(len > 0) {
            trustData.put(decoder.readUuid(), TrustLevel.VALUES[decoder.read()]);
            -- len;
        }
    }
}
