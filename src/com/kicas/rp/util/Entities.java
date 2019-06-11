package com.kicas.rp.util;

import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;

public class Entities {
    
    private final static List<EntityType> PLACEABLES = Arrays.asList(EntityType.LEASH_HITCH, EntityType.PAINTING);
    private final static List<EntityType> INVENTORY_HOLDERS = Arrays.asList(EntityType.MINECART_CHEST, EntityType.MINECART_FURNACE,
            EntityType.MINECART_HOPPER, EntityType.HORSE, EntityType.MULE, EntityType.SKELETON_HORSE,
            EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, EntityType.LLAMA, EntityType.TRADER_LLAMA,
            EntityType.DONKEY, EntityType.ZOMBIE_HORSE);
    private final static List<EntityType> INTERACTABLES = Arrays.asList(EntityType.SHEEP, EntityType.COW,
            EntityType.MUSHROOM_COW, EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.BOAT,
            EntityType.MINECART, EntityType.TURTLE, EntityType.CHICKEN, EntityType.CAT, EntityType.FOX,
            EntityType.OCELOT, EntityType.PANDA, EntityType.PARROT, EntityType.PIG, EntityType.RABBIT, EntityType.WOLF);
    
    public static boolean isPlaceable(EntityType entityType) {
        return PLACEABLES.contains(entityType);
    }
    public static boolean isInventoryHolder(EntityType entityType) {
        return INVENTORY_HOLDERS.contains(entityType);
    }
    public static boolean isInteractable(EntityType entityType) {
        return INTERACTABLES.contains(entityType);
    }
}
