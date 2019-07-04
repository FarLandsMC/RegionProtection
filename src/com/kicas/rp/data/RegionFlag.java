package com.kicas.rp.data;

import com.kicas.rp.data.flagdata.*;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * All of the flags which can be set for a specific region.
 */
public enum RegionFlag {
    TRUST(false, TrustMeta.class),
    DENY_SPAWN(EnumFilter.class),
    DENY_BREAK(EnumFilter.class),
    DENY_PLACE(EnumFilter.class),
    MOB_GRIEF, // Any form of damage caused by non-player, hostile mobs
    TNT(false),
    OVERLAP, // Regions containing the same locations
    INVINCIBLE,
    GREETING(TextMeta.class),
    FAREWELL(TextMeta.class),
    HOSTILE_DAMAGE, // Allow players to damage hostiles
    ANIMAL_DAMAGE, // Allow players to damage animals
    POTION_SPLASH, // The actual splash part
    FORCE_CHEST_ACCESS, // Just chests, trapped chests, and ender chests. Only checked if it's explicitly set.
    PVP,
    BED_ENTER,
    WATER_FLOW,
    LAVA_FLOW,
    SNOW_CHANGE,
    ICE_CHANGE,
    CORAL_DEATH,
    LEAF_DECAY,
    LIGHTNING_MOB_DAMAGE,
    PORTAL_PAIR_FORMATION,
    ENTER_COMMAND(CommandMeta.class),
    EXIT_COMMAND(CommandMeta.class),
    DENY_COMMAND(StringFilter.class),
    FOLLOW, // prevent pet tp
    DENY_AGGRO(EnumFilter.class), // prevent certain mobs from targeting the player
    GROWTH, // vine growth grass spread etc
    DENY_BLOCK_USE(EnumFilter.class),
    KEEP_INVENTORY,
    KEEP_XP,
    RESPAWN_LOCATION(LocationMeta.class),
    DENY_ENTITY_USE(EnumFilter.class),
    DENY_ITEM_USE(EnumFilter.class),
    DENY_WEAPON_USE(EnumFilter.class);

    public static final RegionFlag[] VALUES = values();
    private static final Map<RegionFlag, Object> DEFAULT_VALUES = new HashMap<>();

    private final boolean adminOnly;
    private final Class<?> metaClass;

    RegionFlag(boolean adminOnly, Class<?> metaClass) {
        this.adminOnly = adminOnly;
        this.metaClass = metaClass;
    }

    RegionFlag(Class<?> metaClass) {
        this(true, metaClass);
    }

    RegionFlag(boolean adminOnly) {
        this(adminOnly, boolean.class);
    }

