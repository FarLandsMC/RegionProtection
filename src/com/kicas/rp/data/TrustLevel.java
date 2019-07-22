package com.kicas.rp.data;

/**
 * Contains the various levels of trust that can be assigned.
 */
public enum TrustLevel {
    // Used to define the public's trust level in a region if it is none
    NONE,
    // Standard trusts
    ACCESS, CONTAINER, BUILD, MANAGEMENT;

    public static final TrustLevel[] VALUES = values();

    public boolean isAtLeast(TrustLevel other) {
        return ordinal() >= other.ordinal();
    }
}
