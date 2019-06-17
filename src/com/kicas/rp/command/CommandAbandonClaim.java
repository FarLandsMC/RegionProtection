package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.DataManager;
import com.kicas.rp.data.PlayerSession;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionHighlighter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allows players to delete one, or all of their claims in their current world.
 */
public class CommandAbandonClaim extends Command {
    CommandAbandonClaim() {
        super("abandonclaim", "Abandon the claim you are currently standing in.", "/abandonclaim", "abandonallclaims");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Sender check
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Just for convenience
        Player player = (Player) sender;
        DataManager dm = RegionProtection.getDataManager();
        PlayerSession ps = dm.getPlayerSession(player);

        // Abandon the claim the player is currently standing in
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
            if (!claim.isOwner(player)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to delete this claim.");
                return true;
            }

            // Successful deletion: add claim blocks and send a confirmation message
            if (dm.tryDeleteRegion(player, claim, false)) {
                if (!claim.hasParent())
                    ps.addClaimBlocks(claim.area());

                sender.sendMessage(ChatColor.GREEN + "Successfully deleted this claim." + (claim.hasParent() ? ""
                        : " You now have " + ps.getClaimBlocks() + " claim blocks."));
            } else { // Failed deletion due to subclaims present, show those claims
                ps.setRegionHighlighter(new RegionHighlighter(player, claim.getChildren(), Material.GLOWSTONE,
                        Material.NETHERRACK, false));
            }
        } else { // Abandon all claims including their subdivisions
            // This list won't contain subdivisions
            dm.getRegionsInWorld(player.getWorld()).stream().filter(region -> region.isOwner(player) &&
                    !region.isAdminOwned()).forEach(region -> {
                // This will always succeed since children are included
                dm.tryDeleteRegion(player, region, true);
                ps.addClaimBlocks(region.area());
            });

            sender.sendMessage(ChatColor.GREEN + "Deleted all your claims in your current world. You now have " +
                    ps.getClaimBlocks() + " claim blocks.");
        }

        return true;
    }
}
