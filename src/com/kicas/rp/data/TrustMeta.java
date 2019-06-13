package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The metadata class for the trust region flag.
 */
public class TrustMeta implements Serializable {
    // Key: player UUID, value: trust level
    private final Map<UUID, TrustLevel> trustData;
    private TrustLevel publicTrustLevel;

    // Default value
    public static final TrustMeta EMPTY_TRUST_META = new TrustMeta();

    public TrustMeta() {
        this.trustData = new HashMap<>();
        this.publicTrustLevel = TrustLevel.NONE;
    }

    /**
     * Returns whether or not the given player has the specified level of trust for the given flag container. If the
     * player's trust level is explicitly defined in this object, then that value is used otherwise it defaults to the
     * public trust level. If the specified player is the owner of the container, then true is always returned.
     * @param player the player.
     * @param trust the trust level.
     * @param container the flag container.
     * @return true if the given player has the given level of trust, false other wise.
     */
    public boolean hasTrust(Player player, TrustLevel trust, FlagContainer container) {
        return (trustData.containsKey(player.getUniqueId()) ? trustData.get(player.getUniqueId()).isAtLeast(trust) :
                publicTrustLevel.isAtLeast(trust)) || container.isOwner(player);
    }

    /**
     * Explicitly defines the given player's trust level.
     * @param player the player.
     * @param trust the trust level.
     */
    public void trust(Player player, TrustLevel trust) {
        trustData.put(player.getUniqueId(), trust);
    }

    /**
     * Removes all trust levels from the given player.
     * @param player the player.
     */
    public void untrust(Player player) {
        trustData.remove(player.getUniqueId());
    }

    /**
     * Sets the public trust level to the given trust level.
     * @param trust the trust level.
     */
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

    /**
     * Creates a trust meta based on the given string. The format for a trust meta is as follows: for an individual
     * trust level, the name of the trust level should be follwed by a colon, then a comma separated list of the players
     * who are to be designated that trust level. If "public" is encountered as a player name, then that trust level
     * will be assigned to the public. Each individual trust level as defined above should be separated by a space
     * character. If the given string is empty then an empty trust meta will be returned.
     * @param string the string to parse.
     * @return the trust meta derived from
     */
    @SuppressWarnings("deprecation")
    public static TrustMeta fromString(String string) {
        // Trim off any excess whitespace and check for an empty string, resulting in an empty trust meta
        string = string.trim();
        if(string.isEmpty())
            return EMPTY_TRUST_META;

        // Create the metadata and begin parsing the individual trust levels
        TrustMeta meta = new TrustMeta();
        for(String trust : string.split(" ")) {
            // Ignore extra spaces
            if(trust.isEmpty())
                continue;

            // Error: missing ':'
            if(!trust.contains(":"))
                throw new IllegalArgumentException("Expected a : in \"" + trust + "\"");

            // Get and check the level
            String trustLevel = trust.substring(0, trust.indexOf(':')),
                    players = trust.substring(trust.indexOf(':') + 1);
            TrustLevel level = Utils.safeValueOf(TrustLevel::valueOf, trustLevel.toUpperCase());
            if(level == null)
                throw new IllegalArgumentException("Invalid trust level: " + trustLevel);

            // Parse the players
            for(String player : players.split(",")) {
                if("public".equals(player))
                    meta.trustPublic(level);
                // This is the only check that appears to work to validate a username
                OfflinePlayer op = Bukkit.getOfflinePlayer(player);
                if(Bukkit.getOfflinePlayer(op.getUniqueId()).getName() == null)
                    throw new IllegalArgumentException("Invalid player name: " + player);
                meta.trustData.put(op.getUniqueId(), level);
            }
        }

        return meta;
    }
}
