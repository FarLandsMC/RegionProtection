package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.data.flagdata.LocationMeta;
import com.kicas.rp.data.flagdata.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.event.ClaimAbandonEvent;
import com.kicas.rp.event.ClaimStealEvent;
import com.kicas.rp.util.Pair;
import com.kicas.rp.util.TextUtils;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleCommand extends TabCompleterBase implements CommandExecutor {
    /**
     * Allows players to delete one, or all of their claims in their current world.
     */
    private static final HashMap<UUID, Pair<List<Region>, Integer>> ranAbandonCommandOnce = new HashMap<>();
    public static final SimpleCommand ABANDON_CLAIM = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Just for convenience
        Player player = (Player) sender;
        DataManager dm = RegionProtection.getDataManager();
        PlayerSession ps = dm.getPlayerSession(player);

        if(args.length == 1 && args[0].equals("confirm")){
            if(ranAbandonCommandOnce.containsKey(player.getUniqueId())) {


                List<Region> regions = ranAbandonCommandOnce.get(player.getUniqueId()).getFirst();
                regions = regions.stream().filter(region -> region.isOwner(player.getUniqueId()) && !region.isAdminOwned()).collect(Collectors.toList()); // Double check that it's okay to delete

                boolean isAll = regions.size() == (int) dm.getPlayerRegions(player, player.getWorld()).stream().filter(region -> region.isOwner(player.getUniqueId()) && !region.isAdminOwned()).count();
                ClaimAbandonEvent event = new ClaimAbandonEvent(((Player) sender), regions, isAll);

                RegionProtection.getInstance().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    sender.sendMessage(ChatColor.RED + "You cannot remove this claim.");
                    return true;
                }

                regions.forEach(region -> {
                    dm.tryDeleteRegion(player, region, true);
                });
                TextUtils.sendFormatted(sender, "&(green)Successfully deleted %0 $(inflect,noun,0,claim).  You now have %1 claim $(inflect,noun,1,block).", regions.size(), ps.getClaimBlocks());
                ps.setRegionHighlighter(null);
                return true;
            }
            TextUtils.sendFormatted(sender, "&(red)You need to specify a claim before confirming.");
            return true;
        }

        Region claim = args.length == 1 ?
                RegionProtection.getDataManager().getPlayerRegionByName(player, player.getWorld(), args[0]) : // Specified claim with /abandonclaim <claim>
                null;

        if(claim == null) // Claim isn't found with name or no specified claim(/abandonclaim <claim>)
            dm.getHighestPriorityRegionAt(player.getLocation()); // Claim based on location
        if (claim == null) // Try again on all Y values if no claim is found at location or specified
            claim = dm.getHighestPriorityRegionAtIgnoreY(player.getLocation());

        List<Region> confirmDeleteRegions;

        // Check if player wants to delete all of their claims
        if (args.length >= 1 && args[0].equals("all")) {
            confirmDeleteRegions = dm.getPlayerRegions(player, player.getWorld()).stream().filter(region -> region.isOwner(player.getUniqueId()) && !region.isAdminOwned()).collect(Collectors.toList()); // Double check that it's only their regions
            TextUtils.sendFormatted(sender, "&(red)Are you sure that you want to delete all of your claims? Type {&(dark_red)/abandonclaim confirm} to confirm.");
            dm.getPlayerSession(player).setRegionHighlighter(new RegionHighlighter(player, confirmDeleteRegions,
                Material.GLOWSTONE, Material.NETHERRACK, true));
        } else {
            // Check to ensure there's a claim at the player's location. Admin claims should be remove through /region
            // delete.
            if (claim == null || claim.isAdminOwned()) {
                sender.sendMessage(ChatColor.RED + "Please specify or stand in the claim which you wish to abandon.");
                return true;
            }

            // Make sure the player has permission
            if (!claim.isEffectiveOwner(player)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to delete this claim.");
                return true;
            }

            // Successful deletion: add claim blocks and send a confirmation message
            if (claim.getChildren().isEmpty()) {
                confirmDeleteRegions = Collections.singletonList(claim);
                TextUtils.sendFormatted(sender, "&(red)Are you sure that you want to remove this claim? Type {&(dark_red)/abandonclaim confirm} to confirm.");
                dm.getPlayerSession(player).setRegionHighlighter(new RegionHighlighter(player, Collections.singletonList(claim),
                    Material.GLOWSTONE, Material.NETHERRACK, false));
            }
            // Failed deletion due to sub-claims present, show those claims
            else {
                TextUtils.sendFormatted(sender, "&(red)There are subclaims which must be removed first.");
                ps.setRegionHighlighter(new RegionHighlighter(player, claim.getChildren(), null, null, false));
                return true;
            }
        }
        ranAbandonCommandOnce.put(player.getUniqueId(), new Pair<>(confirmDeleteRegions, Bukkit.getScheduler()
                .scheduleSyncDelayedTask(RegionProtection.getInstance(), () -> ranAbandonCommandOnce.remove(player.getUniqueId()), 30L * 20L)));
        return true;

    }, (sender, command, alias, args) -> {
        if (args.length == 1) {
            List<String> regions = RegionProtection.getDataManager().getPlayerNamedRegions((Player) sender, ((Player) sender).getWorld());
            regions.add("all");
            return filterStartingWith(args[0], regions);
        }
        return Collections.emptyList();
    });

    /**
     * Allows for players to add an remove co-owners of regions.
     */
    public static final SimpleCommand ADD_CO_OWNER = new SimpleCommand((sender, command, alias, args) -> {
        if (args.length == 0)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Get and check the region
        Region region = args.length == 2 ?
                RegionProtection.getDataManager().getPlayerRegionByName((Player) sender, ((Player) sender).getWorld(), args[1]) :
                RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player) sender).getLocation());

        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the region which you wish to modify co-owners.");
            return true;
        }

        if (!region.isEffectiveOwner((Player)sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to modify co-owners in this claim.");
            return true;
        }

        // Get and check the provided player name
        UUID coOwner = RegionProtection.getDataManager().uuidForUsername(args[0]);
        if (coOwner == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        if ("addcoowner".equals(alias)) {
            if (region.getCoOwners().contains(coOwner)) {
                sender.sendMessage(ChatColor.RED + "This person is already a co-owner.");
                return true;
            }

            region.addCoOwner(coOwner);
            sender.sendMessage(ChatColor.GREEN + args[0] + " is now a co-owner of this region.");
        }
        // "removecoowner"
        else {
            if (region.removeCoOwner(coOwner))
                sender.sendMessage(ChatColor.GREEN + args[0] + " is no longer a co-owner of this region.");
            else
                sender.sendMessage(ChatColor.RED + args[0] + " is not a co-owner of this region, so they could not be removed.");
        }

        return true;
    }, (sender, command, alias, args) -> {
        switch(args.length){
            case 1:
                if(alias.equalsIgnoreCase("removecoowner")){
                    List<String> values = new ArrayList<>();
                    RegionProtection.getDataManager().getPlayerRegions(((Player) sender), ((Player) sender).getWorld()).forEach(x -> {
                        values.addAll(x.getCoOwners().stream().map(RegionProtection.getDataManager()::currentUsernameForUuid).collect(Collectors.toList()));
                    });
                    return filterStartingWith(args[0], values);
                }
                return getOnlinePlayers(args[0]);
            case 2:
                if(!alias.equalsIgnoreCase("removecoowner"))
                    return filterStartingWith(args[1], RegionProtection.getDataManager().getPlayerNamedRegions((Player) sender, ((Player) sender).getWorld()));

                return filterStartingWith(args[1],
                        RegionProtection.getDataManager().getPlayerRegions((Player) sender, ((Player) sender).getWorld())
                        .stream().filter(region ->
                                        region.isCoOwner(RegionProtection.getDataManager().uuidForUsername(args[0])) &&
                                        !region.getRawName().isEmpty() &&
                                        region.getRawName() != null)
                        .map(Region::getRawName).collect(Collectors.toList()));
            default:
                return Collections.emptyList();
        }
    });

    /**
     * Allows administrators to toggle between claim creation and admin region creation.
     */
    public static final SimpleCommand ADMIN_REGION = new SimpleCommand((sender, command, alias, args) -> {
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
    });

    /**
     * Creates a claim of the smallest possible size at the sender's location.
     *
     * /claim [radius] [name]
     */
    public static final SimpleCommand CLAIM = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        if (!RegionProtection.getClaimableWorlds().contains(((Player) sender).getWorld().getUID())) {
            sender.sendMessage(ChatColor.RED + "Claims are not allowed in this world.");
            return true;
        }

        // Calculate the distance from the center using the minimum area, and calculate the corners
        double width;
        if (args.length > 0) {
            try {
                width = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                width = Math.sqrt(RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) / 2;
            }
        } else {
            width = Math.sqrt(RegionProtection.getRPConfig().getInt("general.minimum-claim-size")) / 2;
        }

        Location center = ((Player) sender).getLocation();
        Location min = center.clone().subtract(width, DataManager.DEFAULT_CLAIM_BOTTOM_Y, width),
                max = center.clone().add(width, 0, width);

        // Attempt to create the region
        Region region = RegionProtection.getDataManager().tryCreateClaim((Player) sender, min, max, true);
        if (region != null) {
            if(args.length > 1){
                region.setName(args[1]);
            }

            int sideLen = (int) (width * 2);
            sender.sendMessage(
                ChatColor.GREEN + "Created a claim at your location " +
                "with a size of " + sideLen + "x" + sideLen +
                (args.length > 1 ? " With the name of " + args[1] + "." : ".")
            );
                        
            // Highlight the region
            RegionProtection.getDataManager().getPlayerSession((Player) sender)
                    .setRegionHighlighter(new RegionHighlighter((Player) sender, region));
        }

        return true;
    });

    /**
     * Allow players to view a list of the claims they have in their current world, including the x and z location as well
     * as the number of claim blocks the take up. Players with OP can specify which player's claim list they wish to view.
     */
    public static final SimpleCommand CLAIM_LIST = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        UUID uuid = args.length > 0 && !"co-owner".equals(args[0]) && sender.hasPermission("rp.command.externalclaimlist")
                ? RegionProtection.getDataManager().uuidForUsername(args[0])
                : ((Player) sender).getUniqueId();
        boolean coOwner = args.length > 0 && "co-owner".equals(args[args.length - 1]);

        // Build the list
        List<Region> claimlist = RegionProtection.getDataManager()
                .getRegionsInWorld(((Player) sender).getWorld())
                .stream()
                .filter(region -> !region.isAdminOwned() && (coOwner ? region.isCoOwner(uuid) : region.isOwner(uuid)))
                .collect(Collectors.toList());

        // Format the list and send it to the player
        if (coOwner) {
            TextUtils.sendFormatted(
                    sender,
                    "&(gold)%0 a co-owner of {&(aqua)%1} $(inflect,noun,1,claim) in this world:",
                    uuid.equals(((Player) sender).getUniqueId())
                            ? "You are"
                            : args[0] + " is",
                    claimlist.size()
            );
        } else {
            TextUtils.sendFormatted(
                    sender,
                    "&(gold)%0 {&(aqua)%1} $(inflect,noun,1,claim) in this world:",
                    uuid.equals(((Player) sender).getUniqueId())
                            ? "You have"
                            : args[0] + " has",
                    claimlist.size()
            );
        }

        claimlist.forEach(region -> {
            TextUtils.sendFormatted(
                    sender,
                    "{&(green)%0}&(gold)%1x, %2z: {&(aqua)%3} claim blocks",
                    region.getRawName() != null && !region.getRawName().isEmpty() ? region.getRawName() + ": " : "",
                    (int) (0.5 * (region.getMin().getX() + region.getMax().getX())),
                    (int) (0.5 * (region.getMin().getZ() + region.getMax().getZ())),
                    region.area()
            );
        });

        if (!coOwner) {
            // Finally notify the sender of how their claim blocks are being used
            int remaining = RegionProtection.getDataManager().getClaimBlocks(uuid),
                    used = claimlist.stream().map(r -> (int) r.area()).reduce(0, Integer::sum);
            TextUtils.sendFormatted(sender, "&(gold){&(aqua)%0} used + {&(aqua)%1} available = {&(aqua)%2} total " +
                    "claim blocks", used, remaining, used + remaining);
        }

        return true;
    }, (sender, command, alias, args) -> {
        switch (args.length) {
            case 1:
                if (sender.hasPermission("rp.command.externalclaimlist")) {
                    List<String> suggestions = new ArrayList<>(getOnlinePlayers(args[0]));
                    suggestions.add("co-owner");
                    return suggestions;
                } else
                    return Collections.singletonList("co-owner");

            case 2:
                return sender.hasPermission("rp.command.externalclaimlist")
                        ? Collections.singletonList("co-owner")
                        : Collections.emptyList();

            default:
                return Collections.emptyList();
        }
    });

    /**
     * Allows a player to toggle on or off tnt in their claim.
     */
    public static final SimpleCommand CLAIM_TOGGLE = new SimpleCommand((sender, command, alias, args) -> {
        if (args.length == 0)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player) sender).getLocation());
        if (claim == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim where you wish to trust this person.");
            return true;
        }

        // Parse the toggle
        RegionFlag toggle = Utils.valueOfFormattedName(args[0], RegionFlag.class);
        if (toggle == null || !toggle.isPlayerToggleable()) {
            sender.sendMessage(ChatColor.RED + "Invalid toggle: " + args[0]);
            return true;
        }

        // Parse/infer the new value
        boolean newValue;
        if (args.length == 2) {
            if ("on".equalsIgnoreCase(args[1]))
                newValue = true;
            else if ("off".equalsIgnoreCase(args[1]))
                newValue = false;
            else {
                sender.sendMessage(ChatColor.RED + "Invalid toggle value: " + args[1] + ". Expected \"on\" or \"off\"");
                return true;
            }
        } else
            newValue = !claim.isAllowed(toggle);

        // Modify the flag value
        claim.setFlag(toggle, newValue);
        sender.sendMessage(ChatColor.GOLD + (newValue ? "Enabled" : "Disabled") + " " + Utils.formattedName(toggle) +
                " in your claim.");

        return true;
    },
            (sender, command, alias, args) -> {
        switch (args.length) {
            case 1: // Toggle suggestions
                return filterStartingWith(args[0], Arrays.stream(RegionFlag.VALUES)
                        .filter(RegionFlag::isPlayerToggleable).map(Utils::formattedName));
            case 2: // Value suggestions
                return filterStartingWith(args[1], Stream.of("on", "off"));
            default:
                return Collections.emptyList();
        }
    });

    /**
     * Allow players to expand their claim without needing to walk to a corner with a claim tool.
     */
    public static final SimpleCommand EXPAND_CLAIM = new SimpleCommand((sender, command, alias, args) -> {
        if (args.length == 0)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Prioritize sub-claims
        Region claim = args.length == 2 ?
                RegionProtection.getDataManager().getPlayerRegionByName((Player) sender, ((Player) sender).getWorld(), args[1]) :
                RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player) sender).getLocation());

        // Admin claims should be modified in size through /region expand|retract
        if (claim == null || claim.isAdminOwned()) {
            sender.sendMessage(ChatColor.RED + "Please specify or stand in the claim that you wish to expand.");
            return true;
        }

        // Permission check
        if (!claim.isEffectiveOwner((Player) sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to expand this claim.");
            return true;
        }

        // Evaluate the amount
        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[0]);
            return true;
        }

        // Do the expansion and highlight the new region
        BlockFace facing = ((Player) sender).getFacing();
        if (RegionProtection.getDataManager().tryExpandRegion((Player) sender, claim, facing, amount)) {
            PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);
            sender.sendMessage(ChatColor.GREEN + "Successfully expanded this claim." + (claim.hasParent() ? ""
                    : "You have " + ps.getClaimBlocks() + " claim blocks remaining."));
            ps.setRegionHighlighter(new RegionHighlighter((Player) sender, claim));
        }

        return true;
    }, (sender,command, alias, args) ->
            args.length == 2 ? filterStartingWith(args[1], RegionProtection.getDataManager().getPlayerNamedRegions((Player) sender, ((Player) sender).getWorld())) :
                    Collections.emptyList()
    );

    /**
     * Allows players to kick* certain players from their claim.
     * * send to the nearest unclaimed block
     */
    public static final SimpleCommand EXPEL = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Check that they are standing in or have specified a claim
        Region claim = args.length == 2 ?
                RegionProtection.getDataManager().getPlayerRegionByName((Player) sender, ((Player) sender).getWorld(), args[1]) :
                RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player) sender).getLocation());

        if (claim == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim where you wish to expel players.");
            return true;
        }

        // Make sure the sender has permission to expel other players
        TrustMeta trustMeta = claim.getAndCreateFlagMeta(RegionFlag.TRUST);
        if (!trustMeta.hasTrust((Player) sender, TrustLevel.MANAGEMENT, claim)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to expel players from this claim.");
            return true;
        }

        // Make sure the owner isn't trying to expel themselves
        if (claim.isEffectiveOwner((Player) sender) && sender.getName().equals(args[0])) {
            sender.sendMessage(ChatColor.RED + "You cannot expel yourself from your own claim.");
            return true;
        }

        // Check the player exists and is in the claim
        Player player = Bukkit.getPlayer(args[0]);
        if (player == null || !claim.containsIgnoreY(player.getLocation())) {
            sender.sendMessage(ChatColor.RED + "Could not find player " + args[0] + " on your claim");
            return true;
        }

        // Do not allow the command to function on op players
        if (player.isOp()) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to expel this player");
            return true;
        }

        // Find a safe place to send the player
        int width  = claim.getMax().getBlockX() - claim.getMin().getBlockX(),
            length = claim.getMax().getBlockZ() - claim.getMin().getBlockZ();
        Location ejection = claim.getMin().clone().add(width >> 1, 0, length >> 1); // Move to center of the claim
        // Calculate a dx & dz that tend to world center to avoid potential world border issues
        int dx = ejection.getBlockX() < 0 ? 1 : -1,
            dz = ejection.getBlockZ() < 0 ? 1 : -1;
        ejection.add(width * dx, 0, length * dz); // Move to the edge of the claim closest to world center

        // Expel the player
        player.teleport(Utils.walk(ejection, 4 * dx, 4 * dz));
        player.sendMessage(ChatColor.RED + "You have been expelled by the owner of this claim");
        sender.sendMessage(ChatColor.GREEN + "Expelled player " + player.getName() + " from your claim");

        return true;
    }, (sender, command, alias, args) -> {
        switch(args.length){
            case 1:
                return getOnlinePlayers(args[0]);
            case 2:
                return filterStartingWith(args[1], RegionProtection.getDataManager().getPlayerNamedRegions((Player) sender, ((Player) sender).getWorld()));
            default:
                return Collections.emptyList();
        }
    });

    /**
     * Allow administrators to ignore the trust flag.
     */
    public static final SimpleCommand IGNORE_TRUST = new SimpleCommand((sender, command, alias, args) -> {
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
    });

    /**
     * Allows players to name their claims.
     */
    public static final SimpleCommand NAME_CLAIM = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        List<String> invalidNames = Arrays.asList("confirm", "all"); // All names that are blocked(for code reasons)
        if(args.length > 0 && invalidNames.contains(args[0])){
            sender.sendMessage(ChatColor.RED + "You cannot name your claim this.");
            return true;
        }

        Region region = RegionProtection.getDataManager().getLowestPriorityRegionAtIgnoreY(((Player) sender).getLocation());
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim you wish to name.");
            return true;
        }


        if (!region.isEffectiveOwner((Player) sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to rename this claim.");
            return true;
        }

        if(args.length == 0){
            region.setName(null);
            sender.sendMessage(ChatColor.GREEN + "Your claim's name has been reset.");
            return true;
        }

        region.setName(args[0]);
        sender.sendMessage(ChatColor.GREEN + "This claim is now named \"" + args[0] + "\"");
        return true;
    });

    /**
     * Show the nearby claims, same as shift right clicking with a stick
     * <p>
     * /nearbyclaims
     */
    public static final SimpleCommand NEARBY_CLAIMS = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        Player player = (Player) sender;

        List<Region> regions = RegionProtection.getDataManager().getRegionsInWorld(player.getWorld()).stream()
            .filter(region -> region.distanceFromEdge(player.getLocation()) < 100 &&
                !region.isAllowed(RegionFlag.OVERLAP)).collect(Collectors.toList());

        // Notify the player
        player.sendMessage(ChatColor.GOLD + "Found " + regions.size() + " claim" + (regions.size() == 1 ? "" : "s") + " nearby.");

        // Highlight the regions
        if (!regions.isEmpty()) {
            RegionProtection.getDataManager().getPlayerSession(player).setRegionHighlighter(new RegionHighlighter(player, regions));
        }
        return true;
    });

    /**
     * Allows a player to take ownership of an expired claim if they have enough claim blocks.
     */
    public static final SimpleCommand STEAL = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Check to make sure stealing is enabled
        if (!RegionProtection.getRPConfig().getBoolean("general.enable-claim-stealing")) {
            sender.sendMessage(ChatColor.RED + "Claim stealing is not enabled on this server.");
            return true;
        }

        // Check for the existence of a region
        List<Region> regions = RegionProtection.getDataManager().getParentRegionsAt(((Player) sender)
                .getLocation()).stream().filter(region -> !region.isAdminOwned() && !region.hasParent())
                .collect(Collectors.toList());
        if (regions.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "There is no region here to be stolen.");
            return true;
        }

        // Make sure the region there has expired
        Region region = regions.stream().filter(r -> r.hasExpired(RegionProtection.getRPConfig()
                .getInt("general.claim-expiration-time") * 24L * 60L * 60L * 1000L)).findAny().orElse(null);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "This region has not expired yet.");
            return true;
        }

        // Check claim blocks
        PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);
        if (ps.getClaimBlocks() < region.area()) {
            sender.sendMessage(ChatColor.RED + "You do not have enough claim blocks to steal this region.");
            return true;
        }


        ClaimStealEvent event = new ClaimStealEvent(((Player) sender), region);
        RegionProtection.getInstance().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            sender.sendMessage(ChatColor.RED + "You cannot remove this claim.");
            return true;
        }

        // Transfer the claim without transferring the trust flag. This should always succeed.
        RegionProtection.getDataManager().tryTransferOwnership((Player) sender, region, ((Player) sender).getUniqueId(),
                false);
        region.setRecentlyStolen(true);

        // Notify the sender
        ps.setRegionHighlighter(new RegionHighlighter((Player) sender, region));
        sender.sendMessage(ChatColor.GREEN + "This claim is now yours. You have " + ps.getClaimBlocks() + " claim " +
                "blocks remaining.");

        return true;
    });

    /**
     * Teleports the sender to the region with the specified name.
     */
    public static final SimpleCommand TO_REGION = new SimpleCommand((sender, command, alias, args) -> {
        if (args.length == 0)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Get and check the region
        Region region = RegionProtection.getDataManager().getRegionByName(player.getWorld(), args[0]);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Region not found.");
            return true;
        }

        // Teleport the sender to the respawn location if it exists, otherwise teleport them to the center of the region
        if (region.hasFlag(RegionFlag.RESPAWN_LOCATION))
            player.teleport(region.<LocationMeta>getFlagMeta(RegionFlag.RESPAWN_LOCATION).getLocation());
        else {
            Location origin = new Location(
                    region.getWorld(),
                    (region.getMin().getX() + region.getMax().getX()) / 2,
                    (region.getMin().getY() + region.getMax().getY()) / 2,
                    (region.getMin().getZ() + region.getMax().getZ()) / 2,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
            );
            player.teleport(Utils.findSafeNear(origin, 1, region.getMax().getBlockY()));
        }

        return true;
    }, (sender, command, alias, args) -> args.length == 1 ? filterStartingWith(args[0], RegionProtection.getDataManager()
            .getRegionNames(((Player) sender).getWorld(), false)) : Collections.emptyList());

    /**
     * Allows the owner of a claim to give someone else ownership of one of their claims.
     */
    public static final SimpleCommand TRANSFER_CLAIM = new SimpleCommand((sender, command, alias, args) -> {
        // Args check
        if (args.length == 0)
            return false;

        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Get and check the specified new owner
        UUID newOwner = RegionProtection.getDataManager().uuidForUsername(args[0]);
        if (newOwner == null) {
            sender.sendMessage(ChatColor.RED + "Invalid username: " + args[0]);
            return true;
        }

        // Get and check the region the sender is standing in
        Region region = RegionProtection.getDataManager().getParentRegionsAt(((Player) sender).getLocation())
            .stream()
            .filter(r ->
                r.isEffectiveOwner((Player) sender) &&
                (!r.isCoOwner(((Player) sender).getUniqueId()) || // Do not allow co-owners to transfer claims.
                sender.isOp()) // Check for op so that staff can still use the command when co-owners
            )
            .findAny()
            .orElse(null);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the region you wish to transfer to this person.");
            return true;
        }

        // Transfer ownership
        if (RegionProtection.getDataManager().tryTransferOwnership((Player) sender, region, newOwner, true))
            sender.sendMessage(ChatColor.GREEN + "This claim is now owned by " + args[0] + ".");

        return true;
    }, (sender, command, alias, args) -> args.length == 1 ? getOnlinePlayers(args[0]) : Collections.emptyList());

    /**
     * Allows a player who is stuck somewhere in a claim to teleport themselves out to another location.
     */
    public static final SimpleCommand TRAPPED = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Make sure the player is actually stuck and cannot dig themselves out to prevent spamming for free teleports
        // stuck meaning that the player has no permissions in the claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAtIgnoreY(player.getLocation());
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(player.getLocation());
        if (claim == null || flags == null || !flags.hasFlag(RegionFlag.DENY_BREAK) ||
                flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(player, TrustLevel.BUILD, flags)) {
            sender.sendMessage(ChatColor.RED + "You should not be trapped in this location");
            return true;
        }

        // Free the player
        Location ejection = player.getLocation();
        player.teleport(Utils.walk(ejection, ejection.getBlockX() < 0 ? 4 : -4, ejection.getBlockZ() < 0 ? 4 : -4));
        sender.sendMessage(ChatColor.GREEN + "Teleported to safety");

        return true;
    });

    /**
     * Shows the sender what levels of trust various players have on the claim they are standing in.
     */
    public static final SimpleCommand TRUST_LIST = new SimpleCommand((sender, command, alias, args) -> {
        // Online sender required
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You must be in-game to use this command.");
            return true;
        }

        // Make sure that the sender has a claim specified
        Region claim = args.length == 1 ?
                RegionProtection.getDataManager().getPlayerRegionByName((Player) sender, ((Player) sender).getWorld(), args[0]) :
                RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player) sender).getLocation());

        if (claim == null) {
            sender.sendMessage(ChatColor.RED + "Please specify or stand in the claim whose trust list you wish to view.");
            return true;
        }

        // Make sure the sender has permission to view the trust list (this includes administrators)
        TrustMeta trustMeta = claim.getAndCreateFlagMeta(RegionFlag.TRUST);
        if (!trustMeta.hasTrust((Player) sender, TrustLevel.MANAGEMENT, claim) && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to view the trust list here.");
            return true;
        }

        // Format and send the trust list
        Map<TrustLevel, String> trustList = trustMeta.getFormattedTrustList();
        TextUtils.sendFormatted(
                sender,
                "&(gold){&(aqua)Access:} %0\n{&(green)Container:} %1\n{&(yellow)Build:} %2\n{&(blue)Management:} %3\n" +
                        "{&(light_purple)Co-Owners:} %4",
                trustList.get(TrustLevel.ACCESS),
                trustList.get(TrustLevel.CONTAINER),
                trustList.get(TrustLevel.BUILD),
                trustList.get(TrustLevel.MANAGEMENT),
                claim.getCoOwners().stream()
                        .map(RegionProtection.getDataManager()::currentUsernameForUuid)
                        .collect(Collectors.joining(", "))
        );

        return true;
    }, (sender, command, alias, args) ->
        args.length == 1 ? filterStartingWith(args[0], RegionProtection.getDataManager().getPlayerNamedRegions((Player) sender, ((Player) sender).getWorld())) :
            Collections.emptyList()
        );

    /**
     * Shows the sender the claims that they have trusted the given player in
     */
    public static final SimpleCommand TRUSTED = new SimpleCommand((sender, command, alias, args) -> {
        if(!(sender instanceof Player)){
            sender.sendMessage(ChatColor.RED + "You must be in-game to run this command.");
            return true;
        }

        if(args.length < 1){
            sender.sendMessage(ChatColor.RED + "Please specify a playername.");
            return true;
        }

        DataManager dm = RegionProtection.getDataManager();
        UUID senderUUID = ((Player) sender).getUniqueId();
        String senderName = sender.getName();
        if(args.length >= 2 && sender.isOp()){
            senderUUID = dm.uuidForUsername(args[1]);
            if(senderUUID == null){
                sender.sendMessage(ChatColor.RED + "Player " + args[1] + " not found.");
                return true;
            }
            senderName = dm.currentUsernameForUuid(senderUUID);
        }

        List<Region> regions = dm.getPlayerRegions(senderUUID, ((Player) sender).getWorld()).stream().filter(region -> ((TrustMeta) region.getAndCreateFlagMeta(RegionFlag.TRUST)).getAllTrustedPlayerNames().contains(args[0])).collect(Collectors.toList());
        StringBuilder formattedRegions = new StringBuilder();
        regions.stream().sorted(Comparator.comparing(x -> x.getRawName() == null ? "" : x.getRawName())).forEach(region -> {
            TrustMeta tm = region.getAndCreateFlagMeta(RegionFlag.TRUST);
            Map<TrustLevel, String> trustList = tm.getFormattedTrustList();
            String trustListInfo = ("&(gold){&(aqua)Access:} " +
                    trustList.get(TrustLevel.ACCESS) +
                    "\n{&(green)Container:} " +
                    trustList.get(TrustLevel.CONTAINER) +
                    "\n{&(yellow)Build:} " +
                    trustList.get(TrustLevel.BUILD) +
                    "\n{&(blue)Management:} " +
                    trustList.get(TrustLevel.MANAGEMENT) +
                    "\n{&(light_purple)Co-Owners:} " + region.getCoOwners().stream()
                    .map(RegionProtection.getDataManager()::currentUsernameForUuid)
                    .collect(Collectors.joining(", "))).replaceAll(",", "");

            TrustLevel tl = tm.getTrustLevelByName(args[0]);
            String tlName = tl != null ? tl.name().toLowerCase().replaceAll("_", " ") : null;
            formattedRegions.append("\n{&(green)")
                    .append(region.getRawName() != null && !region.getRawName().isEmpty() ? region.getRawName() : "") // Region Name
                    .append("}&(gold)(").append((int) (0.5 * (region.getMin().getX() + region.getMax().getX()))).append("x, ") // X coords
                    .append((int) (0.5 * (region.getMin().getZ() + region.getMax().getZ()))).append("z) ") // Y coords
                    .append("{&(aqua)").append(tlName != null && !tlName.isEmpty() ? tlName.substring(0, 1).toUpperCase() + tlName.substring(1) + " Trust" : "").append("} ") // Trust Level
                    .append("${hover,").append(trustListInfo).append(",&(gray)[Trustlist]} "); // Hover Trustlist

        });
        TextUtils.sendFormatted(sender, "&(gold)%0 trusted {&(green)%1} in {&(aqua)%2} ${inflect,noun,2,claim}:%3",
                args.length < 2 && sender.isOp() ? "You have" : senderName + " has",
                args[0],
                regions.size(),
                formattedRegions.toString()
                );

        return true;
    }, (sender, command, alias, args) -> {
        switch(args.length){
            case 1:
                return getOnlinePlayers(args[0]);
            case 2:
                return sender.isOp() ? getOnlinePlayers(args[1]) : Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    });

    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    private SimpleCommand(CommandExecutor executor, TabCompleter tabCompleter) {
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    private SimpleCommand(CommandExecutor executor) {
        this(executor, null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        return executor.onCommand(sender, command, alias, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabCompleter == null ? Collections.emptyList() : tabCompleter.onTabComplete(sender, command, alias, args);
    }
}
