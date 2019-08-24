package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionHighlighter;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandClaim implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        if (!RegionProtection.getClaimableWorlds().contains(((Player)sender).getWorld().getUID())) {
            sender.sendMessage(ChatColor.RED + "Claims are not allowed in this world.");
            return true;
        }

        // Calculate the distance from the center using the minimum area, and calculate the corners
        double width = Math.sqrt(RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) / 2;
        Location center = ((Player) sender).getLocation();
        Location min = center.clone().subtract(width, 0, width), max = center.clone().add(width, 0, width);

        // Attempt to create the region
        Region region = RegionProtection.getDataManager().tryCreateClaim((Player) sender, min, max);
        if (region != null) {
            sender.sendMessage(ChatColor.GREEN + "Created a claim at your location.");
            // Highlight the region
            RegionProtection.getDataManager().getPlayerSession((Player) sender)
                    .setRegionHighlighter(new RegionHighlighter((Player) sender, region));
        }

        return true;
    }
}
