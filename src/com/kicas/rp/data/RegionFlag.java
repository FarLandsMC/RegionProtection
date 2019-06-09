package com.kicas.rp.data;

import com.kicas.rp.util.Serializable;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public enum RegionFlag {
    ACCESS_TRUST(false, ExtendedUuidList.class),
    CONTAINER_TRUST(false, ExtendedUuidList.class),
    BUILD_TRUST(false, ExtendedUuidList.class),
    MANAGEMENT_TRUST(false, ExtendedUuidList.class),
    DENY_SPAWN(EnumFilter.class),
    OVERLAP;

    public static final RegionFlag[] VALUES = values();
    private static final Map<RegionFlag, Object> DEFAULT_VALUES = new HashMap<>();

    private final boolean adminOnly;
    private final Class<?> metaClass;

    RegionFlag(boolean adminOnly, Class<?> metaClass) {
        this.adminOnly = adminOnly;
        this.metaClass = metaClass;
    }

    RegionFlag(Class<? extends Serializable> metaClass) {
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
        DEFAULT_VALUES.put(ACCESS_TRUST, ExtendedUuidList.EMPTY_LIST);
        DEFAULT_VALUES.put(CONTAINER_TRUST, ExtendedUuidList.EMPTY_LIST);
        DEFAULT_VALUES.put(BUILD_TRUST, ExtendedUuidList.EMPTY_LIST);
        DEFAULT_VALUES.put(MANAGEMENT_TRUST, ExtendedUuidList.EMPTY_LIST);
        DEFAULT_VALUES.put(DENY_SPAWN, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(OVERLAP, false);
    }
}
