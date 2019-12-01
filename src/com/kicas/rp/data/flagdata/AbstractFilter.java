package com.kicas.rp.data.flagdata;

import java.util.Collections;
import java.util.Set;

public abstract class AbstractFilter<T> implements Augmentable<AbstractFilter<T>> {
    protected boolean isWhitelist;
    protected Set<T> filter;

    protected AbstractFilter(boolean isWhitelist, Set<T> filter) {
        this.isWhitelist = isWhitelist;
        this.filter = filter;
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
     * @return a copy of the list containing the explicit values in this filter.
     */
    public Set<T> getFilter() {
        return Collections.unmodifiableSet(filter);
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
}
