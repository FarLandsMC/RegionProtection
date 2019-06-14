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
    MOB_GRIEF,
    TNT_EXPLOSIONS(false),
    OVERLAP,
    // TODO: Implement flags below this comment
    INVINCIBLE,
    GREETING,
    HOSTILE_DAMAGE, // Allow players to damage hostiles
    ANIMAL_DAMAGE, // Allow players to damage animals
    POTION_SPLASH,
    CHEST_ACCESS, // Just chests, trapped chests, and ender chests
    PVP,
    BED_ENTER,
    ENDERMAN_BLOCK_DAMAGE,
    WATER_FLOW,
    LAVA_FLOW,
    SNOW_MELT,
    ICE_MELT,
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
        DEFAULT_VALUES.put(MOB_GRIEF, false);
        DEFAULT_VALUES.put(TNT_EXPLOSIONS, false);
        DEFAULT_VALUES.put(OVERLAP, false);
    }
}
