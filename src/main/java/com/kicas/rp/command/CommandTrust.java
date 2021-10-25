package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.data.flagdata.FlagMeta;
import com.kicas.rp.data.flagdata.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Allows players to grant or deny certain permissions for other players in their claim.
 */
public class CommandTrust extends TabCompleterBase implements CommandExecutor {
    private static final Map<String, String> HELP_MESSAGES = new HashMap<>();
    private static final String ACCESS_HELP = ChatColor.GOLD + "Grant access to buttons, levers, switches, villager " +
            "trade, crafting and enchant tables, sheep shearing, animal breeding, and boats.";
    private static final String CONTAINER_HELP = ChatColor.GOLD + "Grant someone access to containers, or any block " +
            "or entity which stores items.";
    private static final String BUILD_HELP = ChatColor.GOLD + "Grant someone full access to your claim in every way." +
            " The only thing they will not be able to do is edit the claim's size or trust permissions.";
    private static final String MANAGEMENT_HELP = ChatColor.GOLD + "Grant someone the ability to edit the trust " +
            "permissions on your claim. They will not be able to resize, subdivide, or delete your claim.";
    private static final String UNTRUST_HELP = ChatColor.GOLD + "Revoke all trust from someone.";

    static {
        HELP_MESSAGES.put("accesstrust", ACCESS_HELP);
        HELP_MESSAGES.put("at", ACCESS_HELP);
        HELP_MESSAGES.put("containertrust", CONTAINER_HELP);
        HELP_MESSAGES.put("ct", CONTAINER_HELP);
        HELP_MESSAGES.put("trust", BUILD_HELP);
        HELP_MESSAGES.put("managementtrust", MANAGEMENT_HELP);
        HELP_MESSAGES.put("mt", MANAGEMENT_HELP);
        HELP_MESSAGES.put("untrust", UNTRUST_HELP);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Send the help messages
        if (args.length == 0) {
            sender.sendMessage(HELP_MESSAGES.get(alias.toLowerCase()));
            return true;
        }
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        List<String> playerNamedRegions = RegionProtection.getDataManager().getPlayerNamedRegions((Player) sender, ((Player) sender).getWorld());

        // Make sure the sender is actually standing in a claim or has one specified
        Region locationClaim = RegionProtection.getDataManager().getHighestPriorityRegionAtIgnoreY(((Player) sender).getLocation());

        Region specifiedClaim = args.length == 2 ? RegionProtection.getDataManager().getPlayerRegionByName((Player) sender, ((Player) sender).getWorld(), args[1]) : null;

        if(locationClaim == null && specifiedClaim == null) {
            sender.sendMessage(ChatColor.RED + "Please " +
                    (playerNamedRegions.isEmpty() ? "" : "specify or ") +
                    "stand in the claim where you wish to trust this person.");
            return true;
        }

        Region claim = specifiedClaim == null ? locationClaim : specifiedClaim;

        // Make sure the sender has permission to modify trust levels
        TrustMeta trustMeta = claim.getAndCreateFlagMeta(RegionFlag.TRUST);
        if (!trustMeta.hasTrust((Player) sender, TrustLevel.MANAGEMENT, claim)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to trust people in this claim.");
            return true;
        }

        // Find the trust level based on the alias used
        TrustLevel trust;
        switch (alias) {
            case "accesstrust":
            case "at":
                trust = TrustLevel.ACCESS;
                break;
            case "containertrust":
            case "ct":
                trust = TrustLevel.CONTAINER;
                break;
            case "trust":
                trust = TrustLevel.BUILD;
                break;
            case "managementtrust":
            case "mt":
                trust = TrustLevel.MANAGEMENT;
                break;
            default: // "untrust"
                trust = TrustLevel.NONE;
                break;
        }

        // Grant the trust and notify the sender
        if ("public".equals(args[0]) || "all".equals(args[0]) || "everyone".equals(args[0]) || "*".equals(args[0])) {
            trustMeta.trustPublic(trust);

            if (trust == TrustLevel.NONE)
                sender.sendMessage(ChatColor.GOLD + "Untrusted the public from your claim.");
            else {
                sender.sendMessage(ChatColor.GOLD + "Granted the public " + trust.name().toLowerCase() + " trust in " +
                        "your claim.");
            }
        }
        // Grant a specific player trust
        else {
            // Get the UUID to trust
            UUID uuid = RegionProtection.getDataManager().uuidForUsername(args[0]);
            if (uuid == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            // Check to make sure the owner isn't demoting themselves
            if (claim.isOwner(uuid)) {
                sender.sendMessage(ChatColor.RED + "You cannot set the trust level of " + args[0] + " in this claim " +
                        "since they are also an owner of the claim.");
                return true;
            }

            // Notify the sender
            if (trust == TrustLevel.NONE) {
                trustMeta.untrust(uuid);
                sender.sendMessage(ChatColor.GOLD + "Untrusted " + args[0] + " from your claim.");
            } else {
                trustMeta.trust(uuid, trust);
                sender.sendMessage(ChatColor.GOLD + "Granted " + args[0] + " " + trust.name().toLowerCase() +
                        " trust in your claim.");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
            throws IllegalArgumentException {
        // Online players
        List<String> values;
        DataManager dm = RegionProtection.getDataManager(); // Just a bit easier
        switch(args.length){
            case 1:
                if(!alias.toLowerCase().startsWith("un")){
                    values = getOnlinePlayers(args[0]);
                    break;
                }
                if(dm.getHighestPriorityRegionAt(((Player) sender).getLocation()) != null){
                    values = ((TrustMeta) dm.getHighestPriorityRegionAt(((Player) sender).getLocation()).getAndCreateFlagMeta(RegionFlag.TRUST)).getAllTrustedPlayerNames();
                    break;
                }
                List<String> finalValues = new ArrayList<>();
                dm.getPlayerRegions((Player) sender, ((Player) sender).getWorld()).forEach(region -> {
                    finalValues.addAll(((TrustMeta) region.getAndCreateFlagMeta(RegionFlag.TRUST)).getAllTrustedPlayerNames());
                });
                values = finalValues;
                break;
            case 2:
                if(!alias.toLowerCase().startsWith("un") || dm.getHighestPriorityRegionAt(((Player) sender).getLocation()) != null) {
                    values = RegionProtection.getDataManager().getPlayerNamedRegions((Player) sender, ((Player) sender).getWorld());
                    break;
                }

                values = dm.getPlayerRegions((Player) sender, ((Player) sender).getWorld())
                        .stream().filter(region ->
                                ((TrustMeta) region.getAndCreateFlagMeta(RegionFlag.TRUST))
                                        .getAllTrustedPlayerNames().contains(args[0]) &&
                                        !region.getRawName().isEmpty() &&
                                        region.getRawName() != null)
                        .map(Region::getRawName).collect(Collectors.toList());

                break;
            default:
                values = Collections.emptyList();
        }
        return filterStartingWith(args[args.length-1], values.stream().filter(x -> x != null && !x.isEmpty()).collect(Collectors.toList()));
    }
}
