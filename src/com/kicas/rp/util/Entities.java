package com.kicas.rp.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Helps to categorize entities.
 */
public class Entities {
    private static final List<EntityType> INVENTORY_HOLDERS = Arrays.asList(EntityType.MINECART_CHEST,
            EntityType.MINECART_FURNACE, EntityType.MINECART_HOPPER, EntityType.HORSE, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, EntityType.LLAMA,
            EntityType.TRADER_LLAMA, EntityType.DONKEY, EntityType.ZOMBIE_HORSE);
    private static final List<EntityType> INTERACTABLES = Arrays.asList(EntityType.SHEEP, EntityType.COW,
            EntityType.MUSHROOM_COW, EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.TURTLE,
            EntityType.CHICKEN, EntityType.CAT, EntityType.FOX, EntityType.OCELOT, EntityType.PANDA, EntityType.PARROT,
            EntityType.PIG, EntityType.RABBIT, EntityType.WOLF);
    private static final List<EntityType> HOSTILES = Arrays.asList(EntityType.WITHER_SKELETON, EntityType.WITHER,
            EntityType.SILVERFISH, EntityType.ENDERMAN, EntityType.CAVE_SPIDER, EntityType.SPIDER,
            EntityType.VINDICATOR, EntityType.WITCH, EntityType.SLIME, EntityType.CREEPER, EntityType.BLAZE,
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.DROWNED, EntityType.ELDER_GUARDIAN,
            EntityType.ENDER_DRAGON, EntityType.ENDERMITE, EntityType.EVOKER, EntityType.GHAST, EntityType.GIANT,
            EntityType.GUARDIAN, EntityType.HUSK, EntityType.ILLUSIONER, EntityType.MAGMA_CUBE, EntityType.PHANTOM,
            EntityType.PILLAGER, EntityType.RAVAGER, EntityType.SHULKER, EntityType.STRAY, EntityType.VEX,
            EntityType.ZOMBIE_VILLAGER);
    private static final List<EntityType> AGGERABLES = Arrays.asList(EntityType.POLAR_BEAR, EntityType.IRON_GOLEM,
            EntityType.PIG_ZOMBIE, EntityType.WOLF, EntityType.SNOWMAN);

    private Entities() {
    }

    /**
     * Returns whether or not the specified entity type is an inventory holder, meaning it can store items after its
     * inventory is closed.
     *
     * @param entityType the entity type.
     * @return true if the given entity type is an inventory holder, false otherwise.
     */
    public static boolean isInventoryHolder(EntityType entityType) {
        return INVENTORY_HOLDERS.contains(entityType);
    }

    /**
     * Returns whether or not the given entity type can be right clicked causing some state change. This does not
     * include entities which are considered to be inventory holders.
     *
     * @param entityType the entity type.
     * @return true if the given entity type is interactable, false otherwise.
     */
    public static boolean isInteractable(EntityType entityType) {
        return INTERACTABLES.contains(entityType);
    }

    /**
     * Returns whether or not the given entity is hostile towards the given player. If the given entity is always
     * hostile towards players then true is returned, otherwise if the entity has to be provoked then trust is only
     * returned if the given entity is currently targeting the given player.
     *
     * @param player the player.
     * @param entity the entity.
     * @return true if the given entity is hostile towards the given player, false otherwise.
     */
    public static boolean isHostile(Player player, Entity entity) {
        return HOSTILES.contains(entity.getType()) ||
                (AGGERABLES.contains(entity.getType()) && player.equals(((Mob) entity).getTarget()));
    }
}
