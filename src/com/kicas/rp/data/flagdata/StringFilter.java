package com.kicas.rp.data.flagdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is functionally the same as the EnumFilter except rather than filtering enum constants it filters string
 * literals.
 */
public class StringFilter {
    // List of ordinals
    private boolean isWhitelist;
    private final List<String> filter;

    /**
     * Default filter value.
     */
    public static final StringFilter EMPTY_FILTER = new StringFilter(false, Collections.emptyList());

    public StringFilter(boolean isWhitelist, List<String> filter) {
        this.isWhitelist = isWhitelist;
        this.filter = filter;
    }

    public StringFilter(boolean isWhitelist) {
        this(isWhitelist, new ArrayList<>());
    }

    public StringFilter() {
        this(false);
    }

    public boolean isAllowed(String string) {
        return isWhitelist == filter.contains(string);
    }

    public List<String> getFilter() {
        return filter;
    }

    public boolean isWhitelist() {
        return isWhitelist;
    }

    @Override
    public String toString() {
        // ~ = empty filter, IE everything is allowed
        if (!isWhitelist && filter.isEmpty())
            return "~";

        // A whitelist means everything is disallowed with some exceptions, so *,!a,!b
        String base = isWhitelist ? "*" : "";
        // Convert the ordinals to formatted names and apply the formatting
        return filter.isEmpty() ? base : (base.isEmpty() ? "" : "*, ") + String.join(", ", filter.stream()
                .map(string -> (isWhitelist ? "!" : "") + string).toArray(String[]::new));
    }

    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof StringFilter))
            return false;

        StringFilter sf = (StringFilter) other;
        return filter.equals(sf.filter) && isWhitelist == sf.isWhitelist;
    }

    public static StringFilter fromString(String string) {
        if ("~".equals(string))
            return EMPTY_FILTER;

        boolean isWhitelist = string.contains("*");
        StringFilter sf = new StringFilter(isWhitelist);
        for (String element : string.split(",")) {
            element = element.trim();
            // Ignore negation depending on the filter type (! = negation)
            if (!"*".equals(element) && isWhitelist == element.startsWith("!")) {
                // Convert the element into the actual enum name
                sf.filter.add(isWhitelist ? element.substring(1) : element);
            }
        }

        return sf;
    }
}
