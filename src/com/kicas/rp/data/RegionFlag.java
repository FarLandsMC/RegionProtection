package com.kicas.rp.data;

import com.kicas.rp.data.flagdata.*;
import com.kicas.rp.data.flagdata.EnumFilter.EntityFilter;
import com.kicas.rp.data.flagdata.EnumFilter.MaterialFilter;
import com.kicas.rp.util.Pair;
import com.kicas.rp.util.ReflectionHelper;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * All of the flags which can be set for a specific region.
 */
public enum RegionFlag {
    TRUST(TrustMeta.class),
    DENY_SPAWN(EntityFilter.class),
    DENY_BREAK(MaterialFilter.class),
    DENY_PLACE(MaterialFilter.class),
    ANIMAL_GRIEF_BLOCKS(true), // block damage caused by non-player, non-hostile mobs
    TNT(true),
    OVERLAP, // Regions containing the same locations
    INVINCIBLE,
    GREETING(TextMeta.class),
    FAREWELL(TextMeta.class),
    HOSTILE_DAMAGE, // Allow players to damage hostiles
    ANIMAL_DAMAGE, // Allow players to damage animals
    POTION_SPLASH, // The actual splash part
    FORCE_CHEST_ACCESS, // Just chests, trapped chests, and ender chests. Only checked if it's explicitly set.
    PVP(true),
    BED_ENTER,
    WATER_FLOW,
    LAVA_FLOW,
    SNOW_CHANGE,
    ICE_CHANGE,
    CORAL_DEATH,
    LEAF_DECAY,
    LIGHTNING_MOB_DAMAGE,
    PORTAL_PAIR_FORMATION,
    ENTER_COMMAND(CommandMeta.class),
    EXIT_COMMAND(CommandMeta.class),
    DENY_COMMAND(StringFilter.class),
    FOLLOW, // prevent pet tp
    DENY_AGGRO(EntityFilter.class), // prevent certain mobs from targeting the player
    GROWTH, // vine growth grass spread etc
    DENY_BLOCK_USE(MaterialFilter.class),
    KEEP_INVENTORY,
    KEEP_XP,
    RESPAWN_LOCATION(LocationMeta.class),
    DENY_ENTITY_USE(EntityFilter.class),
    DENY_ITEM_USE(MaterialFilter.class),
    DENY_WEAPON_USE(MaterialFilter.class),
    FLIGHT,
    HOSTILE_GRIEF_BLOCKS(true), // block damage caused by hostile mobs
    HOSTILE_GRIEF_ENTITIES(true), // entity damage caused by hostile mobs
    LIGHTNING_STRIKES,
    DENY_ITEM_CONSUMPTION(MaterialFilter.class),
    TNT_IGNITION,
    TNT_ENTITY_DAMAGE,
    TNT_BLOCK_DAMAGE,
    FALL_DAMAGE,
    ELYTRA_FLIGHT,
    DENY_ENTITY_TELEPORT(EntityFilter.class),
    ANIMAL_CONTAINERS,
    ITEM_DAMAGE,
    RIPTIDE,
    FARMLAND_MOISTURE_CHANGE;

    public static final RegionFlag[] VALUES = values();
    private static final Map<RegionFlag, Pair<Object, Function<World, Object>>> DEFAULT_VALUES = new HashMap<>();

    private final boolean playerToggleable;
    private final Class<? extends FlagMeta> metaClass;

    RegionFlag(boolean playerToggleable, Class<? extends FlagMeta> metaClass) {
        this.playerToggleable = playerToggleable;
        this.metaClass = metaClass;
    }

    RegionFlag(Class<? extends FlagMeta> metaClass) {
        this(false, metaClass);
    }

    RegionFlag(boolean playerToggleable) {
        this(playerToggleable, null);
    }

    RegionFlag() {
        this(false, null);
    }

    /**
     * @return true if this flag can be modified using the /claimtoggle command.
     */
    public boolean isPlayerToggleable() {
        return playerToggleable;
    }

    /**
     * @return the metadata class associated with this flag.
     */
    public Class<? extends FlagMeta> getMetaClass() {
        return metaClass;
    }

    /**
     * @return true if this flag is a boolean flag, meaning the meta class is equal to <code>boolean.class</code>.
     */
    public boolean isBoolean() {
        return metaClass == null;
    }

