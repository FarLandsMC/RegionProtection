package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Can act as a blacklist or whitelist for any given enum.
 */
public class EnumFilter extends AbstractFilter<Integer> {
    /**
     * Default filter value.
     */
    public static final EnumFilter EMPTY_FILTER = new EnumFilter(false, Collections.emptySet());

    public EnumFilter(boolean isWhitelist, Set<Integer> filter) {
        super(isWhitelist, filter);
    }

    public EnumFilter(boolean isWhitelist) {
        this(isWhitelist, new HashSet<>());
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
        return filter.isEmpty() ? base : (isWhitelist ? base + ", " : "") + filter.stream()
                .map(ordinal -> (isWhitelist ? "!" : "") + Utils.formattedName(values[ordinal]))
                .sorted().collect(Collectors.joining(", "));
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
