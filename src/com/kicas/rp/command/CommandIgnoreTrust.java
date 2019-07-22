package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.PlayerSession;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allow administrators to ignore the trust flag.
 */
public class CommandIgnoreTrust implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);
        ps.setIgnoringTrust(!ps.isIgnoringTrust());

        sender.sendMessage(ChatColor.GOLD + (ps.isIgnoringTrust() ? "You are now ignoring trust."
                : "You are no longer ignoring trust."));

        return true;
    }
}
