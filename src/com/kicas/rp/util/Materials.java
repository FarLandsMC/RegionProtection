package com.kicas.rp.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import static org.bukkit.Material.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helps to categorize materials.
 */
public final class Materials {
    // Some useful collections of Materials
    private static final List<Material> INVENTORY_HOLDERS = new ArrayList<>();
    private static final List<Material> PLACEABLES        = new ArrayList<>();
    private static final List<Material> CORALS            = new ArrayList<>();
    private static final List<Material> CONSUMABLES       = new ArrayList<>();
    private static final List<Material> GROWABLES         = new ArrayList<>();

    // Initialize categories
    static {
        INVENTORY_HOLDERS.addAll(Arrays.asList(
                FURNACE, BLAST_FURNACE, SMOKER, JUKEBOX, CHEST, TRAPPED_CHEST, DROPPER, DISPENSER, HOPPER,
                BREWING_STAND, BARREL, COMPOSTER, CAULDRON
        ));
        INVENTORY_HOLDERS.addAll(materialsEndingWith("SHULKER_BOX"));

        PLACEABLES.addAll(Arrays.asList(
                BONE_MEAL, ARMOR_STAND, END_CRYSTAL, FLINT_AND_STEEL, FIRE_CHARGE, PAINTING, ITEM_FRAME
        ));
        PLACEABLES.addAll(materialsEndingWith("BUCKET", Collections.singletonList(MILK_BUCKET)));
        PLACEABLES.addAll(materialsEndingWith("_BOAT"));
        PLACEABLES.addAll(materialsEndingWith("MINECART"));

        Stream.of("CORAL", "CORAL_BLOCK", "CORAL_FAN", "CORAL_WALL_FAN")
                .forEach(c -> CORALS.addAll(materialsEndingWith("_" + c)));

        CONSUMABLES.addAll(Arrays.asList(
                MILK_BUCKET, POTION, CAKE
        ));

        GROWABLES.addAll(Arrays.asList(
                OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING, ACACIA_SAPLING, DARK_OAK_SAPLING,
                BROWN_MUSHROOM, RED_MUSHROOM, CRIMSON_FUNGUS, WARPED_FUNGUS,
                WHEAT, CARROTS, POTATOES, COCOA, NETHER_WART, BEETROOTS, SWEET_BERRY_BUSH,
                CACTUS, SUGAR_CANE, KELP, BAMBOO, VINE, WEEPING_VINES, TWISTING_VINES,
                GRASS_BLOCK, MYCELIUM, CRIMSON_NYLIUM, WARPED_NYLIUM
        ));
    }

    private Materials() {
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
                material.name().endsWith(with) && !except.contains(material)).collect(Collectors.toList());
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
     * Returns whether or not the given material is placeable, and if its usage causes a state change.
     *
     * @param material the material
     * @return true if the material is placeable, false otherwise.
     */
    public static boolean isPlaceable(Material material) {
        return PLACEABLES.contains(material);
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
     * Returns whether or not a player can eat or drink the given material.
     *
     * @param material the material.
     * @return true if the material can be consumed as defined above, false otherwise.
     */
    public static boolean isConsumable(Material material) {
        return material.isEdible() || CONSUMABLES.contains(material);
    }

    /**
     * Returns whether or not the given material is sensitive to a player standing on it.
     *
     * @param material the material.
     * @return true if the given material is sensitive to a player standing on it, false otherwise.
     */
    public static boolean isPressureSensitive(Material material) {
        return material == TURTLE_EGG || material == TRIPWIRE || material == FARMLAND ||
                material.name().endsWith("PRESSURE_PLATE");
    }

    /**
     * Returns whether or not the specified material is able to grow
     *  meaning it can grow into a structure like trees,
     *
     *  advance in height by placing a block of the same type above itself,
     *  advance a stage or
     *  spread to other blocks
     *
     * @param material the material.
     * @return true if the given material can grow, false otherwise.
     */
    public static boolean isGrowable(Material material) {
        return GROWABLES.contains(material);
    }

    /**
     * Returns whether or not the given material changes when the given tool is used on it.
     *
     * @param material the material.
     * @param tool     the tool.
     * @return true if the given material changes when the given tool is used on it, false otherwise.
     */
    public static boolean changesOnUse(Material material, Material tool) {
        if (CAKE == material || material == REPEATER || material == COMPARATOR || material == DAYLIGHT_DETECTOR ||
                END_PORTAL_FRAME == material && ENDER_EYE == tool)
            return true;

        if (tool.name().endsWith("AXE"))
            return material.name().endsWith("LOG");

        if (tool.name().endsWith("HOE"))
            return material == GRASS_BLOCK || material == DIRT || material == GRASS_PATH;

        return (tool.name().endsWith("SHOVEL") && material == GRASS_BLOCK) ||
                (tool.name().endsWith("DYE") && material.name().endsWith("SIGN"));
    }

    /**
     * Returns whether or not the given material has a registered crafting recipe.
     *
     * @param material the material.
     * @return true if a crafting recipe resulting in the given material was found, false otherwise.
     */
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
     *
     * @param entity the entity.
     * @return the material associated with the given entity, or AIR if not material is associated.
     */
    public static Material forEntity(Entity entity) {
        if (entity instanceof Boat) {
            switch (((Boat) entity).getWoodType()) {
                case GENERIC:
                    return Material.OAK_BOAT;
                case REDWOOD:
                    return Material.SPRUCE_BOAT;
                default:
                    return Material.valueOf(((Boat) entity).getWoodType().name() + "_BOAT");
            }
        }
        switch (entity.getType()) {
            case MINECART_COMMAND: return COMMAND_BLOCK_MINECART;
            case MINECART_CHEST: return CHEST_MINECART;
            case MINECART_TNT: return TNT_MINECART;
            case MINECART_FURNACE: return FURNACE_MINECART;
            case MINECART_HOPPER: return HOPPER_MINECART;
            case LEASH_HITCH: return LEAD;
            default:
            {
                Material mat = Utils.safeValueOf(Material::valueOf, entity.getType().name());
                return mat == null ? AIR : mat;
            }
        }
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
        return EquipmentSlot.HAND == hand ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
    }
}
