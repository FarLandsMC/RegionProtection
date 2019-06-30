package com.kicas.rp.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import static org.bukkit.Material.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helps to categorize materials.
 */
public class Materials {
    // Some useful collections of Materials
    private static final List<Material> INVENTORY_HOLDERS = new ArrayList<>();
    private static final List<Material> USABLES = new ArrayList<>();
    private static final List<Material> CORALS = new ArrayList<>();

    // Initialize categories
    static {
        INVENTORY_HOLDERS.addAll(Arrays.asList(FURNACE, BLAST_FURNACE, SMOKER, JUKEBOX, CHEST, TRAPPED_CHEST, DROPPER,
                DISPENSER, HOPPER, BREWING_STAND, BARREL, CAMPFIRE, COMPOSTER, CAULDRON));
        INVENTORY_HOLDERS.addAll(materialsEndingWith("SHULKER_BOX"));

        USABLES.addAll(Arrays.asList(BONE_MEAL, ARMOR_STAND, END_CRYSTAL, FLINT_AND_STEEL, PAINTING, ITEM_FRAME));
        USABLES.addAll(materialsEndingWith("BOAT"));
        USABLES.addAll(materialsEndingWith("MINECART"));
        Arrays.asList("CORAL", "CORAL_BLOCK", "CORAL_FAN", "CORAL_WALL_FAN")
                .forEach(c -> CORALS.addAll(materialsEndingWith(c)));
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
     * Returns whether or not the given material is a type of coral, dead or alive.
     *
     * @param material the material
     * @return true if the material is a type of coral, false otherwise.
     */
    public static boolean isCoral(Material material) {
        return CORALS.contains(material);
    }
    
    /**
     * Returns whether or not the given material is sensitive to a player standing on it.
     *
     * @param material the material.
     * @return true if the given material is sensitive to a player standing on it, false otherwise.
     */
    public static boolean isPressureSensitive(Material material) {
        return material == TURTLE_EGG || material == TRIPWIRE ||
                material.name().endsWith("PRESSURE_PLATE");
    }

    /**
     * Returns whether or not the given material could change when being interacted with (right clicked) by a player.
     *
     * @param material the material.
     * @return true if the material could change when being interacted with, false otherwise.
     */
    public static boolean changesOnInteraction(Material material) {
        return material.isInteractable() && material != CRAFTING_TABLE && material != ENCHANTING_TABLE;
    }

    /**
     * Returns whether or not the given material changes when the given tool is used on it.
     *
     * @param material the material.
     * @param tool     the tool.
     * @return true if the given material changes when the given tool is used on it, false otherwise.
     */
    public static boolean changesOnUse(Material material, Material tool) {
        if (CAKE.equals(material) || material.name().endsWith("BUCKET") ||
                (END_PORTAL_FRAME.equals(material) && ENDER_EYE.equals(tool)))
            return true;

        if (tool.name().endsWith("AXE"))
            return material.name().endsWith("LOG");

        if (tool.name().endsWith("HOE"))
            return material == GRASS_BLOCK || material == DIRT || material == GRASS_PATH;

        return tool.name().endsWith("SHOVEL") && material == GRASS_BLOCK;
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
     * Gets the material associated with the given entity. This includes all boat types, minecart types, paintings, item
     * frames, and leash hitches. If the given entity does not have an associated material, then AIR is returned.
     * @param entity the entity.
     * @return the material associated with the given entity, or AIR if not material is associated.
     */
    public static Material forEntity(Entity entity) {
        if(entity instanceof Boat) {
            switch(((Boat)entity).getWoodType()) {
                case GENERIC:
                    return Material.OAK_BOAT;
                case REDWOOD:
                    return Material.SPRUCE_BOAT;
                default:
                    return Material.valueOf(((Boat)entity).getWoodType().name() + "_BOAT");
            }
        }else if(entity instanceof CommandMinecart)
            return Material.COMMAND_BLOCK_MINECART;
        else if(entity instanceof StorageMinecart)
            return Material.CHEST_MINECART;
        else if(entity instanceof ExplosiveMinecart)
            return Material.TNT_MINECART;
        else if(entity instanceof RideableMinecart)
            return Material.MINECART;
        else if(entity instanceof PoweredMinecart)
            return Material.FURNACE_MINECART;
        else if(entity instanceof HopperMinecart)
            return Material.HOPPER_MINECART;
        else if(entity instanceof Painting)
            return Material.PAINTING;
        else if(entity instanceof ItemFrame)
            return Material.ITEM_FRAME;
        else if(entity instanceof LeashHitch)
            return Material.LEAD;
        else
            return Material.AIR;
    }

    /**
     * Returns the material type of the given block, or AIR if the block is null.
     *
     * @param block input block
     * @return the material type of the given block.
     */
    public static Material blockType(Block block) {
        return block == null ? AIR : block.getType();
    }

    /**
     * Returns the type of material in the given item stack, or AIR if the stack is null.
     *
     * @param stack the item stack.
     * @return the type of material in the given item stack.
     */
    public static Material stackType(ItemStack stack) {
        return stack == null ? AIR : stack.getType();
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
