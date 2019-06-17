package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Allows administrators to register and delete regions, as well as modify and view their flags. The size of a region
 * can also be adjusted through this command as well.
 */
public class CommandRegion extends Command {
    private static final List<String> SUB_COMMANDS = Arrays.asList("flag", "create", "expand", "retract", "delete");
    private static final List<String> ALLOW_DENY = Arrays.asList("allow", "deny");
    private static final List<String> EXPANSION_DIRECTIONS = Arrays.asList("vert", "up", "down", "north", "south",
            "east", "west");

    CommandRegion() {
        super("region", "Modify a region.", "/region <flag|create|expand|retract|delete> <name> [args...]", "rg");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Check the sub-command
        args[0] = args[0].toLowerCase();
        if(!SUB_COMMANDS.contains(args[0])) {
            sender.sendMessage(ChatColor.RED + "Invalid sub-command: " + args[0]);
            return false;
        }

        // Args are tagged, IE order does not matter and each value is prefixed with the pertinent field. The only
        // fields for the create sub-command are priority and parent.
        if("create".equals(args[0])) {
            PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player)sender);
            int priority = 0;
            String parentName = null;

            // Skip the sub-command and region name and evaluate the rest of the arguments
            for(int i = 2;i < args.length;++ i) {
                if(args[i].toLowerCase().startsWith("priority:")) {
                    String priorityString = args[i].substring(args[i].indexOf(':') + 1);
                    try {
                        priority = Integer.parseInt(priorityString);
                    }catch(NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Invalid priority: " + priorityString);
                        return true;
                    }
                }else if(args[i].toLowerCase().startsWith("parent:"))
                    parentName = args[i].substring(args[i].indexOf(':') + 1);
                else
                    sender.sendMessage(ChatColor.RED + "Ignoring argument \"" + args[i] + "\" since it is invalid.");
            }

            // Attempt to register the region
            if(RegionProtection.getDataManager().tryRegisterRegion((Player)sender, ps.getCurrentSelectedRegion(),
                    args[1], priority, parentName)) {
                sender.sendMessage(ChatColor.GREEN + "Created region " + args[1] + " with a priority of " +
                        ps.getCurrentSelectedRegion().getPriority() + " and " + (parentName == null ? "no parent."
                        : "parent " + parentName));
            }

