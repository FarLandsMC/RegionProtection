package com.kicas.rp.data.flagdata;

import java.util.*;

/**
 * Can act as a blacklist or whitelist for strings.
 */
public class StringFilter extends AbstractFilter<String> {
    public static final StringFilter EMPTY_FILTER = new StringFilter();

    public StringFilter(boolean isWhitelist, Set<String> filter) {
        super(isWhitelist, filter);
    }

    public StringFilter(boolean isWhitelist) {
        this(isWhitelist, new HashSet<>());
    }

    public StringFilter() {
        this(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String elementFromString(String string) {
        return string;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String elementToString(String element) {
        return element;
    }
}
