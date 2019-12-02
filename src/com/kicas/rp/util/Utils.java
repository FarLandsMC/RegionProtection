package com.kicas.rp.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Arrays;
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

    /**
     * Converts the given floating point number to a string and then truncates the decimal point to the given precision.
     *
     * @param d         the float point to convert to a string.
     * @param precision the number of decimal point the resulting string should have.
     * @return a string form of the given floating point number, with the decimal point truncated to the given
     * precision.
     */
    public static String doubleToString(double d, int precision) {
        String fp = Double.toString(d);
        return fp.contains(".") ? fp.substring(0, Math.min(fp.lastIndexOf('.') + precision + 1, fp.length())) +
                (fp.contains("E") ? fp.substring(fp.lastIndexOf('E')) : "") : fp;
    }

    /**
     * Constrains the given number between the given minimum and maximum value. If the given number n is outside the
     * given range then the closest bound is returned.
     *
     * @param n   the number to constrain.
     * @param min the minimum bound.
     * @param max the maximum bound.
     * @return the constrained number.
     * @throws IllegalArgumentException if the given maximum bound is less than the given minimum bound.
     */
    public static int constrain(int n, int min, int max) {
        if (max < min)
            throw new IllegalArgumentException("The maximum bound cannot be less than the minimum bound.");
        return Math.max(min, Math.min(n, max));
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
        if (WORLD_NAME_ALIASES.containsValue(formattedAlias))
            return formattedAlias;

        formattedAlias = formattedAlias.replaceAll("[\\-\\s]", "_");
        return WORLD_NAME_ALIASES.getOrDefault(formattedAlias, alias);
    }

    /**
     * Un-formats the given name according to how it was formatted in the formattedName method of this class, and get
     * the enumeration value corresponding to that name. If an enumeration constant could not be found with the
     * unformatted name, then null is returned.
     *
     * @param name  the formatted name.
     * @param clazz the enum class.
     * @param <E>   the enum type.
     * @return the enumeration constant corresponding to the given formatted name in the given class, or null if no such
     * constant could be found.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E valueOfFormattedName(String name, Class<E> clazz) {
        return (E) safeValueOf(enumName -> ReflectionHelper.invoke("valueOf", clazz, null, enumName),
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

    /**
     * If the block given block would damage the player when placed at their foot level or eye level
     *
     * @param block the block to check
     * @return if a player can safely stand inside this block
     */
    private static boolean doesDamage(Block block) {
        return block.getType().isSolid() || block.isLiquid() ||
                Arrays.asList(Material.FIRE, Material.CACTUS).contains(block.getType());
    }

    /**
     * If a player would be teleported to the location of this block, confirm they cannot:
     * fall through or take damage
     * unless the block water
     *
     * @param block the block to check
     * @return if the block can be stood on without the block damaging the player
     */
    private static boolean canStand(Block block) {
        return !(
                block.isPassable() ||
                        Arrays.asList(Material.MAGMA_BLOCK, Material.CACTUS).contains(block.getType())
        ) || block.getType().equals(Material.WATER);
    }

    /**
     * If a player would be teleported to this location, confirm they cannot:
     * take damage from the block at    the location (body)
     * take damage from the block above the location (head)
     *
     * @param location the location to check
     * @return if a player would be damaged by any of the blocks at this location upon tp ignoring the block below
     */
    private static boolean isSafe(Location location) {
        return !(
                doesDamage(location.add(0, 1, 0).getBlock()) ||
                        doesDamage(location.add(0, 1, 0).getBlock())
        );
    }

    /**
     * Search for a safe location for a player to stand when teleporting to another player
     * Set the location to be in the center of the block ready for teleportation
     * Calculate reasonable bottom and top values to throw into the private method that completes the implementation
     *
     * @param origin location to check for safety
     * @return a safe location if found, else null
     */
    public static Location findSafe(Location origin) {
        origin.setX(origin.getBlockX() + .5);
        origin.setZ(origin.getBlockZ() + .5);
        return findSafe(origin, Math.max(1, origin.getBlockY() - 8), Math.min(origin.getBlockY() + 7, 255));
    }

    /**
     * Search for a safe location for a player to stand
     * We search a column at the x z of the origin
     * The algorithm treats this column as being sorted with ground at the bottom and sky above it
     * Because of this we can make assumptions that the ground will always have:
     * 2 non damaging blocks above (air|vines|etc) and
     * 1 block the player can stand on below (non passable and non damaging)
     * Because bottom and top specify search range for Y they should satisfy these ranges:
     * 0 < bottom < top < 256   for the overworld
     * 0 < bottom < top < 124   for the nether (124 instead of 128 because the bedrock roof guarantees unsafe tp)
     *
     * @param origin the location to check for safety
     * @param bottom where to set the bottom of the "binary search", 0 < bottom < top
     * @param top    where to set the top    of the "binary search", bottom < top < 256
     * @return a safe location if found, else null
     */
    private static Location findSafe(Location origin, int bottom, int top) {
        Location safe = origin.clone();

        if (canStand(safe.getBlock()) && isSafe(safe.clone()))
            return safe.add(0, .5, 0);

        do {
            safe.setY((bottom + top) >> 1);

            if (canStand(safe.getBlock())) {
                if (isSafe(safe.clone()))
                    return safe.add(0, 1, 0);

                bottom = safe.getBlockY();
            } else
                top = safe.getBlockY();
        } while (top - bottom > 1);

        safe.getChunk().unload();
        return null;
    }

    /**
     * Search for a safe location using findSafe starting at and including origin in steps of dx dz where
     * these values are multiplied by 16 when there is Liquid at y62 as this usually indicates being in an ocean and
     * oceans provide a lower probability for findSafe to return a non null location.
     *
     * @param origin the location to start searching from
     * @param dx     the x offset we tend towards
     * @param dz     the x offset we tend towards
     * @return the first safe location we find
     */
    public static Location walk(Location origin, int dx, int dz) {
        origin.setX(origin.getBlockX() + .5);
        origin.setZ(origin.getBlockZ() + .5);
        final int bottom = 1,
                top = origin.getWorld().getName().equals("world_nether") ? 124 : 255;

        Location safe = findSafe(origin, bottom, top);
        if (safe != null)
            return safe;

        origin.setY(62);
        while (safe == null) {
            if (origin.getBlock().isLiquid())
                origin.add(16 * dx, 0, 16 * dz);
            else
                safe = findSafe(origin.add(dx, 0, dz), bottom, top);
        }
        return safe;
    }
}
