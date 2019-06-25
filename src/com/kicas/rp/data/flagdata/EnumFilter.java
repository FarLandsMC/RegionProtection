package com.kicas.rp.data.flagdata;

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
        encoder.writeBoolean(isWhitelist);
        encoder.writeUintCompressed(filter.size());
        for(Integer ordinal : filter)
            encoder.writeUintCompressed(ordinal);
    }
    
    @Override
    public void deserialize(Decoder decoder) throws IOException {
        isWhitelist = decoder.readBoolean();
        int len = decoder.readCompressedUint();
        while(len > 0) {
            filter.add(decoder.readCompressedUint());
            -- len;
        }
    }

    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> String toString(Class<E> clazz) {
        if(!isWhitelist && filter.isEmpty())
            return "~";

        String base = isWhitelist ? "*" : "";
        Enum<E>[] values = (Enum<E>[])ReflectionHelper.invoke("values", clazz, null);
        return filter.isEmpty() ? base : (base.isEmpty() ? "" : "*,") + String.join(", ", filter.stream()
                .map(ordinal -> (isWhitelist ? "!" : "") + Utils.formattedName(values[ordinal]))
                .toArray(String[]::new));
    }

    @Override
    public boolean equals(Object other) {
        if(other == this)
            return true;

        if(!(other instanceof EnumFilter))
            return false;

        EnumFilter ef = (EnumFilter)other;
        return filter.equals(ef.filter) && isWhitelist == ef.isWhitelist;
    }

    public static <E extends Enum<E>> EnumFilter fromString(String string, Class<E> clazz) {
        if("~".equals(string))
            return EMPTY_FILTER;

        boolean isWhitelist = string.contains("*");
        EnumFilter ef = new EnumFilter(isWhitelist);
        for(String element : string.split(",")) {
            element = element.trim();
            // Ignore negation depending on the filter type (! = negation)
            if(!"*".equals(element) && isWhitelist == element.startsWith("!")) {
                // Convert the element into the actual enum name
                String name = isWhitelist ? element.substring(1) : element;

                Enum e = Utils.valueOfFormattedName(name, clazz);
                if(e == null)
                    throw new IllegalArgumentException("Invalid argument: " + element);
                ef.filter.add(e.ordinal());
            }
        }
        return ef;
    }
}
