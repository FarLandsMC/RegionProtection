package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;

import java.io.IOException;

public enum RegionFlag {
    ACCESS_TRUST(false, ExtendedUuidList.class),
    CONTAINER_TRUST(false, ExtendedUuidList.class),
    BUILD_TRUST(false, ExtendedUuidList.class),
    MANAGEMENT_TRUST(false, ExtendedUuidList.class),
    DENY_SPAWN(EnumFilter.class),
    OVERLAP;

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
        this(adminOnly, BooleanMeta.class);
    }

    RegionFlag() {
        this(true, BooleanMeta.class);
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public Class<? extends Serializable> getMetaClass() {
        return metaClass;
    }

    public static class BooleanMeta implements Serializable {
        public boolean value;

        public BooleanMeta(boolean value) {
            this.value = value;
        }

        @Override
        public void serialize(Encoder encoder) throws IOException {
            encoder.write(value ? 1 : 0);
        }

        @Override
        public void deserialize(Decoder decoder) throws IOException {
            value = decoder.read() == 1;
        }
    }
}
