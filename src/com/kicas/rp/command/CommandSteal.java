package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.PlayerSession;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionHighlighter;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class CommandSteal extends Command {
    CommandSteal() {
        super("steal", "Take ownership of an expired claim if you have enough claim blocks.", "/steal");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Check to make sure stealing is enabled
        if(!RegionProtection.getRPConfig().getBoolean("general.enable-claim-stealing")) {
            sender.sendMessage(ChatColor.RED + "Claim stealing is not enabled on this server.");
            return true;
        }

        // Check for the existence of a region
        List<Region> regions = RegionProtection.getDataManager().getParentRegionsAt(((Player)sender)
                .getLocation()).stream().filter(region -> !region.isAdminOwned()).collect(Collectors.toList());
        if(regions.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "There is no region here to be stolen.");
            return true;
        }

        // Make sure the region there has expired
        Region region = regions.stream().filter(r -> r.hasExpired(RegionProtection.getRPConfig()
                .getInt("general.claim-expiration-time") * 24L * 60L * 60L * 1000L)).findAny().orElse(null);
        if(region == null) {
            sender.sendMessage(ChatColor.RED + "This region has not expired yet.");
            return true;
        }

        // Check claim blocks
        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player)sender);
        if(ps.getClaimBlocks() < region.area()) {
            sender.sendMessage(ChatColor.RED + "You do not have enough claim blocks to steal this region.");
            return true;
        }

        // Transfer the claim without transferring the trust flag. This should always succeed.
        RegionProtection.getDataManager().tryTransferOwnership((Player)sender, region, ((Player)sender).getUniqueId(),
                false);

        // Notify the sender
        ps.setRegionHighlighter(new RegionHighlighter((Player)sender, region));
        sender.sendMessage(ChatColor.GREEN + "This claim is now yours. You have " + ps.getClaimBlocks() + " claim " +
                "blocks remaining.");

        return true;
    }
}
