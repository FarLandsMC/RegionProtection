package com.kicas.rp.data;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * All of the flags which can be set for a specific region.
 */
public enum RegionFlag {
    TRUST(false, TrustMeta.class),
    DENY_SPAWN(EnumFilter.class),
    DENY_BREAK(EnumFilter.class),
    DENY_PLACE(EnumFilter.class),
    MOB_GRIEF, // Any form of damage caused by non-player, hostile mobs
    TNT_EXPLOSIONS(false),
    OVERLAP, // Regions containing the same locations
    INVINCIBLE,
    GREETING(TextMeta.class),
    HOSTILE_DAMAGE, // Allow players to damage hostiles
    ANIMAL_DAMAGE, // Allow players to damage animals
    POTION_SPLASH, // The actual splash part
    CHEST_ACCESS, // Just chests, trapped chests, and ender chests
    PVP,
    BED_ENTER,
    ENDERMAN_BLOCK_DAMAGE,
    WATER_FLOW,
    LAVA_FLOW,
    SNOW_CHANGE,
    ICE_CHANGE,
    CORAL_DEATH,
    LEAF_DECAY;

    public static final RegionFlag[] VALUES = values();
    private static final Map<RegionFlag, Object> DEFAULT_VALUES = new HashMap<>();

    private final boolean adminOnly;
    private final Class<?> metaClass;

    RegionFlag(boolean adminOnly, Class<?> metaClass) {
        this.adminOnly = adminOnly;
        this.metaClass = metaClass;
    }

    RegionFlag(Class<?> metaClass) {
        this(true, metaClass);
    }

    RegionFlag(boolean adminOnly) {
        this(adminOnly, boolean.class);
    }

    RegionFlag() {
        this(true, boolean.class);
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public Class<?> getMetaClass() {
        return metaClass;
    }

    public boolean isBoolean() {
        return boolean.class.equals(metaClass);
    }

    @SuppressWarnings("unchecked")
    public <T> T getDefaultValue() {
        return (T)DEFAULT_VALUES.get(this);
    }

    // Called when the plugin is enabled
    public static void registerDefaults(FileConfiguration config) {
        DEFAULT_VALUES.put(TRUST, TrustMeta.FULL_TRUST_META);
        DEFAULT_VALUES.put(DENY_SPAWN, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_BREAK, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_PLACE, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(MOB_GRIEF, config.getBoolean("entity.mob-grief"));
        DEFAULT_VALUES.put(TNT_EXPLOSIONS, config.getBoolean("world.tnt-explosions"));
        DEFAULT_VALUES.put(OVERLAP, config.getBoolean("world.overlap"));
        DEFAULT_VALUES.put(INVINCIBLE, config.getBoolean("player.invincible"));
        DEFAULT_VALUES.put(GREETING, TextMeta.EMPTY_TEXT);
        DEFAULT_VALUES.put(HOSTILE_DAMAGE, config.getBoolean("player.hostile-damage"));
        DEFAULT_VALUES.put(ANIMAL_DAMAGE, config.getBoolean("player.animal-damage"));
        DEFAULT_VALUES.put(POTION_SPLASH, config.getBoolean("player.potion-splash"));
        DEFAULT_VALUES.put(CHEST_ACCESS, config.getBoolean("player.chest-access"));
        DEFAULT_VALUES.put(PVP, config.getBoolean("player.pvp"));
        DEFAULT_VALUES.put(BED_ENTER, config.getBoolean("player.bed-enter"));
        DEFAULT_VALUES.put(ENDERMAN_BLOCK_DAMAGE, config.getBoolean("entity.enderman-block-damage"));
        DEFAULT_VALUES.put(WATER_FLOW, config.getBoolean("world.water-flow"));
        DEFAULT_VALUES.put(LAVA_FLOW, config.getBoolean("world.lava-flow"));
        DEFAULT_VALUES.put(SNOW_CHANGE, config.getBoolean("world.snow-change"));
        DEFAULT_VALUES.put(ICE_CHANGE, config.getBoolean("world.ice-change"));
        DEFAULT_VALUES.put(CORAL_DEATH, config.getBoolean("world.coral-death"));
        DEFAULT_VALUES.put(LEAF_DECAY, config.getBoolean("world.leaf-decay"));
    }
}
