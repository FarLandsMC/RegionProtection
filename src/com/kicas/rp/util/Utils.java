package com.kicas.rp.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.function.Function;

/**
 * Contains various unrelated utility functions.
 */
public final class Utils {
    private Utils() { }

    /**
     * Capitalizes each word in the provided string. A word is defined as a cluster of characters separated on either
     * side by spaces or the end or beginning of a string.
     * @param x the string to capitalize.
     * @return the capitalized string.
     */
    public static String capitalize(String x) {
        if(x == null || x.isEmpty())
            return x;
        String[] split = x.split(" ");
        for(int i = 0;i < split.length;++ i) {
            if(!split[i].isEmpty())
                split[i] = Character.toUpperCase(split[i].charAt(0)) + split[i].substring(1).toLowerCase();
        }
        return String.join(" ", split);
    }

    /**
     * Returns the UUID associated with the given username, or null if the given username is invalid.
     * @param username the username.
     * @return the UUID associated with the given username, or null if the given username is invalid.
     */
    @SuppressWarnings("deprecation")
    public static UUID uuidForUsername(String username) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(username);
        // This is the only check that appears to work to validate a username
        return Bukkit.getOfflinePlayer(op.getUniqueId()).getName() == null ? null : op.getUniqueId();
    }

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
