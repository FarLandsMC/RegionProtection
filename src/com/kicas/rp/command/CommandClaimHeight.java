package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.Region;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.TrustLevel;
import com.kicas.rp.data.TrustMeta;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CommandClaimHeight extends Command {
    private static final List<String> SUB_COMMANDS = Arrays.asList("get", "set");
    private static final List<String> SIDES = Arrays.asList("top", "bottom");

    CommandClaimHeight() {
        super("claimheight", "Change the hight of a claim.", "/claimheight <get|set> <top|bottom> [amount]");
    }

    @Override
    public boolean executeUnsafe(CommandSender sender, String alias, String[] args) {
        // Args check
        if(args.length < 2)
            return false;

        // Sender check
        if(!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        Player player = (Player)sender;
        Region region = RegionProtection.getDataManager().getRegionsAtIgnoreY(player.getLocation()).stream()
                .max(Comparator.comparingInt(Region::getPriority)).orElse(null);

        if(region == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim that you wish to modify.");
            return true;
        }

        if(!SIDES.contains(args[1].toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "Invalid argument: " + args[1] + ".");
            return true;
        }

        boolean isTop = "top".equals(args[1]);
        Location loc = isTop ? region.getMax() : region.getMin();

        if("get".equalsIgnoreCase(args[0])) {
            if(!region.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player, TrustLevel.MANAGEMENT, region)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command here.");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "The " + (isTop ? "top" : "bottom") + " of this claim is set to " +
                    ChatColor.AQUA + "y=" + loc.getBlockY());
        }else if("set".equalsIgnoreCase(args[0])) {
            if(args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /claimheight set <top|bottom> <amount>");
                return true;
            }

            // Managers can modify subdivisions, but only owners can modify the actual claim
            if(!(region.hasParent() ? region.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player,
                    TrustLevel.MANAGEMENT, region) : region.isOwner(player))) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command here.");
                return true;
            }

            int newY;
            try {
                newY = Integer.parseInt(args[2]);
            }catch(NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid y-value: " + args[2]);
                return true;
            }

            if(newY > region.getWorld().getMaxHeight()) {
                sender.sendMessage(ChatColor.RED + "You cannot extend a claim beyond the maximum world height.");
                return true;
            }

            if(isTop) {
                if(region.hasParent()) {
                    if(newY - region.getMin().getBlockY() < RegionProtection.getRPConfig()
                            .getInt("general.minimum-subdivision-height")) {
                        sender.sendMessage(ChatColor.RED + "A subdivision must have a height of at least " +
                                RegionProtection.getRPConfig().getInt("general.minimum-subdivision-height") +
                                " blocks.");
                    }else{
                        loc.setY(newY);
                        sender.sendMessage(ChatColor.GOLD + "The top of this claim is now set to " + ChatColor.AQUA +
                                "y=" + newY);
                    }
                }else{
                    sender.sendMessage(ChatColor.RED + "You cannot modify the position of the ceiling of a parent " +
                            "claim.");
                }
            }else{
                if(region.hasParent()) {
                    if(newY > 62) {
                        sender.sendMessage(ChatColor.RED + "Your claim must extend down to at least y=62.");
                        return true;
                    }
                }else{
                    if(region.getMax().getBlockY() - newY < RegionProtection.getRPConfig()
                            .getInt("general.minimum-subdivision-height")) {
                        sender.sendMessage(ChatColor.RED + "A subdivision must have a height of at least " +
                                RegionProtection.getRPConfig().getInt("general.minimum-subdivision-height") +
                                " blocks.");
                        return true;
                    }else if(newY < region.getParent().getMin().getBlockY()) {
                        sender.sendMessage(ChatColor.RED + "You cannot extend this subdivision below the minimum " +
                                "y-level of its parent claim (y=" + region.getParent().getMin().getBlockY() + ").");
                        return true;
                    }
                }

                loc.setY(newY);
                sender.sendMessage(ChatColor.GOLD + "The bottom of this claim is now set to " + ChatColor.AQUA +
                        "y=" + newY);
            }
        }else
            sender.sendMessage(ChatColor.RED + "Invalid sub-command: " + args[0]);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location)
            throws IllegalArgumentException {
        switch(args.length) {
            case 1:
                return SUB_COMMANDS;
            case 2:
                return SIDES;
            default:
                return Collections.emptyList();
        }
    }
}
