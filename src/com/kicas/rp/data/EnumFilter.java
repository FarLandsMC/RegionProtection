package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EnumFilter implements Serializable {
    private final List<Integer> filter;
    private boolean isWhitelist;
    
    public EnumFilter(List<Integer> filter, boolean isWhitelist) {
        this.filter = filter;
        this.isWhitelist = isWhitelist;
    }

    public EnumFilter() {
        this(new ArrayList<>(), false);
    }
    
    public boolean isAllowed(Enum e) {
        return isWhitelist == filter.contains(e.ordinal());
    }
    
    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeArray(filter);
    }
    
    @Override
    public void deserialize(Decoder decoder) throws IOException {
        filter.addAll(decoder.readArrayAsList(Integer.class));
    }
}
