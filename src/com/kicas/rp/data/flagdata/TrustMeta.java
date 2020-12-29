package com.kicas.rp.data.flagdata;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Pair;
import com.kicas.rp.util.Utils;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * The metadata class for the trust region flag.
 */
public class TrustMeta extends FlagMeta implements Augmentable<TrustMeta> {
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
     * Sets this meta's public trust level to that of the given meta and adds all explicit trusts in the given meta to
     * this meta's internal list.
     *
     * @param other the other trust meta.
     */
    @Override
    public void augment(TrustMeta other) {
        if (other.publicTrustLevel != TrustLevel.NONE)
            publicTrustLevel = other.publicTrustLevel;

        trustData.putAll(other.trustData);
    }

    /**
     * If this meta's public trust level is equal to that of the given meta, then the public trust level in this meta is
     * set to none. All explicit trusts in this meta that match trusts in the given meta are removed.
     *
     * @param other the other object.
     */
    @Override
    public void reduce(TrustMeta other) {
        if (publicTrustLevel == other.publicTrustLevel)
            publicTrustLevel = TrustLevel.NONE;

        other.trustData.forEach((uuid, trust) -> {
            if (trustData.get(uuid) == trust)
                trustData.remove(uuid);
        });
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
        trustData.entrySet().stream().map(entry -> new Pair<>(entry.getValue(), RegionProtection.getDataManager()
                .currentUsernameForUuid(entry.getKey()))).filter(entry -> entry.getSecond() != null)
                .sorted(Comparator.comparing(entry -> entry.getSecond().toLowerCase())).forEach(entry -> {
            String current = list.get(entry.getFirst());
            list.put(entry.getFirst(), current.isEmpty() ? entry.getSecond() : current + ", " + entry.getSecond());
        });

        return list;
    }

    /**
     * Get the player's trustlevel from username.
     * @param name The name of the player.
     * @return The TrustLevel of the specified player, or null if not found.
     */
    public TrustLevel getTrustLevelByName(String name) {
        List<Map.Entry<UUID, TrustLevel>> reversedSet = new ArrayList<>(trustData.entrySet());
        for (Map.Entry<UUID, TrustLevel> entry : reversedSet) {
            Pair<TrustLevel, String> pair = new Pair<>(entry.getValue(), RegionProtection.getDataManager()
                    .currentUsernameForUuid(entry.getKey()));
            if (pair.getSecond() != null && pair.getSecond().equalsIgnoreCase(name)) {
                return pair.getFirst();
            }
        }
        return null;
    }

    /**
     * Gives a list of all trusted player UUIDs
     * @return A list of the uuids for players with any trust on a region
     */
    public List<UUID> getAllTrustedPlayers(){
        return new ArrayList<>(trustData.keySet());
    }


    /**
     * Gives a list of the names of all trusted player names
     * @return A list of the names for players with any trust on a region
     */
    public List<String> getAllTrustedPlayerNames() {
        List<String> players = new ArrayList<>(getAllTrustedPlayers().stream().map(RegionProtection.getDataManager()::currentUsernameForUuid).collect(Collectors.toList()));
        if (publicTrustLevel != TrustLevel.NONE)
            players.add("public");
        return players;
    }

    /**
     * Returns this trust meta in string form. If the trust data is empty and the public trust level is set to NONE,
     * then "[No Trust]" is returned. If the trust data is empty and the public trust level is set to BUILD, then
     * "[Full Trust]" is returned.
     *
     * @return this trust meta in string form.
     */
    @Override
    public String toMetaString() {
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
     * Updates this trust meta based on the given string. The format for a trust meta is as follows: for an individual
     * trust level, the name of the trust level should be followed by a colon, then a comma separated list of the players
     * who are to be designated that trust level. If "public" is encountered as a player name, then that trust level
     * will be assigned to the public. Each individual trust level as defined above should be separated by a space
     * character. If the given string is empty then an empty trust meta will be returned.
     *
     * @param metaString the string to parse.
     */
    @Override
    public void readMetaString(String metaString) {
        // Trim off any excess whitespace and check for an empty string, resulting in an empty trust meta
        metaString = metaString.trim();
        if (metaString.isEmpty()) {
            publicTrustLevel = TrustLevel.NONE;
            trustData.clear();
            return;
        }

        // Create the metadata and begin parsing the individual trust levels
        for (String trust : metaString.split(" ")) {
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
                    publicTrustLevel = level;
                    continue;
                }

                UUID uuid = RegionProtection.getDataManager().uuidForUsername(player);
                if (uuid == null)
                    throw new IllegalArgumentException("Invalid player name: " + player);

                trustData.put(uuid, level);
            }
        }
    }
}
