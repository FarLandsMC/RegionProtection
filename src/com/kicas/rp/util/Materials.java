package com.kicas.rp.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helps to categorize materials.
 */
public class Materials {
    // Some useful collections of Materials
    private static final List<Material> INVENTORY_HOLDERS = new ArrayList<>();
    private static final List<Material> USABLES = new ArrayList<>();

    // Initialize categories
    static {
        INVENTORY_HOLDERS.addAll(Arrays.asList(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
                Material.JUKEBOX, Material.CHEST, Material.TRAPPED_CHEST, Material.DROPPER, Material.DISPENSER,
                Material.HOPPER, Material.BREWING_STAND, Material.LECTERN, Material.BARREL, Material.CAMPFIRE,
                Material.COMPOSTER));
        INVENTORY_HOLDERS.addAll(materialsEndingWith("SHULKER_BOX"));

        USABLES.addAll(Arrays.asList(Material.BONE_MEAL, Material.ARMOR_STAND, Material.END_CRYSTAL,
                Material.FLINT_AND_STEEL, Material.PAINTING, Material.ITEM_FRAME));
        USABLES.addAll(materialsEndingWith("BOAT"));
        USABLES.addAll(materialsEndingWith("MINECART"));
    }

    /**
     * Collect a group of Materials that have similar endings.
     *
     * @param with End of Material names that should be collected.
     * @return a List of Materials which all end with the value passed in.
     */
    public static List<Material> materialsEndingWith(String with) {
        return materialsEndingWith(with, Collections.emptyList());
    }

    /**
     * Collect a group of Materials that have similar endings ignoring certain Materials
     *
     * @param with   end of Material names that should be collected.
     * @param except a List of Materials that should be ignored when collecting similar Materials
     * @return a List of Materials which all end with the value passed in minus exception Materials.
     */
    @SuppressWarnings("deprecation")
    public static List<Material> materialsEndingWith(String with, List<Material> except) {
        return Arrays.stream(Material.values()).filter(material -> !material.isLegacy() &&
                material.name().endsWith("_" + with) && !except.contains(material)).collect(Collectors.toList());
    }

    /**
     * Returns whether or not the specified material is an inventory holder, meaning it can store items after its
     * inventory is closed.
     *
     * @param material the material.
     * @return true if the given material is an inventory holder, false otherwise.
     */
    public static boolean isInventoryHolder(Material material) {
        return INVENTORY_HOLDERS.contains(material);
    }

    /**
     * Returns whether or not the given material is usable, and if its usage causes a state change.
     *
     * @param material the material
     * @return true if the material is usable, false otherwise.
     */
    public static boolean isUsable(Material material) {
        return USABLES.contains(material);
    }

    /**
     * Returns whether or not the given material is sensitive to a player standing on it.
     *
     * @param material the material.
     * @return true if the given material is sensitive to a player standing on it, false otherwise.
     */
    public static boolean isPressureSensitive(Material material) {
        return material == Material.TURTLE_EGG || material == Material.TRIPWIRE ||
                material.name().endsWith("PRESSURE_PLATE");
    }

    /**
     * Returns whether or not the given material could change when being interacted with (right clicked) by a player.
     *
     * @param material the material.
     * @return true if the material could change when being interacted with, false otherwise.
     */
    public static boolean changesOnInteraction(Material material) {
        return material.isInteractable() && !Material.CRAFTING_TABLE.equals(material);
    }

    /**
     * Returns whether or not the given material changes when the given tool is used on it.
     *
     * @param material the material.
     * @param tool     the tool.
     * @return true if the given material changes when the given tool is used on it, false otherwise.
     */
    public static boolean changesOnUse(Material material, Material tool) {
        if (Material.CAKE.equals(material) || (Material.END_PORTAL_FRAME.equals(material) &&
                Material.ENDER_EYE.equals(tool))) {
            return true;
        }

        if (tool.name().endsWith("AXE"))
            return material.name().endsWith("LOG");

        if (tool.name().endsWith("HOE"))
            return material == Material.GRASS_BLOCK || material == Material.DIRT || material == Material.GRASS_PATH;

        return tool.name().endsWith("SHOVEL") && material == Material.GRASS_BLOCK;
    }

    public static boolean hasRecipe(Material material) {
        Iterator<Recipe> itr = Bukkit.getServer().recipeIterator();
        while (itr.hasNext()) {
            if (itr.next().getResult().getType() == material)
                return true;
        }
        return false;
    }

    /**
     * Returns the material type of the given block, or AIR if the block is null.
     *
     * @param block input block
     * @return the material type of the given block.
     */
    public static Material blockType(Block block) {
        return block == null ? Material.AIR : block.getType();
    }

    /**
     * Returns the type of material in the given item stack, or AIR if the stack is null.
     *
     * @param stack the item stack.
     * @return the type of material in the given item stack.
     */
    public static Material stackType(ItemStack stack) {
        return stack == null ? Material.AIR : stack.getType();
    }

    /**
     * Returns the item the player is holding in the given hand.
     *
     * @param player the player.
     * @param hand   the hand.
     * @return the item the player is holding in the given hand.
     */
    public static ItemStack heldItem(Player player, EquipmentSlot hand) {
        return EquipmentSlot.HAND.equals(hand) ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
    }
}
