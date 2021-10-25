package com.kicas.rp.data.flagdata;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class defines the outline of a filter, or a set of rules to allow or deny elements of a certain type. A filter
 * can act as a blacklist or whitelist for a set of values of a given type.
 *
 * @param <T> the element type.
 */
public abstract class AbstractFilter<T> extends FlagMeta implements Augmentable<AbstractFilter<T>> {
    protected boolean isWhitelist;
    protected Set<T> filter;

    public static final String ELEMENT_NEGATION = "!";
    public static final String ALL_ELEMENTS = "*";
    public static final String NO_ELEMENTS = "~";

    protected AbstractFilter(boolean isWhitelist, Set<T> filter) {
        this.isWhitelist = isWhitelist;
        this.filter = filter;
    }

    /**
     * Converts the given string form of an element into an element.
     *
     * @param string the string form of an element.
     * @return an elements based off the given string.
     */
    protected abstract T elementFromString(String string);

    /**
     * Converts the given element into a string.
     *
     * @param element the element.
     * @return the string form of the given element.
     */
    protected abstract String elementToString(T element);

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
     * @return a copy of the list containing the explicit values in this filter.
     */
    public Set<T> getFilter() {
        return Collections.unmodifiableSet(filter);
    }

    /**
     * Returns whether or not this filter allows the given element. More specifically, returns true if this filter
     * contains the given element and this filter is a whitelist, or true if this filter does not contain the given
     * element and is a blacklist.
     *
     * @param e the element to test.
     * @return true if the given element is allowed by this filter, false otherwise.
     */
    public boolean isBlocked(T e) {
        return isWhitelist != filter.contains(e);
    }

    /**
     * Ignoring the type of the other filter, this method adds all explicit elements in the other filter to this filter.
     *
     * @param other the other filter.
     */
    public void augment(AbstractFilter<T> other) {
        filter.addAll(other.filter);
    }

    /**
     * Ignoring the type of the other filter, this method removes all explicit elements in the other filter from this
     * filter.
     *
     * @param other the other filter.
     */
    public void reduce(AbstractFilter<T> other) {
        filter.removeAll(other.filter);
    }

    /**
     * Returns true if and only if the given object is an abstract filter, and has the same contents and type (whitelist
     * or blacklist) as this filter.
     *
     * @param other the other object to test.
     * @return true if the given object is an abstract filter and equivalent to this filter, false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof AbstractFilter))
            return false;

        AbstractFilter af = (AbstractFilter) other;
        return filter.equals(af.filter) && isWhitelist == af.isWhitelist;
    }

    /**
     * Converts the given string to a filter of the inferred type. Each individual element should be separated by a
     * comma. If the string contains an asterisk (*), then the resulting filter will be a whitelist and only elements
     * prefixed with ! will be added to the filter. If no asterisks are in the input string, then the resulting filter
     * will be a blacklist filter, and elements prefixed with ! will be ignored. If the given input string is just a
     * tilde (~), then an empty filter will be returned.
     *
     * @param metaString the input string.
     * @throws IllegalArgumentException if a filter element cannot be parsed.
     */
    @Override
    public void readMetaString(String metaString) {
        if (NO_ELEMENTS.equals(metaString))
            return;

        isWhitelist = metaString.contains(ALL_ELEMENTS);
        for (String elementString : metaString.split(",")) {
            elementString = elementString.trim();
            // Ignore negation depending on the filter type (! = negation)
            if (!ALL_ELEMENTS.equals(elementString) && isWhitelist == elementString.startsWith(ELEMENT_NEGATION)) {
                // Convert the element into the actual enum name
                String rawElementString = isWhitelist ? elementString.substring(1) : elementString;
                T element = elementFromString(rawElementString);
                if (element == null)
                    throw new IllegalArgumentException(elementString);
                else
                    filter.add(element);
            }
        }
    }

    /**
     * Converts this string filter to a string. Whitelist filters will be prefixed with a * and all subsequent
     * comma-separated elements will be preceded by a !. For a blacklist filter, simply a list of comma separated string
     * constants is returned.
     *
     * @return a string representation of this string filter.
     */
    @Override
    public String toMetaString() {
        // ~ = empty filter, IE everything is allowed
        if (!isWhitelist && filter.isEmpty())
            return NO_ELEMENTS;

        // A whitelist means everything is disallowed with some exceptions, so *,!a,!b
        String base = isWhitelist ? ALL_ELEMENTS : "";
        // Convert the ordinals to formatted names and apply the formatting
        return filter.isEmpty() ? base : (isWhitelist ? base + ", " : "") + filter.stream().map(this::elementToString)
                .map(string -> (isWhitelist ? ELEMENT_NEGATION : "") + string).sorted().collect(Collectors.joining(", "));
    }
}
