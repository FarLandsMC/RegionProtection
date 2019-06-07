package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EnumFilter implements Serializable {
    private List<Integer> whitelist;
    private List<Integer> blacklist;
    
    public EnumFilter(List<Integer> whitelist, List<Integer> blacklist) {
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }
    public EnumFilter() {
        this(new ArrayList<>(), new ArrayList<>());
    }
    
    public boolean isAllowed(Enum e) {
        return whitelist.contains(e.ordinal());
    }
    
    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeArray(whitelist);
        encoder.writeArray(blacklist);
    }
    
    @Override
    public void deserialize(Decoder decoder) throws IOException {
        whitelist = decoder.readArrayAsList(Integer.class);
        blacklist = decoder.readArrayAsList(Integer.class);
    }
}
