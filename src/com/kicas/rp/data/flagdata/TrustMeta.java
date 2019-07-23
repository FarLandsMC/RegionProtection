package com.kicas.rp.data.flagdata;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Utils;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * The metadata class for the trust region flag.
 */
public class TrustMeta {
    // Key: player UUID, value: trust level
    private final Map<UUID, TrustLevel> trustData;
    private TrustLevel publicTrustLevel;

    // Default values
    public static final TrustMeta FULL_TRUST = new TrustMeta(TrustLevel.BUILD);
    public static final TrustMeta NO_TRUST = new TrustMeta();

    public TrustMeta() {
        this.trustData = new HashMap<>();
        this.publicTrustLevel = TrustLevel.NONE;
    }

    public TrustMeta(TrustLevel publicTrustLevel) {
        this.trustData = new HashMap<>();
        this.publicTrustLevel = publicTrustLevel;
    }

    /**
     * @return an exact copy of this trust meta.
     */
    public TrustMeta copy() {
        TrustMeta copy = new TrustMeta(publicTrustLevel);
        copy.trustData.putAll(trustData);
        return copy;
    }

    /**
     * Returns whether or not the given player has the specified level of trust for the given flag container. If the
     * player's trust level is explicitly defined in this object, then that value is used otherwise it defaults to the
     * public trust level. If the specified player is the owner of the container, then true is always returned. If the
     * specified container of this trust meta is a Region with a parent, then unless explicitly defined in this meta
     * this method will default to the trust meta of the parent of the given container.
     *
     * @param player    the player.
     * @param trust     the trust level.
     * @param container the flag container.
     * @return true if the given player has the given level of trust, false other wise.
     */
    public boolean hasTrust(Player player, TrustLevel trust, FlagContainer container) {
        // If these conditions are met then the player bypasses the trust flag
        if (container.isEffectiveOwner(player) || RegionProtection.getDataManager().getPlayerSession(player)
                .isIgnoringTrust()) {
            return true;
        }

        // Check for parent flags
        if (container instanceof Region) {
            Region region = (Region) container;
            // If a parent is found, then default to the parent trust meta
            if (region.hasParent()) {
                // This trust
                if (trustData.containsKey(player.getUniqueId()))
                    return trustData.get(player.getUniqueId()).isAtLeast(trust);

                // Parent trust
                TrustMeta parentTrustMeta = region.getParent().getFlagMeta(RegionFlag.TRUST);
                if (parentTrustMeta.trustData.containsKey(player.getUniqueId()))
                    return parentTrustMeta.trustData.get(player.getUniqueId()).isAtLeast(trust);

                // If the public trust level is none for this container default to the parent
                return publicTrustLevel == TrustLevel.NONE ? parentTrustMeta.publicTrustLevel.isAtLeast(trust)
                        : publicTrustLevel.isAtLeast(trust);
            }
        }

        // Check for a specific case, then default to the public trust level if there is none
        return (trustData.containsKey(player.getUniqueId()) ? trustData.get(player.getUniqueId()).isAtLeast(trust) :
                publicTrustLevel.isAtLeast(trust));
    }

    /**
     * Explicitly defines the given player's trust level.
     *
     * @param uuid  the player's UUID.
     * @param trust the trust level.
     */
    public void trust(UUID uuid, TrustLevel trust) {
        trustData.put(uuid, trust);
    }

    /**
     * Removes all trust levels from the given player.
     *
     * @param uuid the player'd UUID.
     */
    public void untrust(UUID uuid) {
        trustData.remove(uuid);
    }

    /**
     * Sets the public trust level to the given trust level.
     *
     * @param trust the trust level.
     */
    public void trustPublic(TrustLevel trust) {
        publicTrustLevel = trust;
    }

    /**
     * @return the public trust level of this trust meta.
     */
    public TrustLevel getPublicTrustLevel() {
        return publicTrustLevel;
    }

    /**
     * @return an exact copy of the raw trust data in this trust meta.
     */
    public Map<UUID, TrustLevel> getRawTrustDataCopy() {
        return Collections.unmodifiableMap(trustData);
    }

