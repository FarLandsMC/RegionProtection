package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;

public class ExtendedUuidList extends HashSet<UUID> implements Serializable {
    public static final ExtendedUuidList EMPTY_LIST = new ExtendedUuidList();

    @Override
    public boolean add(UUID uuid) {
        if(ExtendedUuid.PUBLIC.equals(uuid)) {
            clear();
            super.add(uuid);
            return true;
        }
        return super.add(uuid);
    }

    public boolean contains(Player player) {
        if(size() == 0)
            return false;
        if(contains(ExtendedUuid.PUBLIC))
            return true;
        else if(contains(ExtendedUuid.ADMIN))
            return player.isOp();
        else
            return contains(player.getUniqueId());
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeInt(size());
        for(UUID uuid: this)
            encoder.writeUuid(uuid);
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        int len = decoder.readInt();
        while(len > 0) {
            add(decoder.readUuid());
            -- len;
        }
    }
}
