package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.util.TextUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class CommandClaimList extends Command {
    CommandClaimList() {
        super("claimlist", "Show the list of claims that you own.", "/claimlist", "claimslist");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        List<Region> claimlist = RegionProtection.getDataManager().getRegionsInWorld(((Player)sender).getWorld())
                .stream().filter(region -> !region.isAdminOwned() && region.isOwner((Player)sender))
                .collect(Collectors.toList());

        TextUtils.sendFormatted(sender, "&(gold)You have {&(aqua)%0} $(inflect,noun,0,claim) in this world:",
                claimlist.size());
        claimlist.forEach(region -> TextUtils.sendFormatted(sender, "&(gold)%0x, %1z: %2 claim blocks",
                (int)(0.5 * (region.getMin().getX() + region.getMax().getX())),
                (int)(0.5 * (region.getMin().getZ() + region.getMax().getZ())),
                region.area()));

        return true;
    }
}
