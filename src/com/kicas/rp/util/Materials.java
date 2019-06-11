package com.kicas.rp.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Materials {
    // Some useful collections of Materials
    private final static List<Material> INVENTORY_HOLDERS = new ArrayList<>();
    private final static List<Material> PLACEABLES = new ArrayList<>();

    static {
        INVENTORY_HOLDERS.addAll(Arrays.asList(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
                Material.JUKEBOX, Material.CHEST, Material.TRAPPED_CHEST, Material.DROPPER, Material.DISPENSER,
                Material.HOPPER, Material.BREWING_STAND, Material.LECTERN, Material.BARREL, Material.CAMPFIRE,
                Material.COMPOSTER));
        INVENTORY_HOLDERS.addAll(materialsEndingWith("SHULKER_BOX"));
        PLACEABLES.addAll(Arrays.asList(Material.BONE_MEAL, Material.ARMOR_STAND, Material.END_CRYSTAL, Material.FLINT_AND_STEEL));
        PLACEABLES.addAll(materialsEndingWith("BOAT"));
        PLACEABLES.addAll(materialsEndingWith("MINECART"));
    }
    
    /**
     * Collect a group of Materials that have similar endings
     * @param with End of Material names that you want to collect
     * @return A List of Materials which all end with the value passed in
     */
    public static List<Material> materialsEndingWith(String with) {
        return materialsEndingWith(with, Collections.emptyList());
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

    public static boolean isInventoryHolder(Material material) {
        return INVENTORY_HOLDERS.contains(material);
    }

    public static boolean isPlaceable(Material material) {
        return PLACEABLES.contains(material);
    }

    public static boolean isPressurePlate(Material material) {
        return material.name().endsWith("PRESSURE_PLATE");
    }

    public static boolean changesOnInteraction(Material material) {
        return material.isInteractable() && !Material.CRAFTING_TABLE.equals(material);
    }

    public static boolean changesOnUse(Material material, Material tool) {
        if(Material.CAKE.equals(material) || (Material.END_PORTAL_FRAME.equals(material) && Material.ENDER_EYE.equals(tool)))
            return true;
        if(tool.name().endsWith("AXE"))
            return material.name().endsWith("LOG");
        if(tool.name().endsWith("HOE"))
            return material == Material.GRASS_BLOCK || material == Material.DIRT || material == Material.GRASS_PATH;
        return tool.name().endsWith("SHOVEL") && material == Material.GRASS_BLOCK;
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