    RegionFlag() {
        this(true, boolean.class);
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public Class<?> getMetaClass() {
        return metaClass;
    }

    public boolean isBoolean() {
        return boolean.class.equals(metaClass);
    }

    @SuppressWarnings("unchecked")
    public <T> T getDefaultValue() {
        return (T)DEFAULT_VALUES.get(this);
    }

    public static String toString(RegionFlag flag, Object meta) {
        String metaString;

        if(flag.isBoolean())
            metaString = (boolean)meta ? "allow" : "deny";
        else if(flag == DENY_SPAWN || flag == DENY_AGGRO || flag == DENY_ENTITY_USE)
            metaString = ((EnumFilter)meta).toString(EntityType.class);
        else if(flag == DENY_PLACE || flag == DENY_BREAK || flag == DENY_BLOCK_USE || flag == DENY_ITEM_USE ||
                flag == DENY_WEAPON_USE)
            metaString = ((EnumFilter)meta).toString(Material.class);
        else
            metaString = meta.toString();

        return metaString;
    }

    public static Object metaFromString(RegionFlag flag, String metaString) throws IllegalArgumentException {
        switch (flag) {
            case TRUST:
                return TrustMeta.fromString(metaString);

            case DENY_SPAWN:
            case DENY_AGGRO:
            case DENY_ENTITY_USE:
                return EnumFilter.fromString(metaString, EntityType.class);

            case DENY_BREAK:
            case DENY_PLACE:
            case DENY_BLOCK_USE:
            case DENY_ITEM_USE:
                return EnumFilter.fromString(metaString, Material.class);

            case GREETING:
            case FAREWELL:
                // The TextUtils.SyntaxException is caught by the execution method wrapping this method
                return new TextMeta(metaString);

            case ENTER_COMMAND:
            case EXIT_COMMAND: {
                int index = metaString.indexOf(':');
                if (index < 0)
                    throw new IllegalArgumentException("Invalid command format. Format: <console|player>:<command>");

                String sender = metaString.substring(0, index);
                if ("console".equalsIgnoreCase(sender))
                    return new CommandMeta(true, metaString.substring(index + 1));
                else if ("player".equalsIgnoreCase(sender))
                    return new CommandMeta(false, metaString.substring(index + 1));
                else
                    throw new IllegalArgumentException("Invalid sender: " + sender);
            }

            case DENY_COMMAND:
                return StringFilter.fromString(metaString);

            // x, y, z, yaw, pitch, world
            case RESPAWN_LOCATION: {
                String[] args = metaString.split(" ");

                if(args.length == 1)
                    throw new IllegalArgumentException("Please provide a y and z value.");
                else if(args.length == 2)
                    throw new IllegalArgumentException("Please provide a z value.");

                double x, y, z;
                try {
                    x = Double.parseDouble(args[0]);
                    y = Double.parseDouble(args[1]);
                    z = Double.parseDouble(args[2]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Failed to parse coordinate values.");
                }

                if(args.length == 3)
                    return new LocationMeta(Bukkit.getWorld("world"), x, y, z);

                if(args.length == 4) {
                    World world = Bukkit.getWorld(Utils.getWorldName(args[3]));
                    if(world == null)
                        throw new IllegalArgumentException("Invalid world name: " + args[3]);
                    return new LocationMeta(world, x, y, z);
                }

                float yaw, pitch;
                try {
                    yaw = Float.parseFloat(args[3]);
                    pitch = Float.parseFloat(args[4]);
                }catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Failed to parse rotation values.");
                }

                if(args.length == 5)
                    return new LocationMeta(Bukkit.getWorld("world"), x, y, z, yaw, pitch);

                World world = Bukkit.getWorld(Utils.getWorldName(args[5]));
                if(world == null)
                    throw new IllegalArgumentException("Invalid world name: " + args[5]);

                return new LocationMeta(world, x, y, z, yaw, pitch);
            }

            default:
                metaString = metaString.trim();
                if ("allow".equalsIgnoreCase(metaString) || "yes".equalsIgnoreCase(metaString) ||
                        "true".equalsIgnoreCase(metaString)) {
                    return true;
                } else if ("deny".equalsIgnoreCase(metaString) || "no".equalsIgnoreCase(metaString) ||
                        "false".equalsIgnoreCase(metaString)) {
                    return false;
                } else
                    throw new IllegalArgumentException("Invalid flag value: " + metaString);
        }
    }

    // Called when the plugin is enabled
    public static void registerDefaults(FileConfiguration config) {
        DEFAULT_VALUES.put(TRUST, TrustMeta.FULL_TRUST);
        DEFAULT_VALUES.put(DENY_SPAWN, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_BREAK, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_PLACE, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(MOB_GRIEF, config.getBoolean("entity.mob-grief"));
        DEFAULT_VALUES.put(TNT, config.getBoolean("world.tnt-explosions"));
        DEFAULT_VALUES.put(OVERLAP, false);
        DEFAULT_VALUES.put(INVINCIBLE, false);
        DEFAULT_VALUES.put(GREETING, TextMeta.EMPTY_TEXT);
        DEFAULT_VALUES.put(FAREWELL, TextMeta.EMPTY_TEXT);
        DEFAULT_VALUES.put(HOSTILE_DAMAGE, true);
        DEFAULT_VALUES.put(ANIMAL_DAMAGE, true);
        DEFAULT_VALUES.put(POTION_SPLASH, true);
        DEFAULT_VALUES.put(FORCE_CHEST_ACCESS, false);
        DEFAULT_VALUES.put(PVP, config.getBoolean("player.pvp"));
        DEFAULT_VALUES.put(BED_ENTER, true);
        DEFAULT_VALUES.put(WATER_FLOW, true);
        DEFAULT_VALUES.put(LAVA_FLOW, true);
        DEFAULT_VALUES.put(SNOW_CHANGE, config.getBoolean("world.snow-change"));
        DEFAULT_VALUES.put(ICE_CHANGE, config.getBoolean("world.ice-change"));
        DEFAULT_VALUES.put(CORAL_DEATH, config.getBoolean("world.coral-death"));
        DEFAULT_VALUES.put(LEAF_DECAY, config.getBoolean("world.leaf-decay"));
        DEFAULT_VALUES.put(LIGHTNING_MOB_DAMAGE, config.getBoolean("world.lightning-mob-damage"));
        DEFAULT_VALUES.put(PORTAL_PAIR_FORMATION, config.getBoolean("world.portal-pair-formation"));
        DEFAULT_VALUES.put(ENTER_COMMAND, CommandMeta.EMPTY_META);
        DEFAULT_VALUES.put(EXIT_COMMAND, CommandMeta.EMPTY_META);
        DEFAULT_VALUES.put(DENY_COMMAND, StringFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(FOLLOW, true);
        DEFAULT_VALUES.put(DENY_AGGRO, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(GROWTH, true);
        DEFAULT_VALUES.put(DENY_BLOCK_USE, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(KEEP_INVENTORY, false);
        DEFAULT_VALUES.put(KEEP_XP, false);
        DEFAULT_VALUES.put(RESPAWN_LOCATION, null);
        DEFAULT_VALUES.put(DENY_ENTITY_USE, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_ITEM_USE, EnumFilter.EMPTY_FILTER);
        DEFAULT_VALUES.put(DENY_WEAPON_USE, EnumFilter.EMPTY_FILTER);
    }
}
