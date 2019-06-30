package com.kicas.rp.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Contains various unrelated utility functions.
 */
public final class Utils {
    public static final UUID UUID_00 = new UUID(0, 0);

    private static final Map<String, String> WORLD_NAME_ALIASES = new HashMap<>();

    static {
        WORLD_NAME_ALIASES.put("overworld", "world");
        WORLD_NAME_ALIASES.put("nether", "world_nether");
        WORLD_NAME_ALIASES.put("the_nether", "world_nether");
        WORLD_NAME_ALIASES.put("world_the_nether", "world_nether");
        WORLD_NAME_ALIASES.put("end", "world_the_end");
        WORLD_NAME_ALIASES.put("the_end", "world_the_end");
        WORLD_NAME_ALIASES.put("world_end", "world_the_end");
    }

    private Utils() {
    }

    public static String doubleToString(double d, int precision) {
        int rounder = 10 * precision;
        return Double.toString(((int)(d * rounder + 0.5)) / (double)rounder);
    }

    public static int constrain(int n, int min, int max) {
        return n < min ? min : (n > max ? max : n);
    }

    /**
     * Attempts to find the proper world name for the given alias. No pattern is necessarily used here, rather common
     * names for the various vanilla worlds are mapped to the correct names. If no world name could be found for the
     * given alias, the given alias is returned.
     *
     * @param alias the alias to map.
     * @return the correct world name for the given alias, or the given alias if no world name could be found.
     */
    public static String getWorldName(String alias) {
        String formattedAlias = alias.toLowerCase();
        if(WORLD_NAME_ALIASES.values().contains(formattedAlias))
            return formattedAlias;

        formattedAlias = formattedAlias.replaceAll("[\\-\\s]", "_");
        return WORLD_NAME_ALIASES.getOrDefault(formattedAlias, alias);
    }

    /**
     * Unformats the given name according to how it was formatted in the formattedName method of this class, and get the
     * enumeration value corresponding to that name. If an enumeration constant could not be found with the unformatted
     * name, then null is returned.
     *
     * @param name the formatted name.
     * @param clazz the enum class.
     * @param <E> the enum type.
     * @return the enumeration constant corresponding to the given formatted name in the given class, or null if no such
     * constant could be found.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E valueOfFormattedName(String name, Class<E> clazz) {
        return (E)safeValueOf(enumName -> ReflectionHelper.invoke("valueOf", clazz, null, enumName),
                name.replaceAll("-", "_").toUpperCase());
    }

    /**
     * Converts the given enumeration element's name (which should be all capitalized with underscores) and replaces the
     * underscores with hyphens and converts the string to lower case.
     *
     * @param e the enumeration element.
     * @return the formatted name of the given element as defined above.
     */
    public static String formattedName(Enum e) {
        return e.name().replaceAll("_", "-").toLowerCase();
    }

    /**
     * Capitalizes each word in the provided string. A word is defined as a cluster of characters separated on either
     * side by spaces or the end or beginning of a string.
     *
     * @param x the string to capitalize.
     * @return the capitalized string.
     */
    public static String capitalize(String x) {
        if (x == null || x.isEmpty())
            return x;
        String[] split = x.split(" ");
        for (int i = 0; i < split.length; ++i) {
            if (!split[i].isEmpty())
                split[i] = Character.toUpperCase(split[i].charAt(0)) + split[i].substring(1).toLowerCase();
        }
        return String.join(" ", split);
    }

    /**
     * Returns the given index if it is not equal to negative one, otherwise it returns the default value.
     *
     * @param index the index.
     * @param def   the default value.
     * @return the given index if it is not equal to negative one, otherwise it returns the default value.
     */
    public static int indexOfDefault(int index, int def) {
        return index == -1 ? def : index;
    }

    /**
     * Returns the value returned by the given function with the given input, or null if the function throws an
     * exception.
     *
     * @param valueOf the value-of function.
     * @param input   the input.
     * @param <T>     the return type.
     * @return the value returned by the given function with the given input, or null if the function throws an
     * exception.
     */
    public static <T> T safeValueOf(Function<String, T> valueOf, String input) {
        try {
            return valueOf.apply(input);
        } catch (Throwable t) {
            return null;
        }
    }
}
