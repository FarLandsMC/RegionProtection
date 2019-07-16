package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Allows players to modify the height of their claim or subdivision to some extent.
 */
public class CommandClaimHeight extends TabCompleterBase implements CommandExecutor {
    // For tab completion
    private static final List<String> SUB_COMMANDS = Arrays.asList("get", "set");
    private static final List<String> SIDES = Arrays.asList("top", "bottom");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Args check
        if (args.length < 2)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Get and check the region
        Region region = RegionProtection.getDataManager().getRegionsAtIgnoreY(player.getLocation()).stream()
                .max(Comparator.comparingInt(Region::getPriority)).orElse(null);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim that you wish to modify.");
            return true;
        }

        // Make sure the given side is correct
        if (!SIDES.contains(args[1].toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "Invalid argument: " + args[1] + ".");
            return true;
        }

        // Whether or not we're changing the top location, and also get the vertex to move
        boolean isTop = "top".equals(args[1]);
        Location vertex = isTop ? region.getMax() : region.getMin();

        // Notify the sender of where the location currently is
        if ("get".equalsIgnoreCase(args[0])) {
            // This requires management trust
            if (!region.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player, TrustLevel.MANAGEMENT, region)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command here.");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "The " + (isTop ? "top" : "bottom") + " of this claim is set to " +
                    ChatColor.AQUA + "y=" + vertex.getBlockY());
        } else if ("set".equalsIgnoreCase(args[0])) {
            // Secondary args check
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /claimheight set <top|bottom> <amount>");
                return true;
            }

            // Managers can modify subdivisions, but only owners can modify the actual claim
            if (!(region.hasParent() ? region.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player,
                    TrustLevel.MANAGEMENT, region) : region.isEffectiveOwner(player))) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command here.");
                return true;
            }

            // Parse the new y-value and handle parsing errors
            int newY;
            try {
                newY = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid y-value: " + args[2]);
                return true;
            }

            // Limit the y-value to be within the world height restrictions
            if (newY > region.getWorld().getMaxHeight()) {
                sender.sendMessage(ChatColor.RED + "You cannot extend a claim beyond the maximum world height.");
                return true;
            } else if (newY < 0) {
                sender.sendMessage(ChatColor.RED + "You cannot extend a claim below bedrock.");
                return true;
            }

            // Modify the top
            if (isTop) {
                // Subdivisions
                if (region.hasParent()) {
                    // Make sure the subdivision meets the minimum height requirement
                    if (newY - region.getMin().getBlockY() < RegionProtection.getRPConfig()
                            .getInt("general.minimum-subdivision-height")) {
                        sender.sendMessage(ChatColor.RED + "A subdivision must have a height of at least " +
                                RegionProtection.getRPConfig().getInt("general.minimum-subdivision-height") +
                                " blocks.");
                    } else { // Success: modify the height
                        vertex.setY(newY);
                        sender.sendMessage(ChatColor.GOLD + "The top of this claim is now set to " + ChatColor.AQUA +
                                "y=" + newY);
                    }
                } else { // Don't allow modification of the top of a regular claim
                    sender.sendMessage(ChatColor.RED + "You cannot modify the position of the ceiling of a parent " +
                            "claim.");
                }
            } else { // Modify the bottom
                // Subdivisions
                if (region.hasParent()) {
                    // Make sure the subdivision meets the minimum height requirement
                    if (region.getMax().getBlockY() - newY < RegionProtection.getRPConfig()
                            .getInt("general.minimum-subdivision-height")) {
                        sender.sendMessage(ChatColor.RED + "A subdivision must have a height of at least " +
                                RegionProtection.getRPConfig().getInt("general.minimum-subdivision-height") +
                                " blocks.");
                    } else if (newY < region.getParent().getMin().getBlockY()) {
                        // Ensure the claim does not extend below the parent

                        sender.sendMessage(ChatColor.RED + "You cannot extend this subdivision below the minimum " +
                                "y-level of its parent claim (y=" + region.getParent().getMin().getBlockY() + ").");
                    } else { // Success: modify the height
                        vertex.setY(newY);
                        sender.sendMessage(ChatColor.GOLD + "The bottom of this claim is now set to " + ChatColor.AQUA +
                                "y=" + newY);
                    }
                } else { // Regular claims
                    // Enforce a maximum bound on the bottom of the claim
                    if (newY > 62) {
                        sender.sendMessage(ChatColor.RED + "Your claim must extend down to at least y=62.");
                        return true;
                    }

                    // Success: change the location of the bottom
                    vertex.setY(newY);
                    // Also adjust the bottoms of the children (if necessary) so they don't extend below the parent
                    region.getChildren().stream().filter(child -> child.getMin().getBlockY() < newY)
                            .forEach(child -> child.getMin().setY(newY));

                    sender.sendMessage(ChatColor.GOLD + "The bottom of this claim is now set to " + ChatColor.AQUA +
                            "y=" + newY);
                }
            }
        } else // Invalid sub-command
            sender.sendMessage(ChatColor.RED + "Invalid sub-command: " + args[0]);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        switch (args.length) {
            case 1:
                return filterStartingWith(args[0], SUB_COMMANDS);
            case 2:
                return filterStartingWith(args[1], SIDES);
            default:
                return Collections.emptyList();
        }
    }
}
