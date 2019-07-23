package com.kicas.rp.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import static org.bukkit.entity.EntityType.*;
import static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.*;

import java.util.Arrays;
import java.util.List;

/**
 * Helps to categorize entities.
 */
public final class Entities {
    private static final List<EntityType> INVENTORY_HOLDERS = Arrays.asList(ARMOR_STAND, ITEM_FRAME,
            HORSE, MULE, SKELETON_HORSE, DONKEY, ZOMBIE_HORSE, LLAMA, TRADER_LLAMA,
            MINECART_CHEST, MINECART_FURNACE, MINECART_HOPPER);
    private static final List<EntityType> INTERACTABLES = Arrays.asList(SHEEP, COW, MUSHROOM_COW, VILLAGER,
            WANDERING_TRADER, TRADER_LLAMA, TURTLE, CHICKEN, CAT, FOX, OCELOT, PANDA, PARROT, PIG, RABBIT, WOLF);
    private static final List<EntityType> HOSTILES = Arrays.asList(WITHER_SKELETON, WITHER, SILVERFISH, ENDERMAN,
            CAVE_SPIDER, SPIDER, VINDICATOR, WITCH, SLIME, CREEPER, BLAZE, ZOMBIE, SKELETON, EntityType.DROWNED,
            ELDER_GUARDIAN, ENDER_DRAGON, ENDERMITE, EVOKER, GHAST, GIANT, GUARDIAN, HUSK, ILLUSIONER, MAGMA_CUBE,
            PHANTOM, PILLAGER, RAVAGER, SHULKER, STRAY, VEX, ZOMBIE_VILLAGER);
    private static final List<EntityType> AGGERABLES = Arrays.asList(POLAR_BEAR, IRON_GOLEM, PIG_ZOMBIE, WOLF, SNOWMAN);
    private static final List<EntityType> PASSIVES = Arrays.asList(CAT, CHICKEN, COW, DONKEY, DOLPHIN, FOX, HORSE,
            LLAMA, MUSHROOM_COW, MULE, OCELOT, PANDA, PARROT, PIG, RABBIT, SHEEP, TURTLE,
            WANDERING_TRADER, TRADER_LLAMA, VILLAGER);
    private static final List<CreatureSpawnEvent.SpawnReason> ARTIFICIAL_SPAWN_REASONS = Arrays.asList(SPAWNER,
            SPAWNER_EGG, BUILD_SNOWMAN, BUILD_IRONGOLEM, BUILD_WITHER, BREEDING, DISPENSE_EGG, CUSTOM, DEFAULT);

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

    /**
     * Returns whether or not the given entity is hostile with respect the griefing capability.
     * Only returns true for regular hostiles.
     *
     * @param entityType the entity type.
     * @return true if the given entity is generally hostile.
     */
    public static boolean isMonster(EntityType entityType) {
        return HOSTILES.contains(entityType);
    }

    /**
     * Returns whether or not the given entity is passive towards the given player. If the given entity is always
     * passive towards players then true is returned, otherwise if the entity has to be provoked then trust is only
     * returned if the given entity is not currently targeting the given player.
     * (can't !isHostile() because of paintings etc)
     *
     * @param player the player.
     * @param entity the entity.
     * @return true if the given entity is passive towards the given player, false otherwise.
     */
    public static boolean isPassive(Player player, Entity entity) {
        return PASSIVES.contains(entity.getType()) || !entity.getType().isAlive() ||
                (AGGERABLES.contains(entity.getType()) && !player.equals(((Mob) entity).getTarget()));
    }

    /**
     * Returns whether or not the given entity is passive with respect the griefing capability.
     * Returns true for regular passives or aggerables.
     *
     * @param entityType the entity type.
     * @return true if the given entity is generally passive.
     */
    public static boolean isPassive(EntityType entityType) {
        return AGGERABLES.contains(entityType) || PASSIVES.contains(entityType) || !entityType.isAlive();
    }

    /**
     * Returns whether or not the given spawn reason is artificial, IE caused by a player.
     *
     * @param reason the spawn reason.
     * @return true if the given spawn reason is artificial, false otherwise.
     */
    public static boolean isArtificialSpawn(CreatureSpawnEvent.SpawnReason reason) {
        return ARTIFICIAL_SPAWN_REASONS.contains(reason);
    }
}
