package com.kicas.rp.data;

import com.kicas.rp.util.Serializable;

import java.util.HashMap;
import java.util.Map;

public class FlagContainer {
    protected final Map<RegionFlag, Serializable> flags;

    public FlagContainer() {
        this.flags = new HashMap<>();
    }

    public boolean hasFlag(RegionFlag flag) {
        return flags.containsKey(flag);
    }

    public boolean isAllowed(RegionFlag flag) {
        return flags.containsKey(flag) ? ((RegionFlag.BooleanMeta)flags.get(flag)).value : flag.getDefaultValue();
    }

    public void setFlag(RegionFlag flag, boolean allow) {
        flags.put(flag, new RegionFlag.BooleanMeta(allow));
    }

    public void setFlag(RegionFlag flag, Serializable meta) {
        flags.put(flag, meta);
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getFlagMeta(RegionFlag flag) {
        return (T)flags.get(flag);
    }

    public Map<RegionFlag, Serializable> getFlags() {
        return flags;
    }
}
