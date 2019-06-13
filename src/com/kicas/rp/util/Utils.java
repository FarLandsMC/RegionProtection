package com.kicas.rp.util;

import java.util.function.Function;

/**
 * Contains various unrelated utility functions.
 */
public final class Utils {
    private Utils() { }

    /**
     * Returns the given index if it is not equal to negative one, otherwise it returns the default value.
     * @param index the index.
     * @param def the default value.
     * @return the given index if it is not equal to negative one, otherwise it returns the default value.
     */
    public static int indexOfDefault(int index, int def) {
        return index == -1 ? def : index;
    }

    /**
     * Returns the value returned by the given function with the given input, or null if the function throws an
     * exception.
     * @param valueOf the value-of function.
     * @param input the input.
     * @param <T> the return type.
     * @return the value returned by the given function with the given input, or null if the function throws an
     * exception.
     */
    public static <T> T safeValueOf(Function<String, T> valueOf, String input) {
        try {
            return valueOf.apply(input);
        }catch(Throwable t) {
            return null;
        }
    }
}
