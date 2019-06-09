package com.kicas.rp.data;

import java.util.HashMap;
import java.util.Map;

public class FlagContainer {
    protected final Map<RegionFlag, Object> flags;

    public FlagContainer() {
        this.flags = new HashMap<>();
    }

    public boolean hasFlag(RegionFlag flag) {
        return flags.containsKey(flag);
    }

    public boolean isAllowed(RegionFlag flag) {
        return flags.containsKey(flag) ? (boolean)flags.get(flag) : flag.getDefaultValue();
    }

    public void setFlag(RegionFlag flag, boolean allow) {
        flags.put(flag, allow);
    }

    public void setFlag(RegionFlag flag, Object meta) {
        flags.put(flag, meta);
    }

    @SuppressWarnings("unchecked")
    public <T> T getFlagMeta(RegionFlag flag) {
        return (T)flags.get(flag);
    }

    public Map<RegionFlag, Object> getFlags() {
        return flags;
    }
}
