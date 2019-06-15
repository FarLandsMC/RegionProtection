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
        encoder.writeBoolean(isWhitelist);
        encoder.writeArray(filter, Integer.class);
    }
    
    @Override
    public void deserialize(Decoder decoder) throws IOException {
        isWhitelist = decoder.readBoolean();
        filter.addAll(decoder.readArrayAsList(Integer.class));
    }

    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> String toString(Class<E> clazz) {
        String base = isWhitelist ? "*" : "";
        Enum<E>[] values = (Enum<E>[])ReflectionHelper.invoke("values", clazz, null);
        return filter.isEmpty() ? base : (base.isEmpty() ? "" : "*,") + String.join(", ", filter.stream().map(ordinal ->
                (isWhitelist ? "!" : "") + Utils.formattedName(values[ordinal])).toArray(String[]::new));
    }

    public static <E extends Enum<E>> EnumFilter fromString(String string, Class<E> clazz) {
        // * = all
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
