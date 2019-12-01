package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.data.flagdata.Augmentable;
import com.kicas.rp.util.Materials;
import com.kicas.rp.util.ReflectionHelper;
import com.kicas.rp.util.TextUtils;
import com.kicas.rp.util.Utils;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.craftbukkit.v1_14_R1.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Allows administrators to register and delete regions, as well as modify and view their flags. The size of a region
 * can also be adjusted through this command as well.
 */
public class CommandRegion extends TabCompleterBase implements CommandExecutor {
    // For tab completion
    private static final List<String> SUB_COMMANDS = Arrays.asList("flag", "create", "expand", "retract", "delete",
            "info", "rename", "set-priority");
    private static final List<String> ALLOW_DENY = Arrays.asList("allow", "deny");
    private static final List<String> EXPANSION_DIRECTIONS = Arrays.asList("vert", "top", "bottom", "north", "south",
            "east", "west");

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Args check
        if (args.length < 1)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Check the sub-command
        args[0] = args[0].toLowerCase();
        if (!SUB_COMMANDS.contains(args[0])) {
            sender.sendMessage(ChatColor.RED + "Invalid sub-command: " + args[0]);
            return false;
        }

        // Show info about the regions the player is standing in, a specific region (by name), or the global flags
        if ("info".equals(args[0])) {
            // Show information about the flags set in the player's world
            if (args.length > 1 && DataManager.GLOBAL_FLAG_NAME.equals(args[1])) {
                // Get the flags and detail them
                FlagContainer worldFlags = RegionProtection.getDataManager().getWorldFlags(((Player) sender).getWorld());

                // No flags -> skip formatting
                if (worldFlags.isEmpty()) {
                    sender.sendMessage(ChatColor.GOLD + "There are no global flags set in this world.");
                    return true;
                }

                TextUtils.sendFormatted(sender, "&(gold)Showing global flag info:\nFlags:\n%0",
                        formatFlags(worldFlags));

                return true;
            }

            // Get the list of regions to detail
            List<Region> regions;
            if (args.length == 1) // Get the regions the player is standing in
                regions = RegionProtection.getDataManager().getRegionsAt(((Player) sender).getLocation());
            else { // Get the region specified by name
                Region region = RegionProtection.getDataManager().getRegionByName(((Player) sender).getWorld(), args[1]);

                // Invalid region name
                if (region == null) {
                    sender.sendMessage(ChatColor.RED + "Could not find a region with name \"" + args[1] +
                            "\" in your world.");
                    return true;
                }

                regions = Collections.singletonList(region);
            }

            // No regions were found
            if (regions.isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "There are no regions present at your location.");
                return true;
            }

            // Detail the regions
            regions.forEach(region -> {
                TextUtils.sendFormatted(
                        sender,
                        "&(gold)Showing info for region {&(green)%0:}\nPriority: {&(aqua)%1}\nParent: {&(aqua)%2}%3%4",
                        region.getDisplayName(),
                        region.getPriority(),
                        region.hasParent() ? region.getParent().getDisplayName() : "none",
                        region.getCoOwners().isEmpty() ? "" : "\nCo-Owners: {&(gray)" + region.getCoOwners().stream()
                                .map(RegionProtection.getDataManager()::currentUsernameForUuid)
                                .collect(Collectors.joining(", ")) + "}",
                        region.isEmpty() ? "" : "\nFlags:\n" + formatFlags(region)
                );
            });

