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

    /**
     * Returns whether or not this filter allows the given string constant. More specifically, returns true if this
     * filter contains the given string constant and this filter is a whitelist, or true if this filter does not contain
     * the given string constant and is a blacklist.
     *
     * @param string the string to test.
     * @return true if the given string is allowed by this filter, false otherwise.
     */
    public boolean isAllowed(String string) {
        return isWhitelist == filter.contains(string);
    }

    /**
     * Returns true if this filter is a whitelist, or false if this filter is a blacklist. A whitelist filter only
     * allows a certain set of elements while a blacklist disallows a certain set of items.
     *
     * @return true if this filter is a whitelist, or false if this filter is a blacklist.
     */
    public boolean isWhitelist() {
        return isWhitelist;
    }

    /**
     * @return a copy of the list containing the string constants in this filter.
     */
    public List<String> getFilter() {
        return Collections.unmodifiableList(filter);
    }

    /**
     * Converts this string filter to a string. Whitelist filters will be prefixed with a * and all subsequent
     * comma-separated elements will be preceded by a !. For a blacklist filter, simply a list of comma separated string
     * constants is returned.
     *
     * @return a string representation of this string filter.
     */
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

    /**
     * Returns true if and only if the given object is a string filter, and has the same contents and type (whitelist or
     * blacklist) as this filter.
     *
     * @param other the other object to test.
     * @return true if the given object is an string filter and equivalent to this filter, false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof StringFilter))
            return false;

        StringFilter sf = (StringFilter) other;
        return filter.equals(sf.filter) && isWhitelist == sf.isWhitelist;
    }

    /**
     * Converts the given string to an string filter. Each individual element should be separated by a comma. If the
     * string contains an asterisk (*), then the resulting filter will be a whitelist and only elements prefixed with !
     * will be added to the filter. If no asterisks are in the input string, then the resulting filter will be a
     * blacklist filter, and elements prefixed with ! will be ignored. If the given input string is just a tilde (~),
     * then an empty filter will be returned.
     *
     * @param string the input string.
     * @return the string filter resulting from the given string.
     */
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
