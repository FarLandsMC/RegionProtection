package com.kicas.rp.data;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public enum RegionFlag {
    TRUST(false, TrustMeta.class),
    DENY_SPAWN(EnumFilter.class),
    DENY_BREAK(EnumFilter.class),
    DENY_PLACE(EnumFilter.class),
    OVERLAP;

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

    public static void registerDefaults(FileConfiguration config) {
        DEFAULT_VALUES.put(TRUST, TrustMeta.EMPTY_TRUST_META);
        DEFAULT_VALUES.put(DENY_SPAWN, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_BREAK, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_PLACE, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(OVERLAP, false);
    }
}
