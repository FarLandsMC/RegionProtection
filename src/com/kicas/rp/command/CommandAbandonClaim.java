package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.DataManager;
import com.kicas.rp.data.PlayerSession;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionHighlighter;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allows players to delete one, or all of their claims in their current world.
 */
public class CommandAbandonClaim implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Just for convenience
        Player player = (Player) sender;
        DataManager dm = RegionProtection.getDataManager();
        PlayerSession ps = dm.getPlayerSession(player);

        // Abandon the single claim the player is currently standing in
        if ("abandonclaim".equals(alias)) {
            // Prioritize sub-claims
            Region claim = dm.getHighestPriorityRegionAt(player.getLocation());

            // Check to ensure there's a claim at the player's location. Admin claims should be remove through /region
            // delete.
            if (claim == null || claim.isAdminOwned()) {
                sender.sendMessage(ChatColor.RED + "Please stand in the claim which you wish to abandon.");
                return true;
            }

            // Make sure the player has permission
            if (!claim.isEffectiveOwner(player)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to delete this claim.");
                return true;
            }

            // Successful deletion: add claim blocks and send a confirmation message
            if (dm.tryDeleteRegion(player, claim, false)) {
                sender.sendMessage(ChatColor.GREEN + "Successfully deleted this claim." + (claim.hasParent() ? ""
                        : " You now have " + ps.getClaimBlocks() + " claim blocks."));
                ps.setRegionHighlighter(null);
            } else { // Failed deletion due to sub-claims present, show those claims
                ps.setRegionHighlighter(new RegionHighlighter(player, claim.getChildren(), null, null, false));
            }
        } else { // Abandon all claims including their subdivisions
            dm.tryDeleteRegions(player, player.getWorld(), region -> region.isOwner(player.getUniqueId()) &&
                    !region.isAdminOwned(), true);

            sender.sendMessage(ChatColor.GREEN + "Deleted all your claims in this world. You now have " +
                    ps.getClaimBlocks() + " claim blocks.");
        }

        return true;
    }
}