    /**
     * Returns this flag's default value for regions. This value returned by this method can by modified in the config.
     *
     * @param <T> the meta type.
     * @return this flag's default value for regions.
     */
    @SuppressWarnings("unchecked")
    public <T> T getRegionDefaultValue() {
        return (T) DEFAULT_VALUES.get(this).getFirst();
    }

    /**
     * Returns this flag's default value for the world. This value will be equal to the functional vanilla value so that
     * the global flag container does not cause conflicts based on configured defaults.
     *
     * @param world the world to get the default value for.
     * @param <T>   the meta type.
     * @return this flag's default value for the world.
     */
    @SuppressWarnings("unchecked")
    public <T> T getWorldDefaultValue(World world) {
        Pair<Object, Function<World, Object>> def = DEFAULT_VALUES.get(this);
        return def.getSecond() == null ? (T) def.getFirst() : (T) def.getSecond().apply(world);
    }

    /**
     * Converts the given meta to a human-readable string based on the given flag the meta is associated with.
     *
     * @param flag the flag.
     * @param meta the associated metadata.
     * @return the given meta in human-readable string from.
     */
    public static String toString(RegionFlag flag, Object meta) {
        return flag.isBoolean() ? ((boolean) meta ? "allow" : "deny") : meta.toString();
    }

    /**
     * Parses the given meta string based on the associated flag, and returns the flag metadata derived from the given
     * string.
     *
     * @param flag       the flag associated with the given metadata in string form.
     * @param metaString the string to parse.
     * @return the flag metadata resulting from the given string.
     * @throws IllegalArgumentException if the given metadata string is invalid in some way. The message of this
     *                                  exception will give details as to what the error was.
     */
    public static Object metaFromString(RegionFlag flag, String metaString) throws IllegalArgumentException {
        if (flag.isBoolean()) {
            metaString = metaString.trim();
            if ("allow".equalsIgnoreCase(metaString) || "yes".equalsIgnoreCase(metaString) ||
                    "true".equalsIgnoreCase(metaString)) {
                return true;
            } else if ("deny".equalsIgnoreCase(metaString) || "no".equalsIgnoreCase(metaString) ||
                    "false".equalsIgnoreCase(metaString)) {
                return false;
            } else
                throw new IllegalArgumentException("Invalid flag value: " + metaString);
        } else {
            FlagMeta meta = ReflectionHelper.instantiateWithDefaultParams(flag.getMetaClass());

            // This should never happen
            if (meta == null)
                throw new InternalError();

            meta.readMetaString(metaString);
            return meta;
        }
    }

