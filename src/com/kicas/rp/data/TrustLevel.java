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

    /**
     * Returns whether or not the ordinal of this trust level is greater than or equal to the ordinal of the given trust
     * level.
     *
     * @param other the trust level to compare to.
     * @return true if this trust level is at least the give level, false otherwise.
     */
    public boolean isAtLeast(TrustLevel other) {
        return ordinal() >= other.ordinal();
    }
}
