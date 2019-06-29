package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionHighlighter;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandClaim extends Command {
    CommandClaim() {
        super("claim", "Make a minimum-sized claim centered at where you are standing.", "/claim");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        int width = (int)Math.ceil(Math.sqrt(RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) / 2);
        Location center = ((Player)sender).getLocation();
        Location min = center.clone().subtract(width - 1, 0, width - 1), max = center.clone().add(width, 0, width);
        Region region = RegionProtection.getDataManager().tryCreateClaim((Player)sender, min, max);
        if(region != null) {
            sender.sendMessage(ChatColor.GREEN + "Created a claim at your location.");
            RegionProtection.getDataManager().getPlayerSession((Player)sender)
                    .setRegionHighlighter(new RegionHighlighter((Player)sender, region));
        }

        return true;
    }
}
