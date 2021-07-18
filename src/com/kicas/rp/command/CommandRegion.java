package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.data.flagdata.*;
import com.kicas.rp.util.Materials;
import com.kicas.rp.util.ReflectionHelper;
import com.kicas.rp.util.TextUtils;
import com.kicas.rp.util.Utils;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Allows administrators to register and delete regions, as well as modify and view their flags. The size of a region
 * can also be adjusted through this command as well.
 */
public class CommandRegion extends TabCompleterBase implements CommandExecutor {
    // For tab completion
    private static final List<String> ALLOW_DENY = Arrays.asList("allow", "deny");
    private static final List<String> EXPANSION_DIRECTIONS = Arrays.asList("vert", "top", "bottom", "north", "south",
            "east", "west");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Args check
        if (args.length < 1)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            TextUtils.sendFormatted(sender, "&(red)You must be in-game to use this command.");
            return true;
        }

        // Check the sub-command
        SubCommand subCommand = Utils.valueOfFormattedName(args[0], SubCommand.class);
        if (subCommand == null) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region <%0>", Stream.of(SubCommand.VALUES)
                    .map(Utils::formattedName).collect(Collectors.joining("|")));
            return true;
        }

        // Handle the first set of sub commands
        switch (subCommand) {
            case FLAG:
                commandFlag(sender, args);
                return true;

            case INFO:
                commandInfo(sender, args);
                return true;

            case CREATE:
                commandCreate(sender, args);
                return true;
        }

        if (args.length == 1) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region %0 <region-name>...", Utils.formattedName(subCommand));
            return true;
        }

        // Check to make sure the region name is valid
        Region region = RegionProtection.getDataManager().getRegionByName(((Player) sender).getWorld(), args[1]);
        if (region == null) {
            if (DataManager.GLOBAL_FLAG_NAME.equals(args[1]))
                TextUtils.sendFormatted(sender, "&(red)This operation cannot be performed on the global flag set.");
            else
                TextUtils.sendFormatted(sender, "&(red)Could not find a region with name {&(gold)%0} in your world.", args[1]);
            return true;
        }

        switch (subCommand) {
            case EXPAND:
            case RETRACT:
                commandExpandRetract(sender, args, region, subCommand);
                return true;

            case DELETE: {
                boolean includeChildren = args.length == 3 && "include-children".equalsIgnoreCase(args[2]);
                // This can fail if includeChildren is false and the region has children
                if (RegionProtection.getDataManager().tryDeleteRegion((Player) sender, region, includeChildren)) {
                    TextUtils.sendFormatted(sender, "&(green)Deleted region {&(aqua)%0}%1", args[1], includeChildren
                            ? " and all child regions." : ".");
                }

                return true;
            }

            case RENAME:
                if (args.length == 2) {
                    TextUtils.sendFormatted(sender, "&(red)Usage: /region rename %0 <new-name>", args[1]);
                    return true;
                }

                if (RegionProtection.getDataManager().tryRenameRegion((Player) sender, region, args[2]))
                    TextUtils.sendFormatted(sender, "&(green)Renamed region {&(aqua)%0} to {&(aqua)%1}", args[1], args[2]);

                return true;

            case REDEFINE: {
                PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);

                if (ps.getCurrentSelectedRegion() == null) {
                    TextUtils.sendFormatted(sender, "&(red)Please outline the new region bounds before using this command");
                    return true;
                }

                if (RegionProtection.getDataManager().tryRedefineBounds((Player) sender, region,
                        ps.getCurrentSelectedRegion().getBounds())) {
                    TextUtils.sendFormatted(sender, "&(green)Successfully redefined the bound of region {&(aqua)%0}", args[1]);
                    return true;
                }
            }

            case SET_PRIORITY: {
                if (args.length == 2) {
                    TextUtils.sendFormatted(sender, "&(red)Usage: /region set-priority %0 <new-priority>", args[1]);
                    return true;
                }

                // Parse the priority
                int newPriority;
                try {
                    newPriority = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    TextUtils.sendFormatted(sender, "&(red)Invalid priority: %0", args[2]);
                    return true;
                }

                // Make sure the priority is within bounds
                if (newPriority < 0 || newPriority > 127) {
                    TextUtils.sendFormatted(sender, "&(red)Region priorities must be between 0 and 127 inclusive.");
                    return true;
                }

                // Perform the change
                region.setPriority(newPriority);
                TextUtils.sendFormatted(sender, "&(green)Set region priority to %0.", newPriority);

                return true;
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private void commandFlag(CommandSender sender, String[] args) {
        // Check for the region name
        if (args.length == 1) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region flag <region-name>...");
            return;
        }

        // Get the flag container being modified
        FlagContainer flags = DataManager.GLOBAL_FLAG_NAME.equals(args[1])
                ? RegionProtection.getDataManager().getWorldFlags(((Player) sender).getWorld())
                : RegionProtection.getDataManager().getRegionByName(((Player) sender).getWorld(), args[1]);

        // Invalid region name
        if (flags == null) {
            TextUtils.sendFormatted(sender, "&(red)Could not find a region with name {&(gold)%0} in your world.", args[1]);
            return;
        }

        // Check for flag operation
        if (args.length == 2) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region flag %0 <%1>...", args[1], Stream.of(FlagOperation.VALUES)
                    .map(Utils::formattedName).collect(Collectors.joining("|")));
            return;
        }

        FlagOperation operation = Utils.valueOfFormattedName(args[2], FlagOperation.class);

        // Check for the flag name
        if (args.length == 3) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region flag %0 %1 <flag-name>...", args[1],
                    Utils.formattedName(operation));
            return;
        }

        // Get and check the flag in question
        RegionFlag flag = Utils.valueOfFormattedName(args[3], RegionFlag.class);
        if (flag == null) {
            TextUtils.sendFormatted(sender, "&(red)Invalid flag name: %0", args[3]);
            return;
        }

        if ((operation == FlagOperation.APPEND || operation == FlagOperation.NEGATE) &&
                (flag.isBoolean() || !Augmentable.class.isAssignableFrom(flag.getMetaClass()))) {
            TextUtils.sendFormatted(sender, "&(red)This flag cannot be appended to or reduced.");
            return;
        }

        // Show the current value
        if (operation == FlagOperation.GET) {
            String metaString = RegionFlag.toString(flag, flags.getFlagMeta(flag));
            TextUtils.sendFormatted(sender, "&(gold)Flag {&(aqua)%0} is currently set to: {&(gray)%1}",
                    Utils.formattedName(flag), metaString.contains("\n") ? "\n" + metaString : metaString);
            return;
        }

        // We're deleting the flag
        if (operation == FlagOperation.DELETE) {
            flags.deleteFlag(flag);
            TextUtils.sendFormatted(sender, "&(green)Deleted flag {&(aqua)%0} from region {&(aqua)%1}",
                    Utils.formattedName(flag), args[1]);
            return;
        }

        // Check for a value
        if (args.length == 4) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region flag %0 %1 %2 <value>...", args[1],
                    Utils.formattedName(operation), Utils.formattedName(flag));
            return;
        }

        // More arguments means there is a value that should be evaluated as done below
        Object meta;
        try {
            // Parse/evaluate the meta string
            meta = RegionFlag.metaFromString(flag, joinArgsBeyond(3, " ", args));
        } catch (IllegalArgumentException | TextUtils.SyntaxException ex) { // Thrown by the meta parsers
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }

        // Regular set
        if (operation == FlagOperation.SET) {
            flags.setFlag(flag, meta);
        }
        // Augmentation or reduction
        else {
            // This requires metadata to already exist
            Object currentMeta = flags.getFlagMeta(flag);
            if (currentMeta == null) {
                TextUtils.sendFormatted(sender, "&(red)This flag cannot be appended to or reduced since no current value exists.");
                return;
            }

            // This should never happen
            if (!Augmentable.class.isAssignableFrom(currentMeta.getClass()) ||
                    !meta.getClass().equals(currentMeta.getClass())) {
                throw new InternalError();
            }

            // Perform the operation
            if (operation == FlagOperation.APPEND)
                ((Augmentable) currentMeta).augment(meta);
            else
                ((Augmentable) currentMeta).reduce(meta);
        }

        TextUtils.sendFormatted(sender, "&(green)Updated flag {&(aqua)%0} for region {&(aqua)%1}", Utils.formattedName(flag),
                args[1]);
    }

    private void commandInfo(CommandSender sender, String[] args) {
        // Show information about the flags set in the player's world
        if (args.length > 1 && DataManager.GLOBAL_FLAG_NAME.equals(args[1])) {
            // Get the flags and detail them
            FlagContainer worldFlags = RegionProtection.getDataManager().getWorldFlags(((Player) sender).getWorld());

            // No flags -> skip formatting
            if (worldFlags.isEmpty()) {
                TextUtils.sendFormatted(sender, "&(gold)There are no global flags set in this world.");
                return;
            }

            TextUtils.sendFormatted(sender, "&(gold)Showing global flag info:\nFlags:\n%0", formatFlags(worldFlags));

            return;
        }

        // Get the list of regions to detail
        final List<Region> regions;
        // Get the regions the player is standing in
        if (args.length == 1) {
            regions = RegionProtection.getDataManager().getRegionsAt(((Player) sender).getLocation());
        }
        // Get the region specified by name
        else {
            Region region = RegionProtection.getDataManager().getRegionByName(((Player) sender).getWorld(), args[1]);

            // Invalid region name
            if (region == null) {
                TextUtils.sendFormatted(sender, "&(red)Could not find a region with name {&(gold)%0} in your world.", args[1]);
                return;
            }

            regions = Collections.singletonList(region);
        }

        // No regions were found
        if (regions.isEmpty()) {
            TextUtils.sendFormatted(sender, "&(gold)There are no regions present at your location.");
            return;
        }

        // Detail the regions
        regions.forEach(region -> {
            TextUtils.sendFormatted(
                    sender,
                    "&(gold)Showing info for region {&(green)%0}:" +
                            "\nOwner: {&(green)%1}%c" +
                            "\nBounds: $(hovercmd,/toregion %0,{&(aqua)Click here to go to this region},{&(aqua)%2 %3 %4 | %5 %6 %7})" +
                            "\nPriority: {&(aqua)%8}" +
                            "\nParent: {&(aqua)%9}%a%b",
                    region.getDisplayName(),
                    region.getOwnerName(),
                    region.getMin().getBlockX(),
                    region.getMin().getBlockY(),
                    region.getMin().getBlockZ(),
                    region.getMax().getBlockX(),
                    region.getMax().getBlockY(),
                    region.getMax().getBlockZ(),
                    region.getPriority(),
                    region.hasParent()
                            ? region.getParent().getDisplayName()
                            : "none",
                    region.getCoOwners().isEmpty()
                            ? ""
                            : "\nCo-Owners: {&(gray)" + region.getCoOwners().stream()
                            .map(RegionProtection.getDataManager()::currentUsernameForUuid)
                            .sorted(Comparator.comparing(String::toLowerCase)).collect(Collectors.joining(", ")) + "}",
                    region.isEmpty() || (region.hasParent() && region.getPriority() == region.getParent().getPriority() &&
                            regions.contains(region.getParent()))
                            ? ""
                            : "\nFlags:\n" + formatFlags(region),
                    region.isAdminOwned() ? "" : "\nRecently Stolen: " + region.isRecentlyStolen()
            );
        });
    }

    private void commandCreate(CommandSender sender, String[] args) {
        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);

        if (ps.getCurrentSelectedRegion() == null) {
            TextUtils.sendFormatted(sender, "&(red)Please outline the region you wish to create before using this command.");
            return;
        }

        if (args.length == 1) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region create <region-name>...");
            return;
        }

        // Fields to potentially overwrite
        int priority = 0;
        String parentName = null;
        boolean force = false;

        // Skip the sub-command and region name and evaluate the rest of the arguments
        for (int i = 2; i < args.length; ++i) {
            int colonIndex = args[i].indexOf(':');
            CreationParameter parameter = Utils.valueOfFormattedName(
                    args[i].substring(0, Utils.indexOfDefault(colonIndex, args[i].length())),
                    CreationParameter.class
            );

            if (parameter == null) {
                TextUtils.sendFormatted(sender, "&(red)Ignoring argument \"%0\" since it is invalid.", args[i]);
                continue;
            }

            String parameterValue = colonIndex < 0 ? null : args[i].substring(colonIndex + 1);

            switch (parameter) {
                case PRIORITY: {
                    String priorityString = args[i].substring(args[i].indexOf(':') + 1);
                    try {
                        priority = Integer.parseInt(priorityString);
                    } catch (NumberFormatException ex) {
                        TextUtils.sendFormatted(sender, "&(red)Invalid priority: %0", priorityString);
                        return;
                    }

                    if (priority < 0 || priority > 127) {
                        TextUtils.sendFormatted(sender, "&(red)Region priorities must be between 0 and 127 inclusive.");
                        return;
                    }

                    break;
                }

                case PARENT:
                    parentName = parameterValue;
                    break;

                case FORCE:
                    force = true;
                    break;
            }
        }

        // Attempt to register the region (parent name checked here)
        if (RegionProtection.getDataManager().tryRegisterRegion((Player) sender, ps.getCurrentSelectedRegion(),
                args[1], priority, parentName, force)) {
            TextUtils.sendFormatted(sender, "&(green)Created region {&(aqua)%0} with a priority of {&(aqua)%1} and %2.",
                    args[1], ps.getCurrentSelectedRegion().getPriority(),
                    (parentName == null ? "no parent" : "parent " + parentName));
        }
    }

    private void commandExpandRetract(CommandSender sender, String[] args, Region region, SubCommand operation) {
        if (args.length == 2) {
            TextUtils.sendFormatted(sender, "&(red)Usage: /region %0 %1 <vert|top|bottom|north|south|east|west>[...]",
                    Utils.formattedName(operation), args[1]);
            return;
        }

        // Vertical extension is a different case, so check for that first
        if ("vert".equalsIgnoreCase(args[2]) || "vertical".equalsIgnoreCase(args[2])) {
            // Retracting vertically doesn't make sense, don't allow it
            if (operation == SubCommand.RETRACT) {
                TextUtils.sendFormatted(sender, "&(red)You cannot vertically retract a region without specifying an " +
                        "amount. Use /region retract %0 up|down instead.", args[1]);
                return;
            }

            // Perform the extension
            region.getMin().setY(0);
            region.getMax().setY(region.getWorld().getMaxHeight());

            TextUtils.sendFormatted(sender, "&(green)Extended region to bedrock and world height.");
        }
        // Regular expansion and retraction
        else {
            // Parse the side
            args[2] = args[2].toLowerCase();
            BlockFace side;
            if ("top".equals(args[2]))
                side = BlockFace.UP;
            else if ("bottom".equals(args[2]))
                side = BlockFace.DOWN;
            else
                side = Utils.valueOfFormattedName(args[2], BlockFace.class);

            // Check the side
            if (side == null) {
                TextUtils.sendFormatted(sender, "&(red)Invalid direction: %0", args[2]);
                return;
            }

            // Check for the amount
            if (args.length == 3) {
                TextUtils.sendFormatted(sender, "&(red)Usage: /region %0 %1 %2 <amount>", Utils.formattedName(operation),
                        args[1], args[2]);
                return;
            }

            // Get and check the amount
            int amount;
            try {
                amount = Integer.parseInt(args[3]) * (operation == SubCommand.RETRACT ? -1 : 1);
            } catch (NumberFormatException ex) {
                TextUtils.sendFormatted(sender, "&(red)Invalid amount: %0", args[3]);
                return;
            }

            // Expand the region
            if (RegionProtection.getDataManager().tryExpandRegion((Player) sender, region, side, amount))
                TextUtils.sendFormatted(sender, "&(green)Successfully adjusted region {&(aqua)%0}", args[1]);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        // Get a location
        Location location;
        if (sender instanceof Player)
            location = ((Player) sender).getLocation();
        else if (sender instanceof BlockCommandSender)
            location = ((BlockCommandSender) sender).getBlock().getLocation();
        else {
            // Add default to prevent errors
            location = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
        }

        // Show sub-commands
        if (args.length == 1) {
            return filterStartingWith(args[0], Stream.of(SubCommand.VALUES).map(Utils::formattedName));
        }

        SubCommand subCommand = Utils.valueOfFormattedName(args[0], SubCommand.class);
        if (subCommand == null)
            return Collections.emptyList();

        // Suggest region names (if applicable)
        if (args.length == 2) {
            switch (subCommand) {
                case CREATE:
                    return args[1].isEmpty() ? Collections.singletonList("<region-name>") : Collections.emptyList();

                case FLAG:
                case INFO:
                    // Global flags allowed
                    return filterStartingWith(args[1], RegionProtection.getDataManager().getAdminRegionNames(((Player) sender)
                            .getWorld(), true));

                default:
                    // Global flags aren't allowed
                    return filterStartingWith(args[1], RegionProtection.getDataManager().getAdminRegionNames(((Player) sender)
                            .getWorld(), false));
            }
        }

        switch (subCommand) {
            case FLAG:
                // Flag operations
                if (args.length == 3)
                    return filterStartingWith(args[2], Stream.of(FlagOperation.VALUES).map(Utils::formattedName));
                // Flag names
                else if (args.length == 4) {
                    Region region = RegionProtection.getDataManager().getRegionByName(location.getWorld(), args[1]);

                    // For certain flag operations, limit the suggestions to the flags explicitly set in the region
                    Stream<RegionFlag> suggestedFlags = Arrays.stream(RegionFlag.VALUES);
                    if (region != null) {
                        FlagOperation flagOperation = Utils.valueOfFormattedName(args[2], FlagOperation.class);
                        if (flagOperation == FlagOperation.APPEND || flagOperation == FlagOperation.NEGATE ||
                                flagOperation == FlagOperation.DELETE) {
                            suggestedFlags = suggestedFlags.filter(region::hasFlag);
                        }
                    }

                    return filterStartingWith(args[3], suggestedFlags.map(Utils::formattedName));
                }
                // Flag values
                else {
                    FlagOperation flagOperation = Utils.valueOfFormattedName(args[2], FlagOperation.class);
                    if (flagOperation != FlagOperation.GET && flagOperation != FlagOperation.DELETE)
                        return suggestFlagValue(sender, args, Utils.valueOfFormattedName(args[3], RegionFlag.class), location);
                }

            case CREATE: {
                String arg = args[args.length - 1];

                int colonIndex = arg.indexOf(':');

                // Suggests the various parameters that have not already been set
                if (colonIndex < 0) {
                    return filterStartingWith(arg, Stream.of(CreationParameter.VALUES)
                            .map(parameter -> Utils.formattedName(parameter) + (parameter.acceptsArgs ? ":" : ""))
                            .filter(tag -> {
                        // Remove the tags that have already been used
                        for (int i = 2; i < args.length; ++i) {
                            if (args[i].toLowerCase().startsWith(tag))
                                return false;
                        }
                        return true;
                    }));
                }
                // Suggest values for each parameter
                else {
                    CreationParameter parameter = Utils.valueOfFormattedName(
                            arg.substring(0, Utils.indexOfDefault(colonIndex, arg.length() - 1)),
                            CreationParameter.class
                    );

                    if (parameter == CreationParameter.PARENT) {
                        return filterStartingWith(arg, RegionProtection.getDataManager().getRegionsInWorld(location.getWorld())
                                .stream().map(region -> "parent:" + region.getRawName()));
                    } else
                        return Collections.emptyList();
                }
            }

            case EXPAND:
                return args.length == 3 ? filterStartingWith(args[2], EXPANSION_DIRECTIONS) : Collections.emptyList();

            case RETRACT:
                return args.length == 3 ? filterStartingWith(args[2], EXPANSION_DIRECTIONS.stream()
                        .filter(direction -> !"vert".equals(direction))) : Collections.emptyList();

            case DELETE:
                return args.length == 3 ? filterStartingWith(args[2], Stream.of("include-children")) : Collections.emptyList();

            default:
                return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> suggestFlagValue(CommandSender sender, String[] args, RegionFlag flag, Location location) {
        // Invalid flag -> no suggestions
        if (flag == null)
            return Collections.emptyList();

        // Location meta suggestion
        if (LocationMeta.class.equals(flag.getMetaClass())) {
            switch (args.length) {
                case 5: // x
                    return Collections.singletonList(Integer.toString(location.getBlockX()));
                case 6: // y
                    return Collections.singletonList(Integer.toString(location.getBlockY()));
                case 7: // z
                    return Collections.singletonList(Integer.toString(location.getBlockZ()));
                case 8:
                    // By default suggest the yaw value
                    if (sender instanceof Player && (args[7].isEmpty() || args[7].matches("(-)?[\\d.]+"))) {
                        return Collections.singletonList(Integer.toString((int) ((Player) sender).getLocation()
                                .getYaw()));
                    } else // If the player starts typing a non-number, suggest world values instead
                        return filterStartingWith(args[7], Bukkit.getWorlds().stream().map(World::getName));
                case 9: // Pitch if the sender is a player, otherwise suggest world values again
                    // Pitch
                    if (sender instanceof Player) {
                        return Collections.singletonList(Integer.toString((int) ((Player) sender).getLocation()
                                .getPitch()));
                    } else { // World values
                        return filterStartingWith(args[8], Bukkit.getWorlds().stream().map(World::getName));
                    }
                case 10: // World values
                    return filterStartingWith(args[9], Bukkit.getWorlds().stream().map(World::getName));
                default: // Nothing left to suggest
                    return Collections.emptyList();
            }
        }

        if (args.length > 5)
            return Collections.emptyList();

        if (flag.isBoolean())
            return filterStartingWith(args[4], ALLOW_DENY);

        // Too complex, not worth giving suggestions for
        if (TrustMeta.class.equals(flag.getMetaClass()) || TextMeta.class.equals(flag.getMetaClass()))
            return Collections.emptyList();

        if (CommandMeta.class.equals(flag.getMetaClass())) {
            // Suggest the allowed senders
            if (!args[4].contains(":"))
                return filterStartingWith(args[4], Stream.of("console:", "player:"));
            else { // Suggest the known commands matching the prefix
                String prefix = args[4].substring(0, args[4].indexOf(':') + 1);
                Map<String, org.bukkit.command.Command> knownCommands =
                        (Map<String, org.bukkit.command.Command>) ReflectionHelper.getFieldValue
                                ("knownCommands", SimpleCommandMap.class, ((CraftServer) Bukkit.getServer())
                                        .getCommandMap());

                return filterStartingWith(args[4], knownCommands.keySet().stream()
                        .filter(cmd -> !cmd.contains(":"))
                        .map(cmd -> prefix + cmd.toLowerCase()));
            }
        }

        // These are the filter flags
        switch (flag) {
            // Give suggestions following the enum filter format (entities)
            case DENY_SPAWN:
            case DENY_AGGRO:
            case DENY_ENTITY_USE:
            case DENY_ENTITY_TELEPORT:
                return filterFormat(args[4], Stream.of(EntityType.values()), Utils::formattedName);

            // Suggest placeable and breakable materials
            case DENY_BREAK:
            case DENY_PLACE:
                return filterFormat(args[4], Stream.of(Material.values()).filter(mat ->
                                Materials.isPlaceable(mat) || mat == Material.LEAD || mat.isBlock()),
                        Utils::formattedName);

            // Suggest interactable blocks
            case DENY_BLOCK_USE:
                return filterFormat(args[4], Stream.of(Material.values()).filter(mat ->
                        (mat.isInteractable() || Materials.isPressureSensitive(mat)) && mat.isBlock()),
                        Utils::formattedName);

            // Suggest items
            case DENY_ITEM_USE:
            case DENY_WEAPON_USE:
                return filterFormat(args[4], Stream.of(Material.values()).filter(mat -> !mat.isBlock()),
                        Utils::formattedName);

            case DENY_ITEM_CONSUMPTION:
                return filterFormat(args[4], Stream.of(Material.values()).filter(Materials::isConsumable),
                        Utils::formattedName);

            // Suggest the known commands
            case DENY_COMMAND: {
                Map<String, org.bukkit.command.Command> knownCommands =
                        (Map<String, org.bukkit.command.Command>) ReflectionHelper.getFieldValue(
                                "knownCommands",
                                SimpleCommandMap.class,
                                ((CraftServer) Bukkit.getServer()).getCommandMap()
                        );

                return filterFormat(args[4], knownCommands.keySet().stream().filter(s -> !s.contains(":")), null);
            }

            // Suggest blocks that are able to grow
            case DENY_GROWTH:
                return filterFormat(args[4], Stream.of(Material.values()).filter(Materials::isGrowable),
                        Utils::formattedName);

            case ENTRANCE_RESTRICTION:
                return filterFormat(args[4], Arrays.stream(BorderPolicy.Policy.VALUES), Utils::formattedName);

            // Suggest gamemodes
            case ENTRY_GAMEMODE:
            case EXIT_GAMEMODE:
                return filterFormat(args[4], Arrays.stream(GameModeMeta.Mode.VALUES), Utils::formattedName);
        }

        return Collections.emptyList();
    }

    // Convert the flags and their meta in the given container into an alphabetical, readable format
    private static String formatFlags(FlagContainer flags) {
        return flags.getFlags().entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> "- " + Utils.formattedName(entry.getKey()) + ": {&(gray)" +
                        RegionFlag.toString(entry.getKey(), entry.getValue()) + "}\n").reduce("", String::concat).trim();
    }

    // Convert a stream of some object into the EnumFilter format using the given toString method and prefix
    private static <T> List<String> filterFormat(String prefix, Stream<T> stream, Function<T, String> toString) {
        return filterStartingWith(prefix, stream.map(x -> prefix.substring(0, prefix.lastIndexOf(',') + 1) +
                (prefix.contains(AbstractFilter.ALL_ELEMENTS) ? AbstractFilter.ELEMENT_NEGATION : "") +
                (toString == null ? (String) x : toString.apply(x))));
    }

    private enum SubCommand {
        FLAG, INFO, CREATE, EXPAND, RETRACT, DELETE, RENAME, REDEFINE, SET_PRIORITY;

        static final SubCommand[] VALUES = values();
    }

    private enum FlagOperation {
        GET, SET, DELETE, APPEND, NEGATE;

        static final FlagOperation[] VALUES = values();
    }

    private enum CreationParameter {
        PRIORITY, PARENT, FORCE(false);

        static final CreationParameter[] VALUES = values();

        final boolean acceptsArgs;

        CreationParameter(boolean acceptsArgs) {
            this.acceptsArgs = acceptsArgs;
        }

        CreationParameter() {
            this(true);
        }
    }
}
