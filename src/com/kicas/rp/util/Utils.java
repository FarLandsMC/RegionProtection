package com.kicas.rp.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.function.Function;

public final class Utils {
    private Utils() { }

    public static Material stackType(ItemStack stack) {
        return stack == null ? Material.AIR : stack.getType();
    }

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
}
