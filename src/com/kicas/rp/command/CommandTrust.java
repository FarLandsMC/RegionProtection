package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.TrustMeta;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Allows players to grant or deny certain permissions for other players in their claim.
 */
public class CommandTrust extends Command {
    CommandTrust() {
        super("trust", "Give players levels of access to your claim.", "/trust <player>", "accesstrust", "at",
                "containertrust", "ct", "managementtrust", "mt", "untrust");
    }

    @Override
    protected boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Args check
        if(args.length == 0)
            return false;

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

        // Delete the trust meta if it's empty
        if(trustMeta.isEmpty())
            claim.deleteFlag(RegionFlag.TRUST);

        return true;
    }
}
