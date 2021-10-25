package com.kicas.rp.data.flagdata;

/**
 * This class is the root for all flag metadata classes. This class requires that each flag metadata type has methods to
 * convert in and out of some serialized string form.
 */
public abstract class FlagMeta {
    /**
     * Updates the flag metadata based on the given string.
     *
     * @param metaString the metadata in string form.
     */
    public abstract void readMetaString(String metaString) throws IllegalArgumentException;

    /**
     * Converts the flag metadata into string form.
     *
     * @return the string form of the flag metadata.
     */
    public abstract String toMetaString();

    /**
     * @see FlagMeta#toMetaString() toMetaString
     */
    @Override
    public final String toString() {
        return toMetaString();
    }
}