    /**
     * @return a mapping of every trust level to a list of the UUIDs with that trust level.
     */
    public Map<TrustLevel, List<UUID>> getTrustList() {
        Map<TrustLevel, List<UUID>> list = new HashMap<>();

        // Skip the NONE trust level
        for (int i = 1; i < TrustLevel.VALUES.length; ++i)
            list.put(TrustLevel.VALUES[i], new ArrayList<>());

        trustData.forEach((uuid, trust) -> list.get(trust).add(uuid));

        return list;
    }

    /**
     * @return a mapping of every trust level to a string containing a comma separated list of the players with that
     * trust level.
     */
    public Map<TrustLevel, String> getFormattedTrustList() {
        Map<TrustLevel, String> list = new HashMap<>();

        // Skip the NONE trust level
        for (int i = 1; i < TrustLevel.VALUES.length; ++i)
            list.put(TrustLevel.VALUES[i], "");

        if (publicTrustLevel != TrustLevel.NONE)
            list.put(publicTrustLevel, "public");

        // Built the individual formatted strings
        trustData.forEach((uuid, trust) -> {
            String name = RegionProtection.getDataManager().currentUsernameForUuid(uuid);
            if (name != null) {
                String current = list.get(trust);
                list.put(trust, current.isEmpty() ? name : current + ", " + name);
            }
        });

        return list;
    }

    /**
     * Returns this trust meta in string form. If the trust data is empty and the public trust level is set to NONE,
     * then "[No Trust]" is returned. If the trust data is empty and the public trust level is set to BUILD, then
     * "[Full Trust]" is returned.
     *
     * @return this trust meta in string form.
     */
    @Override
    public String toString() {
        if (trustData.isEmpty()) {
            if (publicTrustLevel == TrustLevel.NONE)
                return "[No Trust]";
            else if (publicTrustLevel == TrustLevel.BUILD)
                return "[Full Trust]";
        }

        return getFormattedTrustList().entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
                .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                .map(entry -> Utils.capitalize(entry.getKey().name()) + ": " + entry.getValue() + "\n")
                .reduce("", String::concat).trim();
    }

    /**
     * Returns true if an only if the given object is a trust meta instance, and if the trust lists are equal and the
     * public trust levels are equal.
     *
     * @param other the object to test.
     * @return true if this trust meta is equivalent to the given object, false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof TrustMeta))
            return false;

        TrustMeta tm = (TrustMeta) other;
        return trustData.equals(tm.trustData) && publicTrustLevel.equals(tm.publicTrustLevel);
    }

    /**
     * Creates a trust meta based on the given string. The format for a trust meta is as follows: for an individual
     * trust level, the name of the trust level should be followed by a colon, then a comma separated list of the players
     * who are to be designated that trust level. If "public" is encountered as a player name, then that trust level
     * will be assigned to the public. Each individual trust level as defined above should be separated by a space
     * character. If the given string is empty then an empty trust meta will be returned.
     *
     * @param string the string to parse.
     * @return the trust meta derived from
     */
    public static TrustMeta fromString(String string) {
        // Trim off any excess whitespace and check for an empty string, resulting in an empty trust meta
        string = string.trim();
        if (string.isEmpty())
            return NO_TRUST.copy();

        // Create the metadata and begin parsing the individual trust levels
        TrustMeta meta = new TrustMeta();
        for (String trust : string.split(" ")) {
            // Ignore extra spaces
            if (trust.isEmpty())
                continue;

            // Error: missing ':'
            if (!trust.contains(":"))
                throw new IllegalArgumentException("Expected a : in \"" + trust + "\"");

            // Get and check the level
            String trustLevel = trust.substring(0, trust.indexOf(':')),
                    players = trust.substring(trust.indexOf(':') + 1);
            TrustLevel level = Utils.safeValueOf(TrustLevel::valueOf, trustLevel.toUpperCase());
            if (level == null)
                throw new IllegalArgumentException("Invalid trust level: " + trustLevel);

            // Parse the players
            for (String player : players.split(",")) {
                if ("public".equals(player)) {
                    meta.trustPublic(level);
                    continue;
                }
                UUID uuid = RegionProtection.getDataManager().uuidForUsername(player);
                if (uuid == null)
                    throw new IllegalArgumentException("Invalid player name: " + player);
                meta.trustData.put(uuid, level);
            }
        }

        return meta;
    }
}
