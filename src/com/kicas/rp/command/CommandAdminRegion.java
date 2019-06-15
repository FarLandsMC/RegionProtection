package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.PlayerSession;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allows administrators to toggle between claim creation and admin region creation.
 */
public class CommandAdminRegion extends Command {
    CommandAdminRegion() {
        super("adminregion", "Change into or out of admin region creation mode.", "/adminregion");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player)sender);
        ps.setInAdminRegionMode(!ps.isInAdminRegionMode());

        sender.sendMessage(ChatColor.GOLD + (ps.isInAdminRegionMode() ? "You can now create admin regions."
                : "You are no longer creating admin regions."));

        return true;
    }

    @Override
    public boolean isOpOnly() {
        return true;
    }
}