            return true;
        }

        // Check to make sure the region name is valid
        Region region = RegionProtection.getDataManager().getRegionByName(((Player)sender).getWorld(), args[1]);
        if(region == null) {
            sender.sendMessage(ChatColor.RED + "Could not find a region with name \"" + args[1] + "\" in your world.");
            return true;
        }

        // Flag sub-command
        if("flag".equals(args[0])) {
            // Get and check the flag in question
            RegionFlag flag = Utils.valueOfFormattedName(args[2], RegionFlag.class);
            if(flag == null) {
                sender.sendMessage(ChatColor.RED + "Invalid flag: " + args[2]);
                return true;
            }

            // If no value is specified, notify the player of the current value
            if(args.length == 3) {
                Object meta = region.getFlagMeta(flag);
                String metaString;

                if(flag.isBoolean())
                    metaString = (boolean)meta ? "allow" : "deny";
                else if(flag == RegionFlag.DENY_SPAWN)
                    metaString = ((EnumFilter)meta).toString(EntityType.class);
                else if(flag == RegionFlag.DENY_PLACE || flag == RegionFlag.DENY_BREAK)
                    metaString = ((EnumFilter)meta).toString(Material.class);
                else
                    metaString = meta.toString();

                sender.sendMessage(ChatColor.GOLD + Utils.formattedName(flag) + " in region " + args[1] +
                        " is set to: " + ChatColor.GRAY + (metaString.contains("\n") ? "\n" + metaString : metaString));

                return true;
            }

            // More arguments means there is a value that should be evaluated as done below
            Object meta;
            String metaString = joinArgsBeyond(2, " ", args);
            // Parse/evaluate the meta string
            try {
                switch (flag) {
                    case TRUST:
                        meta = TrustMeta.fromString(metaString);
                        break;

                    case DENY_SPAWN:
                        meta = EnumFilter.fromString(metaString, EntityType.class);
                        break;

                    case DENY_BREAK:
                    case DENY_PLACE:
                        meta = EnumFilter.fromString(metaString, Material.class);
                        break;

                    case GREETING:
                        // The TextUtils.SyntaxException is caught by the execution method wrapping this method
                        meta = new TextMeta(metaString);
                        break;

                    default:
                        metaString = metaString.trim();
                        if ("allow".equalsIgnoreCase(metaString) || "yes".equalsIgnoreCase(metaString) ||
                                "true".equalsIgnoreCase(metaString)) {
                            meta = true;
                        } else if ("deny".equalsIgnoreCase(metaString) || "no".equalsIgnoreCase(metaString) ||
                                "false".equalsIgnoreCase(metaString)) {
                            meta = false;
                        } else {
                            sender.sendMessage(ChatColor.RED + "Invalid flag value: " + metaString);
                            return true;
                        }
                }
            }catch(IllegalArgumentException ex) { // Thrown by the meta parsers
                sender.sendMessage(ChatColor.RED + ex.getMessage());
                return true;
            }

            // Set the flag and notify the player
            region.setFlag(flag, meta);
            sender.sendMessage(ChatColor.GREEN + "Updated flag value for region " + args[1]);
        }else if("expand".equals(args[0]) || "retract".equals(args[0])) { // Region size modification
            // Verticle extension is a different case, so check for that first
            if("vert".equalsIgnoreCase(args[2]) || "vertical".equalsIgnoreCase(args[2])) {
                // Retracting vertically doesn't make sense, don't allow it
                if("retract".equals(args[0])) {
                    sender.sendMessage(ChatColor.RED + "You cannot vertically retract a region without specifying an " +
                            "amount. Use /region retract up/down instead.");
                    return true;
                }

                // Perform the extension
                region.getMin().setY(0);
                region.getMax().setY(region.getWorld().getMaxHeight());
                sender.sendMessage(ChatColor.GREEN + "Extended region to bedrock and world height.");
            }else{ // Regular expansion and retraction
                // Get and check the direction
                BlockFace face = Utils.valueOfFormattedName(args[2], BlockFace.class);
                if(face == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid direction: " + args[2]);
                    return true;
                }

                // Get and check the amount
                int amount;
                try {
                    amount = Integer.parseInt(args[3]) * ("retract".equals(args[0]) ? -1 : 1);
                }catch(NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
                    return true;
                }

                // Perform the size modification
                switch(face) {
                    case UP:
                        region.getMax().add(0, amount, 0);
                        break;

                    case DOWN:
                        region.getMin().subtract(0, amount, 0);
                        break;

                    case NORTH:
                        region.getMin().subtract(0, 0, amount);
                        break;

                    case SOUTH:
                        region.getMax().add(0, 0, amount);
                        break;

                    case EAST:
                        region.getMax().add(amount, 0, 0);
                        break;

                    case WEST:
                        region.getMin().subtract(amount, 0, 0);
                        break;

                    default:
                        sender.sendMessage(ChatColor.RED + "Invalid direction: " + args[3] + ". Please only use the " +
                                "four cardinal directions as well as up and down.");
                        return true;
                }

                sender.sendMessage(ChatColor.GREEN + "Successfully adjusted region " + args[1]);
            }
        }else if("delete".equals(args[0])) { // Delete a region
            boolean includeChildren = args.length == 3 && "true".equalsIgnoreCase(args[2]);
            // This can fail if includeChildren is false and the region has children
            if(RegionProtection.getDataManager().tryDeleteRegion((Player)sender, region, includeChildren)) {
                sender.sendMessage(ChatColor.GREEN + "Deleted region " + args[1] + (includeChildren
                        ? " and all child regions." : "."));
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location)
            throws IllegalArgumentException {
        // The first two argument suggestions are independent of the sub-command
        if(args.length == 1)
            return filterStartingWith(args[0], SUB_COMMANDS);
        else if(args.length == 2) {
            // Don't suggest names if we're creating a region
            return "create".equalsIgnoreCase(args[0]) ? Collections.emptyList()
                    : filterStartingWith(args[1], RegionProtection.getDataManager()
                        .getRegionsInWorld(location.getWorld()).stream().map(Region::getName));
        }

        // Creation suggestions: suggest the unused tags, and if it's the parent tag then suggest the list of possible
        // parents.
        if("create".equalsIgnoreCase(args[0])) {
            if(args[args.length - 1].toLowerCase().startsWith("parent:")) { // Suggest list of possible parents
                return filterStartingWith(args[args.length - 1], RegionProtection.getDataManager()
                        .getRegionsInWorld(location.getWorld()).stream().map(region -> "parent:" + region.getName()));
            }else if(args[args.length - 1].indexOf(':') < 0) { // Suggest the tag prefixes that are left
                return Stream.of("priority:", "parent:").filter(tag -> {
                    for(int i = 2;i < args.length;++ i) {
                        if(args[i].toLowerCase().startsWith(tag))
                            return false;
                    }
                    return true;
                }).collect(Collectors.toList());
            }
        }else if("flag".equalsIgnoreCase(args[0])) {
            // Suggest the list of flags
            if(args.length == 3) {
                return filterStartingWith(args[2], Stream.of(RegionFlag.VALUES).map(Utils::formattedName));
            }else if(args.length == 4) { // Suggest flag values
                RegionFlag flag = Utils.valueOfFormattedName(args[2], RegionFlag.class);
                switch(flag) {
                    case TRUST: // Too complex, not worth giving suggestions when the trust command exists
                        return Collections.emptyList();

                    case DENY_SPAWN: // Give suggestions following the enum filter format (entities)
                        return filterStartingWith(args[3], Stream.of(EntityType.values())
                                .map(e -> args[3].substring(0, args[3].lastIndexOf(',') + 1) + (args[3].contains("*")
                                        ? "!" : "") + Utils.formattedName(e)));

                    case DENY_BREAK:
                    case DENY_PLACE: // Ibid (materials)
                        return filterStartingWith(args[3], Stream.of(Material.values())
                                .map(e -> args[3].substring(0, args[3].lastIndexOf(',') + 1) + (args[3].contains("*")
                                        ? "!" : "") + Utils.formattedName(e)));

                    default:
                        return filterStartingWith(args[3], ALLOW_DENY);
                }
            }
        }else if(("expand".equalsIgnoreCase(args[0]) || "retract".equalsIgnoreCase(args[0])) && args.length == 3)
            // For expansion and retraction just suggest the valid directions
            return filterStartingWith(args[2], EXPANSION_DIRECTIONS);
        else if("delete".equalsIgnoreCase(args[0]) && args.length == 3) // Suggestion for the includeChildren option
            return filterStartingWith(args[1], Stream.of("true", "false"));

        // By default return no suggestions
        return Collections.emptyList();
    }
}
