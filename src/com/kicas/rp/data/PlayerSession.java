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
    // All of the following can be null
    private RegionHighlighter currentHighlighter;
    private PlayerRegionAction action;
    private Region currentSelectedRegion;
    private Location lastClickedBlock;

    public PlayerSession(UUID uuid, int claimBlocks) {
        this.uuid = uuid;
        this.claimBlocks = claimBlocks;
        this.currentHighlighter = null;
        this.action = null;
        this.isInAdminRegionMode = false;
        this.currentSelectedRegion = null;
        this.lastClickedBlock = null;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getClaimBlocks() {
        return (int)claimBlocks;
    }

    public void addClaimBlocks(double amount) {
        claimBlocks += amount;
    }

    public void subtractClaimBlocks(int amount) {
        claimBlocks -= amount;
    }

    /**
     * Removes the current highlighter and replaces it with the new, given highlighter. If the given highlighter is not
     * null, then its blocks are shown to the player.
     * @param highlighter the region highlighter.
     */
    public void setRegionHighlighter(RegionHighlighter highlighter) {
        if(currentHighlighter != null && !currentHighlighter.isComplete())
            currentHighlighter.remove();
        currentHighlighter = highlighter;
        if(highlighter != null)
            Bukkit.getScheduler().runTaskLater(RegionProtection.getInstance(), currentHighlighter::showBlocks, 1L);
    }

    public PlayerRegionAction getAction() {
        return action;
    }

    public void setAction(PlayerRegionAction action) {
        this.action = action;
    }

    public boolean isInAdminRegionMode() {
        return isInAdminRegionMode;
    }

    public void setInAdminRegionMode(boolean inAdminRegionMode) {
        isInAdminRegionMode = inAdminRegionMode;
    }

    public boolean isIgnoringTrust() {
        return isIgnoringTrust;
    }

    public void setIgnoringTrust(boolean ignoringTrust) {
        isIgnoringTrust = ignoringTrust;
    }

    public Region getCurrentSelectedRegion() {
        return currentSelectedRegion;
    }

    public void setCurrentSelectedRegion(Region currentSelectedRegion) {
        this.currentSelectedRegion = currentSelectedRegion;
    }

    public Location getLastClickedBlock() {
        return lastClickedBlock;
    }

    public void setLastClickedBlock(Location lastClickedBlock) {
        this.lastClickedBlock = lastClickedBlock;
    }
}
