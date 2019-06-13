package com.kicas.rp.data;

import com.kicas.rp.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Can act as a blacklist or whitelist for any given enum.
 */
public class EnumFilter implements Serializable {
    // List of ordinals
    private final List<Integer> filter;
    private boolean isWhitelist;

    /**
     * Default filter value.
     */
    public static final EnumFilter EMPTY_FILTER = new EnumFilter(false);
    
    public EnumFilter(boolean isWhitelist) {
        this.filter = new ArrayList<>();
        this.isWhitelist = isWhitelist;
    }

    public EnumFilter() {
        this(false);
    }
    
    public boolean isAllowed(Enum e) {
        return isWhitelist == filter.contains(e.ordinal());
    }
    
    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.write(isWhitelist ? 1 : 0);
        encoder.writeArray(filter);
    }
    
    @Override
    public void deserialize(Decoder decoder) throws IOException {
        isWhitelist = decoder.read() == 1;
        filter.addAll(decoder.readArrayAsList(Integer.class));
    }

    public static EnumFilter fromString(String string, Class<? extends Enum<?>> clazz) {
        // * = all
        boolean isWhitelist = string.contains("*");
        EnumFilter ef = new EnumFilter(isWhitelist);
        for(String element : string.split(",")) {
            element = element.trim();
            // Ignore negation depending on the filter type (! = negation)
            if(!"*".equals(element) && isWhitelist == element.startsWith("!")) {
                Enum e = Utils.safeValueOf(str -> (Enum)ReflectionHelper.invoke("valueOf", clazz, null, str),
                        isWhitelist ? element.substring(1) : element);
                if(e == null)
                    throw new IllegalArgumentException("Invalid argument: " + element);
                ef.filter.add(e.ordinal());
            }
        }
        return ef;
    }
}