    /**
     * This method is called upon the enabling of the plugin. Calling this method again while the plugin is enabled
     * could cause a concurrent modification exception, but otherwise would not affect the functionality of the plugin.
     *
     * @param config the config to get default values from.
     */
    public static void registerDefaults(FileConfiguration config) {
        registerDefault(TRUST, TrustMeta.FULL_TRUST);
        registerDefault(DENY_SPAWN, EntityFilter.EMPTY_FILTER);
        registerDefault(DENY_BREAK, MaterialFilter.EMPTY_FILTER);
        registerDefault(DENY_PLACE, MaterialFilter.EMPTY_FILTER);
        registerDefault(ANIMAL_GRIEF_BLOCKS, config.getBoolean("entity.animal-grief-blocks"),
                world -> world.getGameRuleValue(GameRule.MOB_GRIEFING));
        registerDefault(TNT, config.getBoolean("world.tnt-explosions"));
        registerDefault(OVERLAP, false);
        registerDefault(INVINCIBLE, config.getBoolean("region.invincible"), world -> false);
        registerDefault(GREETING, TextMeta.EMPTY_TEXT);
        registerDefault(FAREWELL, TextMeta.EMPTY_TEXT);
        registerDefault(HOSTILE_DAMAGE, config.getBoolean("player.hostile-damage"), world -> true);
        registerDefault(ANIMAL_DAMAGE, config.getBoolean("player.animal-damage"), world -> true);
        registerDefault(POTION_SPLASH, config.getBoolean("region.potion-splash"), world -> true);
        registerDefault(FORCE_CHEST_ACCESS, false);
        registerDefault(PVP, config.getBoolean("player.pvp"),
                world -> ((CraftServer) Bukkit.getServer()).getServer().getDedicatedServerProperties().pvp);
        registerDefault(BED_ENTER, true);
        registerDefault(WATER_FLOW, true);
        registerDefault(LAVA_FLOW, true);
        registerDefault(SNOW_CHANGE, config.getBoolean("world.snow-change"), world -> true);
        registerDefault(ICE_CHANGE, config.getBoolean("world.ice-change"), world -> true);
        registerDefault(CORAL_DEATH, config.getBoolean("world.coral-death"),
                world -> world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED) > 0);
        registerDefault(LEAF_DECAY, config.getBoolean("world.leaf-decay"),
                world -> world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED) > 0);
        registerDefault(LIGHTNING_MOB_DAMAGE, config.getBoolean("world.lightning-mob-damage"), world -> true);
        registerDefault(PORTAL_PAIR_FORMATION, config.getBoolean("world.portal-pair-formation"), world -> true);
        registerDefault(ENTER_COMMAND, CommandMeta.EMPTY_META);
        registerDefault(EXIT_COMMAND, CommandMeta.EMPTY_META);
        registerDefault(DENY_COMMAND, StringFilter.EMPTY_FILTER);
        registerDefault(FOLLOW, true);
        registerDefault(DENY_AGGRO, EntityFilter.EMPTY_FILTER);
        registerDefault(GROWTH, true);
        registerDefault(DENY_BLOCK_USE, MaterialFilter.EMPTY_FILTER);
        registerDefault(KEEP_INVENTORY, false, world -> world.getGameRuleValue(GameRule.KEEP_INVENTORY));
        registerDefault(KEEP_XP, false, world -> world.getGameRuleValue(GameRule.KEEP_INVENTORY));
        registerDefault(RESPAWN_LOCATION, null);
        registerDefault(DENY_ENTITY_USE, EntityFilter.EMPTY_FILTER);
        registerDefault(DENY_ITEM_USE, MaterialFilter.EMPTY_FILTER);
        registerDefault(DENY_WEAPON_USE, MaterialFilter.EMPTY_FILTER);
        registerDefault(FLIGHT, false, world -> Bukkit.getServer().getAllowFlight());
        registerDefault(HOSTILE_GRIEF_BLOCKS, config.getBoolean("entity.hostile-grief-blocks"),
                world -> world.getGameRuleValue(GameRule.MOB_GRIEFING));
        registerDefault(HOSTILE_GRIEF_ENTITIES, config.getBoolean("entity.hostile-grief-entities"),
                world -> world.getGameRuleValue(GameRule.MOB_GRIEFING));
        registerDefault(LIGHTNING_STRIKES, true);
        registerDefault(DENY_ITEM_CONSUMPTION, MaterialFilter.EMPTY_FILTER);
        registerDefault(TNT_IGNITION, true);
        registerDefault(TNT_ENTITY_DAMAGE, true);
        registerDefault(TNT_BLOCK_DAMAGE, true);
        registerDefault(FALL_DAMAGE, true);
        registerDefault(ELYTRA_FLIGHT, true);
        registerDefault(DENY_ENTITY_TELEPORT, EntityFilter.EMPTY_FILTER);
        registerDefault(ANIMAL_CONTAINERS, true);
        registerDefault(ITEM_DAMAGE, true);
        registerDefault(RIPTIDE, true);
        registerDefault(FARMLAND_MOISTURE_CHANGE, true);
    }

    /**
     * Registers the given flag with the given region default value, and given function to get the default value for a
     * world.
     *
     * @param flag          the flag to register default values for.
     * @param regionDefault the region default value.
     * @param worldDefault  the function to get world default values.
     */
    private static void registerDefault(RegionFlag flag, Object regionDefault, Function<World, Object> worldDefault) {
        DEFAULT_VALUES.put(flag, new Pair<>(regionDefault, worldDefault));
    }

    /**
     * Registers the given flag with the given default value, which will be both the region default value and world
     * default value.
     *
     * @param flag  the flag to register the default value for.
     * @param value the default value.
     */
    private static void registerDefault(RegionFlag flag, Object value) {
        registerDefault(flag, value, null);
    }
}
