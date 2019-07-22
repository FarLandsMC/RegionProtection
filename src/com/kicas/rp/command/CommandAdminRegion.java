package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.PlayerSession;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allows administrators to toggle between claim creation and admin region creation.
 */
public class CommandAdminRegion implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);
        ps.setInAdminRegionMode(!ps.isInAdminRegionMode());

        sender.sendMessage(ChatColor.GOLD + (ps.isInAdminRegionMode() ? "You can now create admin regions."
                : "You are no longer creating admin regions."));

        return true;
    }
}
