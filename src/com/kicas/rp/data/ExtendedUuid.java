package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.UUID;

public class ExtendedUuid implements Serializable {
    private UUID uuid;

    public static final UUID PUBLIC = new UUID(0, 0);
    public static final UUID ADMIN_ONLY = new UUID(0, 1);

    public ExtendedUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public ExtendedUuid(Player player) {
        this(player.getUniqueId());
    }

    public boolean matches(Player player) {
        if(PUBLIC.equals(uuid))
            return true;
        else if(ADMIN_ONLY.equals(uuid))
            return player.isOp();
        else
            return uuid.equals(player.getUniqueId());
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeUuid(uuid);
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        uuid = decoder.readUuid();
    }
}
