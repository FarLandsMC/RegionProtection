package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Stores transient data for a given player.
 */
public class PlayerSession {
    private UUID uuid;
    private double claimBlocks;
    private boolean isInAdminRegionMode;
    private boolean isIgnoringTrust;
    private long lastMessageSent;
    // All of the following can be null
    private RegionHighlighter currentHighlighter;
    private PlayerRegionAction action;
    private Region currentSelectedRegion;
    private Location lastClickedBlock;

    public PlayerSession(PersistentPlayerData playerData) {
        this.uuid = playerData.getUuid();
        this.claimBlocks = playerData.getClaimBlocks();
        this.currentHighlighter = null;
        this.action = null;
        this.isInAdminRegionMode = false;
        this.currentSelectedRegion = null;
        this.lastClickedBlock = null;
    }

    /**
     * @return the UUID of the associated player.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * @return the number of claim blocks the associated player has.
     */
    public int getClaimBlocks() {
        return (int) claimBlocks;
    }

    /**
     * Adds the given number of claim blocks (or partial claim blocks) to the associated player's claim block count.
     *
     * @param amount the amount of blocks to add.
     */
    public void addClaimBlocks(double amount) {
        claimBlocks += amount;
    }

    /**
     * Subtracts the given number of claim blocks (or partial claim blocks) from the associated player's claim block
     * count.
     *
     * @param amount the amount of blocks to subtract.
     */
    public void subtractClaimBlocks(int amount) {
        claimBlocks -= amount;
    }

    /**
     * Removes the current highlighter and replaces it with the new, given highlighter. If the given highlighter is not
     * null, then it is activated and the client-side block changes are sent.
     *
     * @param highlighter the region highlighter.
     */
    public void setRegionHighlighter(RegionHighlighter highlighter) {
        if (currentHighlighter != null && !currentHighlighter.isComplete())
            currentHighlighter.remove();

        currentHighlighter = highlighter;

        if (highlighter != null)
            Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(), currentHighlighter::showChanges, 1L);
    }

    /**
     * @return the current region action the player is performing, or null if no action is currently being performed.
     */
    public PlayerRegionAction getAction() {
        return action;
    }

    /**
     * Sets the player's region action to the given value.
     *
     * @param action the region action.
     * @see PlayerRegionAction Region Actions
     */
    public void setAction(PlayerRegionAction action) {
        this.action = action;
    }

    /**
     * @return the player's current selected region, or null if no region is currently selected.
     */
    public Region getCurrentSelectedRegion() {
        return currentSelectedRegion;
    }

    /**
     * Sets the player's current selected region to the given region.
     *
     * @param region the selected region.
     */
    public void setCurrentSelectedRegion(Region region) {
        currentSelectedRegion = region;
    }

    /**
     * @return the player's last clicked block.
     */
    public Location getLastClickedBlock() {
        return lastClickedBlock;
    }

    /**
     * Sets the player's last clicked block to the given location.
     *
     * @param lastClickedBlock the last clicked block.
     */
    public void setLastClickedBlock(Location lastClickedBlock) {
        this.lastClickedBlock = lastClickedBlock;
    }

    /**
     * Sets the player's region action, current selected region, and last clicked block to null.
     */
    public void endRegionAction() {
        action = null;
        currentSelectedRegion = null;
        lastClickedBlock = null;
    }

    /**
     * @return true if the player is in admin region mode, false otherwise.
     */
    public boolean isInAdminRegionMode() {
        return isInAdminRegionMode;
    }

    /**
     * Sets whether or not the associated player is in administrative region mode, which allows them to create
     * admin-owned regions.
     *
     * @param inAdminRegionMode whether or not the player is in admin-region mode.
     */
    public void setInAdminRegionMode(boolean inAdminRegionMode) {
        isInAdminRegionMode = inAdminRegionMode;
    }

    /**
     * @return true if the player is ignoring the trust flag restrictions, false otherwise.
     */
    public boolean isIgnoringTrust() {
        return isIgnoringTrust;
    }

    /**
     * Sets whether or not the associated player is ignoring the trust flag, which also makes them the effective owner
     * of every region.
     *
     * @param ignoringTrust whether or not the player is ignoring the trust flag.
     */
    public void setIgnoringTrust(boolean ignoringTrust) {
        isIgnoringTrust = ignoringTrust;
    }

    /**
     * Represents the actions players can be doing regarding regions.
     */
    public enum PlayerRegionAction {
        CREATE_REGION, RESIZE_REGION, SUBDIVIDE_REGION
    }
}
