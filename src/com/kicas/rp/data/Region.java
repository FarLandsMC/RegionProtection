package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.ReflectionHelper;
import com.kicas.rp.util.Serializable;
import org.bukkit.Location;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class Region implements Serializable {
    protected final int id;
    protected ExtendedUuid owner;
    protected final Map<RegionFlag, Serializable> flags;

    protected Region(int id, ExtendedUuid owner) {
        this.id = id;
        this.owner = owner;
        this.flags = new HashMap<>();
    }

    public int getId() {
        return id;
    }

    public ExtendedUuid getOwner() {
        return owner;
    }

    public void setOwner(ExtendedUuid owner) {
        this.owner = owner;
    }

    public abstract boolean contains(Location loc);

    public boolean isFlagSet(RegionFlag flag) {
        return flags.containsKey(flag);
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getFlagMeta(RegionFlag flag) {
        return (T)flags.get(flag);
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        owner.serialize(encoder);
        encoder.writeInt(flags.size());
        for(Map.Entry<RegionFlag, Serializable> entry : flags.entrySet()) {
            encoder.write(entry.getKey().ordinal());
            if(entry.getKey().hasMeta())
                entry.getValue().serialize(encoder);
        }
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        owner = new ExtendedUuid();
        owner.deserialize(decoder);
        int len = decoder.readInt();
        while(len > 0) {
            RegionFlag flag = RegionFlag.VALUES[decoder.read()];
            Serializable meta = null;
            if(flag.hasMeta()) {
                meta = ReflectionHelper.instantiate(flag.getMetaClass());
                meta.deserialize(decoder);
            }
            flags.put(flag, meta);
        }
    }
}
