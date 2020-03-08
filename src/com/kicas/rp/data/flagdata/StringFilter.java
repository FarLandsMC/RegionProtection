package com.kicas.rp.data.flagdata;

import java.util.*;

/**
 * This class is functionally the same as the EnumFilter except rather than filtering enum constants it filters string
 * literals.
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

    @Override
    protected String elementFromString(String s) {
        return s;
    }

    @Override
    protected String elementToString(String e) {
        return e;
    }
}
