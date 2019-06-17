package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.PlayerSession;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allow administrators to ignore the trust flag.
 */
public class CommandIgnoreTrust extends Command {
    CommandIgnoreTrust() {
        super("ignoretrust", "Ignores the trust flag in all regions.", "/ignoretrust");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player)sender);
        ps.setIgnoringTrust(!ps.isIgnoringTrust());

        sender.sendMessage(ChatColor.GOLD + (ps.isIgnoringTrust() ? "You are now ignoring trust."
                : "You are no longer ignoring trust."));

        return true;
    }

    @Override
    public boolean isOpOnly() {
        return true;
    }
}
