package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.TextUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Shows the sender what levels of trust various players have on the claim they are standing in.
 */
public class CommandTrustList implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAtIgnoreY(((Player)sender).getLocation());
        if(claim == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim whose trust list you wish to view.");
            return true;
        }

        // Make sure the sender has permission to view the trust list (this includes administrators)
        TrustMeta trustMeta = claim.getAndCreateFlagMeta(RegionFlag.TRUST);
        if(!trustMeta.hasTrust((Player)sender, TrustLevel.MANAGEMENT, claim) && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to view the trust list here.");
            return true;
        }

        Map<TrustLevel, String> trustList = trustMeta.getFormattedTrustList();
        TextUtils.sendFormatted(sender, "&(gold){&(aqua)Access:} %0\n{&(green)Container:} %1\n{&(yellow)Build:} %2\n" +
                "{&(blue)Management:} %3", trustList.get(TrustLevel.ACCESS), trustList.get(TrustLevel.CONTAINER),
                trustList.get(TrustLevel.BUILD), trustList.get(TrustLevel.MANAGEMENT));

        return true;
    }
}
