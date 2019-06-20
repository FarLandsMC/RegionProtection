package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.TrustMeta;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Allows players to grant or deny certain permissions for other players in their claim.
 */
public class CommandTrust extends Command {
    private static final Map<String, String> HELP_MESSAGES = new HashMap<>();
    private static final String ACCESS_HELP = ChatColor.GOLD + "Grant access to buttons, levers, switches, villager " +
            "trade, crafting and enchant tables, sheep shearing, and animal breeding.";
    private static final String CONTAINER_HELP = ChatColor.GOLD + "Grant someone access to containers, or any block " +
            "or entity which stores items.";
    private static final String BUILD_HELP = ChatColor.GOLD + "Grant someone full access to your claim in every way." +
            " The only thing they will not be able to do is edit the claim\'s size or trust permissions.";
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

    CommandTrust() {
        super("trust", "Give players levels of access to your claim.", "/trust <player>", "accesstrust", "at",
                "containertrust", "ct", "managementtrust", "mt", "untrust");
    }

    @Override
    protected boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Send the help messages
        if(args.length == 0) {
            sender.sendMessage(HELP_MESSAGES.get(alias.toLowerCase()));
            return true;
        }

        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player)sender).getLocation());
        if(claim == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim where you wish to trust this person.");
            return true;
        }

        // Make sure the sender has permission to modify trust levels
        TrustMeta trustMeta = claim.getAndCreateFlagMeta(RegionFlag.TRUST);
        if(!trustMeta.hasTrust((Player)sender, TrustLevel.MANAGEMENT, claim)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to trust people in this claim.");
            return true;
        }

        // Find the trust level based on the alias used
        TrustLevel trust;
        if("accesstrust".equals(alias) || "at".equals(alias))
            trust = TrustLevel.ACCESS;
        else if("containertrust".equals(alias) || "ct".equals(alias))
            trust = TrustLevel.CONTAINER;
        else if("trust".equals(alias))
            trust = TrustLevel.BUILD;
        else if("managementtrust".equals(alias) || "mt".equals(alias))
            trust = TrustLevel.MANAGEMENT;
        else
            trust = TrustLevel.NONE;

        // Grant the trust and notify the sender
        if("public".equals(args[0])) {
            trustMeta.trustPublic(trust);

            if(trust == TrustLevel.NONE)
                sender.sendMessage(ChatColor.GOLD + "Untrusted the public from your claim.");
            else{
                sender.sendMessage(ChatColor.GOLD + "Granted the public " + trust.name().toLowerCase() + " trust in " +
                        "your claim.");
            }
        }else{
            Player player = Bukkit.getPlayer(args[0]);
            UUID uuid;
            if(player == null) {
                uuid = Utils.uuidForUsername(args[0]);
                if(uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
            }else
                uuid = player.getUniqueId();

            if(trust == TrustLevel.NONE) {
                trustMeta.untrust(uuid);
                sender.sendMessage(ChatColor.GOLD + "Untrusted " + args[0] + " from your claim.");
            }else{
                trustMeta.trust(uuid, trust);
                sender.sendMessage(ChatColor.GOLD + "Granted " + args[0] + " " + trust.name().toLowerCase() +
                        " trust in your claim.");
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location)
            throws IllegalArgumentException {
        return args.length == 1 ? getOnlinePlayers(args[0]) : Collections.emptyList();
    }
}
