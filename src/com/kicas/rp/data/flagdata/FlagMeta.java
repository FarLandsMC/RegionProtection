package com.kicas.rp.data.flagdata;

public abstract class FlagMeta {
    public abstract void readMetaString(String metaString);

    public abstract String toMetaString();

    @Override
    public final String toString() {
        return toMetaString();
    }
}
