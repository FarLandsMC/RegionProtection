package com.kicas.rp.data;

import com.kicas.rp.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StringFilter implements Serializable {
    // List of ordinals
    private final List<String> filter;
    private boolean isWhitelist;

    /**
     * Default filter value.
     */
    public static final StringFilter EMPTY_FILTER = new StringFilter(false);

    public StringFilter(boolean isWhitelist) {
        this.filter = new ArrayList<>();
        this.isWhitelist = isWhitelist;
    }

    public StringFilter() {
        this(false);
    }

    public boolean isAllowed(String string) {
        return isWhitelist == filter.contains(string);
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeBoolean(isWhitelist);
        encoder.writeArray(filter, String.class);
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        isWhitelist = decoder.readBoolean();
        filter.addAll(decoder.readArrayAsList(String.class));
    }

    @Override
    public String toString() {
        String base = isWhitelist ? "*" : "";
        return filter.isEmpty() ? base : (base.isEmpty() ? "" : "*,") + String.join(", ", filter.stream().map(string ->
                (isWhitelist ? "!" : "") + string).toArray(String[]::new));
    }

    @Override
    public boolean equals(Object other) {
        if(other == this)
            return true;

        if(!(other instanceof StringFilter))
            return false;

        StringFilter sf = (StringFilter)other;
        return filter.equals(sf.filter) && isWhitelist == sf.isWhitelist;
    }

    public static StringFilter fromString(String string) {
        // * = all
        boolean isWhitelist = string.contains("*");
        StringFilter sf = new StringFilter(isWhitelist);
        for(String element : string.split(",")) {
            element = element.trim();
            // Ignore negation depending on the filter type (! = negation)
            if(!"*".equals(element) && isWhitelist == element.startsWith("!")) {
                // Convert the element into the actual enum name
                sf.filter.add(isWhitelist ? element.substring(1) : element);
            }
        }
        return sf;
    }
}
