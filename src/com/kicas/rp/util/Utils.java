package com.kicas.rp.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.function.Function;

public final class Utils {
    private Utils() { }

    public static Material blockType(Location location) {
        Block block = location.getBlock();
        return block == null ? Material.AIR : block.getType();
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
