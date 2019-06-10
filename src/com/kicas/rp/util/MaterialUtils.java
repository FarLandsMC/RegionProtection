package com.kicas.rp.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MaterialUtils {
    // Some useful collections of Materials
    public final static List<Material> CONTAINERS = Arrays.asList(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.JUKEBOX, Material.CHEST, Material.TRAPPED_CHEST, Material.DROPPER, Material.DISPENSER,
            Material.HOPPER, Material.BREWING_STAND, Material.LECTERN, Material.BARREL, Material.CAMPFIRE, Material.COMPOSTER);
    public final static List<Material> SHULKERS = materialsEndingWith("SHULKER_BOX");
    
    public final static List<Material> BUTTONS = materialsEndingWith("BUTTON"); // doesn't include levers
    public final static List<Material> DOORS = materialsEndingWith("DOOR",
            Collections.singletonList(Material.IRON_DOOR));
    public final static List<Material> TRAPDOORS = materialsEndingWith("TRAPDOOR",
            Collections.singletonList(Material.IRON_TRAPDOOR));
    public final static List<Material> GATES = materialsEndingWith("FENCE_GATE");
    
    public final static List<Material> BEDS = materialsEndingWith("BED");
    public final static List<Material> COMPONENTS = Arrays.asList(Material.REPEATER, Material.COMPARATOR,
            Material.NOTE_BLOCK, Material.DAYLIGHT_DETECTOR);
    public final static List<Material> PLACEABLES = Arrays.asList(Material.BONE_MEAL, Material.ARMOR_STAND,
            Material.END_CRYSTAL, Material.FLINT_AND_STEEL);
    public final static List<Material> BOATS = materialsEndingWith("BOAT");
    public final static List<Material> MINECARTS = materialsEndingWith("MINECART");
    public final static List<Material> RAILS = materialsEndingWith("RAIL");
    
    /**
     * Collect a group of Materials that have similar endings
     * @param with End of Material names that you want to collect
     * @return A List of Materials which all end with the value passed in
     */
    public static List<Material> materialsEndingWith(String with) {
        return materialsEndingWith(with, null);
    }
    /**
     * Collect a group of Materials that have similar endings ignoring certain Materials
     * @param with End of Material names that you want to collect
     * @param except A List of Materials you want to ignore when collecting similar Materials
     * @return A List of Materials which all end with the value passed in minus exception Materials
     */
    public static List<Material> materialsEndingWith(String with, List<Material> except) {
        List<Material> materialList = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && material.name().endsWith("_" + with) && !except.contains(material))
                materialList.add(material);
        }
        return materialList;
    }
    
    /**
     * A bit of consistency
     * @param block input block
     * @return Air if null, else block
     */
    public static Material getMaterial(Block block) {
        return block == null ? Material.AIR : block.getType();
    }
}
