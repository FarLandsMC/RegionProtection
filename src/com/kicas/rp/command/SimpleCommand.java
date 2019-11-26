package com.kicas.rp.command;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.*;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.TextUtils;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
     */
    public static final SimpleCommand CLAIM = new SimpleCommand((sender, command, alias, args) -> {
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

        // Get the owner in question
        UUID uuid = args.length > 0 && sender.hasPermission("rp.command.externalclaimlist")
                ? RegionProtection.getDataManager().uuidForUsername(args[0]) : ((Player) sender).getUniqueId();

        // Build the list
        List<Region> claimlist = RegionProtection.getDataManager().getRegionsInWorld(((Player) sender).getWorld())
                .stream().filter(region -> !region.isAdminOwned() && region.isOwner(uuid)).collect(Collectors.toList());

        // Format the list and send it to the player
        TextUtils.sendFormatted(sender, "&(gold)%0 {&(aqua)%1} $(inflect,noun,1,claim) in this world:",
                uuid.equals(((Player) sender).getUniqueId()) ? "You have" : args[0] + " has", claimlist.size());
        claimlist.forEach(region -> TextUtils.sendFormatted(sender, "&(gold)%0x, %1z: {&(aqua)%2} claim blocks",
                (int) (0.5 * (region.getMin().getX() + region.getMax().getX())),
                (int) (0.5 * (region.getMin().getZ() + region.getMax().getZ())),
                region.area()));

        // Finally notify the sender of how their claim blocks are being used
        int remaining = RegionProtection.getDataManager().getClaimBlocks(uuid),
                used = claimlist.stream().map(r -> (int) r.area()).reduce(0, Integer::sum);
        TextUtils.sendFormatted(sender, "&(gold){&(aqua)%0} used + {&(aqua)%1} available = {&(aqua)%2} total claim " +
                "blocks", used, remaining, used + remaining);

        return true;
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
    }, (sender, command, alias, args) -> {
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
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAt(((Player) sender).getLocation());
        // Admin claims should be modified in size through /region expand|retract
        if (claim == null || claim.isAdminOwned()) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim that you wish to expand.");
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

        // Do the expansion
        BlockFace facing = ((Player) sender).getFacing();
        if (RegionProtection.getDataManager().tryExpandRegion((Player) sender, claim, facing, amount)) {
            PlayerSession ps = RegionProtection.getDataManager().getPlayerSession((Player) sender);
            sender.sendMessage(ChatColor.GREEN + "Successfully expanded this claim." + (claim.hasParent() ? ""
                    : "You have " + ps.getClaimBlocks() + " claim blocks remaining."));
            ps.setRegionHighlighter(new RegionHighlighter((Player) sender, claim));
        }

        return true;
    });

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

        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAtIgnoreY(((Player) sender).getLocation());
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

        // Check the player exists
        Player player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Could not find player " + args[0]);
            return true;
        }

        // Find a safe place to send the player
        int w = claim.getMax().getBlockX() - claim.getMin().getBlockX(),
                l = claim.getMax().getBlockZ() - claim.getMin().getBlockZ();
        Location ejection = claim.getMin().clone().add(w >> 1, 0, l >> 1); // center x,z of the claim

        // Expel the player // dx dz tend to 0 ~ 0 to avoid world border issues
        player.teleport(Utils.walk(ejection, ejection.getBlockX() < 0 ? w : -w, ejection.getBlockZ() < 0 ? l : -l));
        sender.sendMessage(ChatColor.GREEN + "Expelled player " + player.getName() + " from your claim");

        return true;
    }, (sender, command, alias, args) -> args.length == 1 ? getOnlinePlayers(args[0]) : Collections.emptyList());

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

        // Transfer the claim without transferring the trust flag. This should always succeed.
        RegionProtection.getDataManager().tryTransferOwnership((Player) sender, region, ((Player) sender).getUniqueId(),
                false);

        // Notify the sender
        ps.setRegionHighlighter(new RegionHighlighter((Player) sender, region));
        sender.sendMessage(ChatColor.GREEN + "This claim is now yours. You have " + ps.getClaimBlocks() + " claim " +
                "blocks remaining.");

        return true;
    });

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
        Region region = RegionProtection.getDataManager().getParentRegionsAt(((Player) sender).getLocation()).stream()
                .filter(r -> r.isEffectiveOwner((Player) sender)).findAny().orElse(null);
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

        // Find a safe place to send the player
        int w = claim.getMax().getBlockX() - claim.getMin().getBlockX(),
                l = claim.getMax().getBlockZ() - claim.getMin().getBlockZ();
        Location ejection = claim.getMin().clone().add(w >> 1, 0, l >> 1); // center x,z of the claim
        // get dx dz tend to 0 ~ 0 to avoid world border issues
        int dx = ejection.getBlockX() < 0 ? 1 : -1, dz = ejection.getBlockZ() < 0 ? 1 : -1;
        ejection.add(w * dx, 0, l * dz); // get to the edge of the claim closest to 0 ~ 0

        // Free the player
        player.teleport(Utils.walk(ejection, 3 * dx, 3 * dz));
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

        // Make sure the sender is actually standing in a claim
        Region claim = RegionProtection.getDataManager().getHighestPriorityRegionAtIgnoreY(((Player) sender).getLocation());
        if (claim == null) {
            sender.sendMessage(ChatColor.RED + "Please stand in the claim whose trust list you wish to view.");
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
        TextUtils.sendFormatted(sender, "&(gold){&(aqua)Access:} %0\n{&(green)Container:} %1\n{&(yellow)Build:} %2\n" +
                        "{&(blue)Management:} %3", trustList.get(TrustLevel.ACCESS), trustList.get(TrustLevel.CONTAINER),
                trustList.get(TrustLevel.BUILD), trustList.get(TrustLevel.MANAGEMENT));

        return true;
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
