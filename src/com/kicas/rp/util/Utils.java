package com.kicas.rp.util;

import java.util.function.Function;

public final class Utils {
    private Utils() { }

    public static int indexOfDefault(int index, int def) {
        return index == -1 ? def : index;
    }

    public static <T> T safeValueOf(Function<String, T> valueOf, String name) {
        try {
            return valueOf.apply(name);
        }catch(Throwable t) {
            return null;
        }
    }

    public static Pair<String, Integer> getEnclosed(int start, String string) {
        boolean curved = string.charAt(start) == '('; // ()s or {}s
        int depth = 1, i = start + 1;
        while(depth > 0) { // Exits when there are no pairs of open brackets
            if(i == string.length()) // Avoid index out of bound errors
                return new Pair<>(null, -1);
            char c = string.charAt(i++);
            if(c == (curved ? ')' : '}')) // We've closed off a pair
                -- depth;
            else if(c == (curved ? '(' : '{')) // We've started a pair
                ++ depth;
        }
        // Return the stuff inside the brackets, and the index of the char after the last bracket
        return new Pair<>(string.substring(start + 1, i - 1), i);
    }
}