            return true;
        }

        // All commands beyond this point require at lest two args
        if (args.length == 1)
            return false;

        // These args are tagged, IE order does not matter and each value is prefixed with the pertinent field. The only
        // fields for the create sub-command are priority and parent.
        if ("create".equals(args[0])) {
            PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);

            if (ps.getCurrentSelectedRegion() == null) {
                sender.sendMessage(ChatColor.RED + "Please outline the region you wish to create before using this " +
                        "command.");
                return true;
            }

            // Fields to potentially overwrite
            int priority = 0;
            String parentName = null;
            boolean force = false;

            // Skip the sub-command and region name and evaluate the rest of the arguments
            for (int i = 2; i < args.length; ++i) {
                // Parse the priority
                if (args[i].toLowerCase().startsWith("priority:")) {
                    String priorityString = args[i].substring(args[i].indexOf(':') + 1);
                    try {
                        priority = Integer.parseInt(priorityString);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Invalid priority: " + priorityString);
                        return true;
                    }

                    if (priority < 0 || priority > 127) {
                        sender.sendMessage(ChatColor.RED + "Region priorities must be between 0 and 127 inclusive.");
                        return true;
                    }
                }
                // Get the parent region's name
                else if (args[i].toLowerCase().startsWith("parent:"))
                    parentName = args[i].substring(args[i].indexOf(':') + 1);
                    // Whether or not to forcefully create the region (IE ignore overlap)
                else if (args[i].equalsIgnoreCase("force"))
                    force = true;
                    // Skip invalid tags
                else
                    sender.sendMessage(ChatColor.RED + "Ignoring argument \"" + args[i] + "\" since it is invalid.");
            }

            // Attempt to register the region (parent name checked here)
            if (RegionProtection.getDataManager().tryRegisterRegion((Player) sender, ps.getCurrentSelectedRegion(),
                    args[1], priority, parentName, force)) {
                sender.sendMessage(ChatColor.GREEN + "Created region " + args[1] + " with a priority of " +
                        ps.getCurrentSelectedRegion().getPriority() + " and " + (parentName == null ? "no parent."
                        : "parent " + parentName));
            }

            return true;
        }

        // Flag sub-command
        if ("flag".equals(args[0])) {
            // Tertiary args check
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /region flag <name> <flag> [value]");
                return true;
            }

            // Get the flag container being modified
            FlagContainer flags = DataManager.GLOBAL_FLAG_NAME.equals(args[1])
                    ? RegionProtection.getDataManager().getWorldFlags(((Player) sender).getWorld())
                    : RegionProtection.getDataManager().getRegionByName(((Player) sender).getWorld(), args[1]);

            // Invalid region name
            if (flags == null) {
                sender.sendMessage(ChatColor.RED + "Could not find a region with name \"" + args[1] + "\" in your world.");
                return true;
            }

            // See the enum for details
            FlagModification modification = FlagModification.forFlagString(args[2]);

            // Get and check the flag in question
            RegionFlag flag = Utils.valueOfFormattedName(modification == FlagModification.SET_OR_NONE ? args[2]
                    : args[2].substring(1), RegionFlag.class);
            if (flag == null) {
                sender.sendMessage(ChatColor.RED + "Invalid flag: " + args[2]);
                return true;
            }

            if ((modification == FlagModification.AUGMENT || modification == FlagModification.REDUCE) &&
                    !Augmentable.class.isAssignableFrom(flag.getMetaClass())) {
                sender.sendMessage(ChatColor.RED + "This flag cannot be augmented or reduced.");
                return true;
            }

            // We're deleting the flag
            if (modification == FlagModification.DELETE) {
                flags.deleteFlag(flag);
                sender.sendMessage(ChatColor.GREEN + "Deleted flag " + Utils.formattedName(flag) + " from region " +
                        args[1]);
                return true;
            }

            // If no value is specified, notify the player of the current value
            if (args.length == 3) {
                if (modification == FlagModification.SET_OR_NONE) {
                    String metaString = RegionFlag.toString(flag, flags.getFlagMeta(flag));
                    sender.sendMessage(ChatColor.GOLD + Utils.formattedName(flag) + " in region " + args[1] +
                            " is set to: " + ChatColor.GRAY + (metaString.contains("\n") ? "\n" + metaString : metaString));
                } else
                    sender.sendMessage(ChatColor.RED + "A flag value must be specified to augment a flag.");
                return true;
            }

            // More arguments means there is a value that should be evaluated as done below
            Object meta;
            try {
                // Parse/evaluate the meta string
                meta = RegionFlag.metaFromString(flag, joinArgsBeyond(2, " ", args));
            } catch (IllegalArgumentException | TextUtils.SyntaxException ex) { // Thrown by the meta parsers
                sender.sendMessage(ChatColor.RED + ex.getMessage());
                return true;
            }

            // Regular set
            if (modification == FlagModification.SET_OR_NONE)
                flags.setFlag(flag, meta);
            else { // Augmentation or reduction
                // This requires metadata to already exist
                Object currentMeta = flags.getFlagMeta(flag);
                if (currentMeta == null) {
                    sender.sendMessage(ChatColor.RED + "This flag cannot be augmented or reduced since no current value exists.");
                    return true;
                }

                // This should never happen
                if (!Augmentable.class.isAssignableFrom(currentMeta.getClass()) ||
                        !meta.getClass().equals(currentMeta.getClass())) {
                    throw new InternalError();
                }

                // Perform the operation
                if (modification == FlagModification.AUGMENT)
                    ((Augmentable) currentMeta).augment(meta);
                else
                    ((Augmentable) currentMeta).reduce(meta);
            }

            sender.sendMessage(ChatColor.GREEN + "Updated flag value for region " + args[1]);
            return true;
        }

        // Check to make sure the region name is valid
        Region region = RegionProtection.getDataManager().getRegionByName(((Player) sender).getWorld(), args[1]);
        if (region == null) {
            if (DataManager.GLOBAL_FLAG_NAME.equals(args[1]))
                sender.sendMessage(ChatColor.RED + "This operation cannot be performed on the global flag set.");
            else {
                sender.sendMessage(ChatColor.RED + "Could not find a region with name \"" + args[1] +
                        "\" in your world.");
            }
            return true;
        }

        // Region size modification
        if ("expand".equals(args[0]) || "retract".equals(args[0])) {
            // Tertiary args check
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /region expand|retract <name> <direction> [value]");
                return true;
            }

            // Vertical extension is a different case, so check for that first
            if ("vert".equalsIgnoreCase(args[2]) || "vertical".equalsIgnoreCase(args[2])) {
                // Retracting vertically doesn't make sense, don't allow it
                if ("retract".equals(args[0])) {
                    sender.sendMessage(ChatColor.RED + "You cannot vertically retract a region without specifying an " +
                            "amount. Use /region retract up/down instead.");
                    return true;
                }

                // Perform the extension
                region.getMin().setY(0);
                region.getMax().setY(region.getWorld().getMaxHeight());

                sender.sendMessage(ChatColor.GREEN + "Extended region to bedrock and world height.");
            } else { // Regular expansion and retraction
                // Final args check
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /region expand|retract <name> <direction> <value>");
                    return true;
                }

                // Parse the side
                BlockFace side;
                if ("top".equalsIgnoreCase(args[2]))
                    side = BlockFace.UP;
                else if ("bottom".equalsIgnoreCase(args[2]))
                    side = BlockFace.DOWN;
                else
                    side = Utils.valueOfFormattedName(args[2], BlockFace.class);

                // Check the side
                if (side == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid direction: " + args[2]);
                    return true;
                }

                // Get and check the amount
                int amount;
                try {
                    amount = Integer.parseInt(args[3]) * ("retract".equals(args[0]) ? -1 : 1);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
                    return true;
                }

                // Expand the region
                if (RegionProtection.getDataManager().tryExpandRegion((Player) sender, region, side, amount))
                    sender.sendMessage(ChatColor.GREEN + "Successfully adjusted region " + args[1]);
            }
        } else if ("rename".equals(args[0])) {
            if (RegionProtection.getDataManager().tryRenameRegion((Player) sender, region, args[2]))
                sender.sendMessage(ChatColor.GREEN + "Renamed region \"" + args[1] + "\" to \"" + args[2] + "\".");
        } else if ("set-priority".equals(args[0])) {
            int newPriority;
            try {
                newPriority = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid priority: " + args[2]);
                return true;
            }

            if (newPriority < 0 || newPriority > 127) {
                sender.sendMessage(ChatColor.RED + "Region priorities must be between 0 and 127 inclusive.");
                return true;
            }

            region.setPriority(newPriority);
            sender.sendMessage(ChatColor.GREEN + "Set region priority to " + newPriority + ".");
        } else if ("delete".equals(args[0])) { // Delete a region
            boolean includeChildren = args.length == 3 && "true".equalsIgnoreCase(args[2]);
            // This can fail if includeChildren is false and the region has children
            if (RegionProtection.getDataManager().tryDeleteRegion((Player) sender, region, includeChildren)) {
                sender.sendMessage(ChatColor.GREEN + "Deleted region " + args[1] + (includeChildren
                        ? " and all child regions." : "."));
            }
        }

        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        // Get a location
        Location location;
        if (sender instanceof Player)
            location = ((Player) sender).getLocation();
        else if (sender instanceof BlockCommandSender)
            location = ((BlockCommandSender) sender).getBlock().getLocation();
            // Add default to prevent errors
        else
            location = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);

        // Show sub-commands
        if (args.length == 1)
            return filterStartingWith(args[0], SUB_COMMANDS);
            // Suggest region names (if applicable)
        else if (args.length == 2) {
            // Don't suggest names if we're creating a region
            if ("create".equalsIgnoreCase(args[0]))
                return Collections.emptyList();
                // Global flags allowed
            else if ("info".equalsIgnoreCase(args[0]) || "flag".equalsIgnoreCase(args[0])) {
                return filterStartingWith(args[1], RegionProtection.getDataManager()
                        .getRegionNames(((Player) sender).getWorld(), true));
            }
            // Global flags aren't allowed
            else {
                return filterStartingWith(args[1], RegionProtection.getDataManager()
                        .getRegionNames(((Player) sender).getWorld(), false));
            }
        }

        // Creation suggestions: suggest the unused tags, and if it's the parent tag then suggest the list of possible
        // parents.
        if ("create".equalsIgnoreCase(args[0])) {
            // Suggest list of possible parents
            if (args[args.length - 1].toLowerCase().startsWith("parent:")) {
                return filterStartingWith(args[args.length - 1], RegionProtection.getDataManager()
                        .getRegionsInWorld(location.getWorld()).stream().map(region -> "parent:" + region.getRawName()));
            }
            // Suggest the tag prefixes that are left
            else if (args[args.length - 1].indexOf(':') < 0) {
                return Stream.of("priority:", "parent:", "force").filter(tag -> {
                    for (int i = 2; i < args.length; ++i) {
                        if (args[i].toLowerCase().startsWith(tag))
                            return false;
                    }
                    return true;
                }).collect(Collectors.toList());
            }
        }
        // Suggest the list of flags
        else if ("flag".equalsIgnoreCase(args[0])) {
            // Flag names
            if (args.length == 3) {
                return filterStartingWith(args[2], Stream.of(RegionFlag.VALUES)
                        .map(flag -> FlagModification.forFlagString(args[2]).marker + Utils.formattedName(flag)));
            }
            // Flag values
            else {
                FlagModification modification = FlagModification.forFlagString(args[2]);
                RegionFlag flag = Utils.valueOfFormattedName(modification == FlagModification.SET_OR_NONE ? args[2]
                        : args[2].substring(1), RegionFlag.class);

                // Invalid flag -> no suggestions
                if (flag == null)
                    return Collections.emptyList();

                // Too complex, not worth giving suggestions for
                if (flag == RegionFlag.TRUST || flag == RegionFlag.GREETING || flag == RegionFlag.FAREWELL)
                    return Collections.emptyList();

                // Special case flag(s) that can accept more that one argument
                if (flag == RegionFlag.RESPAWN_LOCATION) {
                    switch (args.length) {
                        case 4: // x
                            return Collections.singletonList(Integer.toString(location.getBlockX()));
                        case 5: // y
                            return Collections.singletonList(Integer.toString(location.getBlockY()));
                        case 6: // z
                            return Collections.singletonList(Integer.toString(location.getBlockZ()));
                        case 7:
                            // By default suggest the yaw value
                            if (sender instanceof Player && (args[6].isEmpty() || args[6].matches("(-)?[\\d.]+"))) {
                                return Collections.singletonList(Integer.toString((int) ((Player) sender).getLocation()
                                        .getYaw()));
                            }
                            // If the player starts typing a non-number, suggest world values instead
                            else
                                return filterStartingWith(args[6], Bukkit.getWorlds().stream().map(World::getName));
                        case 8: // Pitch if the sender is a player, otherwise suggest world values again
                            // Pitch
                            if (sender instanceof Player) {
                                return Collections.singletonList(Integer.toString((int) ((Player) sender).getLocation()
                                        .getPitch()));
                            }
                            // World values
                            else {
                                return filterStartingWith(args[7], Bukkit.getWorlds().stream().map(World::getName));
                            }
                        case 9: // World values
                            return filterStartingWith(args[8], Bukkit.getWorlds().stream().map(World::getName));
                        default: // Nothing left to suggest
                            return Collections.emptyList();
                    }
                }

                // These flags only accept one argument
                switch (flag) {
                    // Give suggestions following the enum filter format (entities)
                    case DENY_SPAWN:
                    case DENY_AGGRO:
                    case DENY_ENTITY_USE:
                        return filterFormat(args[3], Stream.of(EntityType.values()), Utils::formattedName);

                    // Suggest placeable and breakable materials
                    case DENY_BREAK:
                    case DENY_PLACE:
                        return filterFormat(args[3], Stream.of(Material.values()).filter(mat ->
                                        Materials.isPlaceable(mat) || mat == Material.LEAD || mat.isBlock()),
                                Utils::formattedName);

                    // Suggest interactable blocks
                    case DENY_BLOCK_USE:
                        return filterFormat(args[3], Stream.of(Material.values()).filter(mat ->
                                mat.isInteractable() && mat.isBlock()), Utils::formattedName);

                    // Suggest items
                    case DENY_ITEM_USE:
                    case DENY_WEAPON_USE:
                        return filterFormat(args[3], Stream.of(Material.values()).filter(mat -> !mat.isBlock()),
                                Utils::formattedName);

                    // Give suggestions according to the CommandMeta format
                    case ENTER_COMMAND:
                    case EXIT_COMMAND: {
                        // Suggest the allowed senders
                        if (!args[3].contains(":"))
                            return filterStartingWith(args[3], Stream.of("console:", "player:"));
                        else { // Suggest the known commands matching the prefix
                            String prefix = args[3].substring(0, args[3].indexOf(':') + 1);
                            Map<String, org.bukkit.command.Command> knownCommands =
                                    (Map<String, org.bukkit.command.Command>) ReflectionHelper.getFieldValue
                                            ("knownCommands", SimpleCommandMap.class, ((CraftServer) Bukkit.getServer())
                                                    .getCommandMap());

                            return filterStartingWith(args[3], knownCommands.keySet().stream()
                                    .filter(cmd -> !cmd.contains(":"))
                                    .map(cmd -> prefix + cmd.toLowerCase()));
                        }
                    }

                    // Suggest the known commands
                    case DENY_COMMAND: {
                        Map<String, org.bukkit.command.Command> knownCommands =
                                (Map<String, org.bukkit.command.Command>) ReflectionHelper.getFieldValue
                                        ("knownCommands", SimpleCommandMap.class, ((CraftServer) Bukkit.getServer())
                                                .getCommandMap());

                        return filterFormat(args[3], knownCommands.keySet().stream().filter(s -> !s.contains(":")),
                                null);
                    }

                    // Boolean flags
                    default:
                        return filterStartingWith(args[3], ALLOW_DENY);
                }
            }
        }
        // For expansion and retraction just suggest the valid directions
        else if (("expand".equalsIgnoreCase(args[0]) || "retract".equalsIgnoreCase(args[0])) && args.length == 3) {
            return filterStartingWith(args[2], EXPANSION_DIRECTIONS);
        }
        // Suggestion for the includeChildren option
        else if ("delete".equalsIgnoreCase(args[0]) && args.length == 3)
            return filterStartingWith(args[2], Stream.of("true", "false"));

        // By default return no suggestions
        return Collections.emptyList();
    }

    // Convert the flags and their meta in the given container into a readable format
    private static String formatFlags(FlagContainer flags) {
        return flags.getFlags().entrySet().stream().map(entry -> "- " + Utils.formattedName(entry.getKey()) +
                ": {&(gray)" + RegionFlag.toString(entry.getKey(), entry.getValue()) + "}\n").reduce("",
                String::concat).trim();
    }

    // Convert a stream of some object into the EnumFilter format using the given toString method and prefix
    private static <T> List<String> filterFormat(String prefix, Stream<T> stream, Function<T, String> toString) {
        return filterStartingWith(prefix, stream.map(x -> prefix.substring(0, prefix.lastIndexOf(',') + 1) +
                (prefix.contains("*") ? "!" : "") + (toString == null ? (String) x : toString.apply(x))));
    }

    private enum FlagModification {
        SET_OR_NONE(""), DELETE("!"), AUGMENT("+"), REDUCE("-");

        final String marker;

        static final FlagModification[] VALUES = values();

        FlagModification(String marker) {
            this.marker = marker;
        }

        static FlagModification forFlagString(String flag) {
            if (flag.isEmpty())
                return SET_OR_NONE;

            String marker = flag.substring(0, 1);
            for (FlagModification modification : VALUES) {
                if (modification.marker.equals(marker))
                    return modification;
            }

            return SET_OR_NONE;
        }
    }
}
