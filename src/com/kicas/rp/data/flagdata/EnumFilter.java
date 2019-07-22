package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Can act as a blacklist or whitelist for any given enum.
 */
public class EnumFilter {
    private boolean isWhitelist;
    // List of ordinals
    private final List<Integer> filter;

    /**
     * Default filter value.
     */
    public static final EnumFilter EMPTY_FILTER = new EnumFilter(false, Collections.emptyList());

    public EnumFilter(boolean isWhitelist, List<Integer> filter) {
        this.isWhitelist = isWhitelist;
        this.filter = filter;
    }

    public EnumFilter(boolean isWhitelist) {
        this(isWhitelist, new ArrayList<>());
    }

    public EnumFilter() {
        this(false);
    }

    /**
     * Returns whether or not this filter allows the given enum constant. More specifically, returns true if this filter
     * contains the given enum constant and this filter is a whitelist, or true if this filter does not contain the
     * given enum constant and is a blacklist.
     *
     * @param e the enum constant to test.
     * @return true if the given enum constant is allowed by this filter, false otherwise.
     */
    public boolean isAllowed(Enum e) {
        return isWhitelist == filter.contains(e.ordinal());
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
     * @return a copy of the list containing the enum ordinals in this filter.
     */
    public List<Integer> getFilterCopy() {
        return new ArrayList<>(filter);
    }

    /**
     * Converts this enum filter to a string using the given enum class. Whitelist filters will be prefixed with a * and
     * all subsequent comma-separated elements will be preceded by a !. For a blacklist filter, simply a list of comma
     * separated, formatted enum constant names is returned.
     *
     * @param clazz the enum class.
     * @param <E>   the enum type.
     * @return a string representation of this enum filter.
     */
    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> String toString(Class<E> clazz) {
        // ~ = empty filter, IE everything is allowed
        if (!isWhitelist && filter.isEmpty())
            return "~";

        // A whitelist means everything is disallowed with some exceptions, so *,!a,!b
        String base = isWhitelist ? "*" : "";
        // Convert the ordinals to formatted names and apply the formatting
        Enum<E>[] values = (Enum<E>[]) ReflectionHelper.invoke("values", clazz, null);
        return filter.isEmpty() ? base : (base.isEmpty() ? "" : "*, ") + String.join(", ", filter.stream()
                .map(ordinal -> (isWhitelist ? "!" : "") + Utils.formattedName(values[ordinal]))
                .toArray(String[]::new));
    }

    /**
     * Returns true if and only if the given object is an enum filter, and has the same contents and type (whitelist or
     * blacklist) as this filter.
     *
     * @param other the other object to test.
     * @return true if the given object is an enum filter and equivalent to this filter, false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof EnumFilter))
            return false;

        EnumFilter ef = (EnumFilter) other;
        return filter.equals(ef.filter) && isWhitelist == ef.isWhitelist;
    }

    /**
     * Converts the given string to an enum filter using the given enum class to convert formatted enum names to
     * ordinals. Each individual element should be separated by a comma. If the string contains an asterisk (*), then
     * the resulting filter will be a whitelist and only elements prefixed with ! will be added to the filter. If no
     * asterisks are in the input string, then the resulting filter will be a blacklist filter, and elements prefixed
     * with ! will be ignored. If the given input string is just a tilde (~), then an empty filter will be returned.
     *
     * @param string the input string.
     * @param clazz  the enum class.
     * @param <E>    the enum type.
     * @return the enum filter resulting from the given string.
     * @throws IllegalArgumentException if a filter element cannot be matched to an enum ordinal.
     * @see com.kicas.rp.util.Utils#formattedName(Enum) Formatted enum constand names.
     */
    public static <E extends Enum<E>> EnumFilter fromString(String string, Class<E> clazz) {
        if ("~".equals(string))
            return EMPTY_FILTER;

        boolean isWhitelist = string.contains("*");
        EnumFilter ef = new EnumFilter(isWhitelist);
        for (String element : string.split(",")) {
            element = element.trim();
            // Ignore negation depending on the filter type (! = negation)
            if (!"*".equals(element) && isWhitelist == element.startsWith("!")) {
                // Convert the element into the actual enum name
                String name = isWhitelist ? element.substring(1) : element;

                // Check the given enum name
                Enum e = Utils.valueOfFormattedName(name, clazz);
                if (e == null)
                    throw new IllegalArgumentException("Invalid argument: " + element);

                ef.filter.add(e.ordinal());
            }
        }

        return ef;
    }
}
