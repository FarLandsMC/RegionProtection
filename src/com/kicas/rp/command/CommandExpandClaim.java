package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.PlayerSession;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionHighlighter;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allow players to expand their claim without needing to walk to a corner with a claim tool.
 */
public class CommandExpandClaim extends Command {
    CommandExpandClaim() {
        super("expandclaim", "Expand your claim in the direction you are facing.", "/expandclaim <amount>");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        if(args.length == 0)
            return false;

        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Prioritize sub-claims
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player)sender).getLocation());
        // Admin claims should be modified in size through /region expand|retract
        if(claim == null || claim.isAdminOwned()) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim that you whish to expand.");
            return true;
        }

        // Permission check
        if(!claim.isEffectiveOwner((Player)sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to expand this claim.");
            return true;
        }

        // Evaluate the amount
        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        }catch(NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[0]);
            return true;
        }

        // Do the expansion
        BlockFace facing = ((Player)sender).getFacing();
        if(RegionProtection.getDataManager().tryExpandRegion((Player)sender, claim, facing, amount)) {
            PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player)sender);
            sender.sendMessage(ChatColor.GREEN + "Successfully expanded this claim." + (claim.hasParent() ? ""
                    : "You have " + ps.getClaimBlocks() + " claim blocks remaining."));
            ps.setRegionHighlighter(new RegionHighlighter((Player)sender, claim));
        }

        return true;
    }
}
