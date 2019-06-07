package com.kicas.rp.data;

import com.kicas.rp.util.Serializable;

public enum RegionFlag {
    ACCESS_TRUST(false, ExtendedUuidList.class),
    CONTAINER_TRUST(false, ExtendedUuidList.class),
    BUILD_TRUST(false, ExtendedUuidList.class),
    MANAGEMENT_TRUST(false, ExtendedUuidList.class),
    DENY_SPAWN(EnumFilter.class);

    public static final RegionFlag[] VALUES = values();

    private final boolean adminOnly;
    private final Class<? extends Serializable> metaClass;

    RegionFlag(boolean adminOnly, Class<? extends Serializable> metaClass) {
        this.adminOnly = adminOnly;
        this.metaClass = metaClass;
    }

    RegionFlag(Class<? extends Serializable> metaClass) {
        this(true, metaClass);
    }

    RegionFlag(boolean adminOnly) {
        this(adminOnly, null);
    }

    RegionFlag() {
        this(true, null);
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public Class<? extends Serializable> getMetaClass() {
        return metaClass;
    }

    public boolean hasMeta() {
        return metaClass != null;
    }
}
