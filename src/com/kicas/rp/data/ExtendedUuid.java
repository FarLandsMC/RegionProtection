package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.UUID;

public class ExtendedUuid implements Serializable {
    private UUID uuid;

    public static final UUID PUBLIC = new UUID(0, 0);
    public static final UUID ADMIN = new UUID(0, 1);

    public ExtendedUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public ExtendedUuid(Player player) {
        this(player.getUniqueId());
    }

    public ExtendedUuid() {
        this((UUID)null);
    }

    public boolean isPublic() {
        return PUBLIC.equals(uuid);
    }

    public boolean isAdmin() {
        return ADMIN.equals(uuid);
    }

    public boolean matches(Player player) {
        if(PUBLIC.equals(uuid))
            return true;
        else if(ADMIN.equals(uuid))
            return player.isOp();
        else
            return uuid.equals(player.getUniqueId());
    }

    public String getName() {
        if(PUBLIC.equals(uuid))
            return "public";
        else if(ADMIN.equals(uuid))
            return "administrator";
        else
            return Bukkit.getOfflinePlayer(uuid).getName();
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
