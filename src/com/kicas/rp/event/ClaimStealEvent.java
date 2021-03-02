package com.kicas.rp.event;

import com.kicas.rp.data.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * The event which is dispatched with a player steals a claim.
 */
public class ClaimStealEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player newOwner;
    private final UUID previousOwner;
    private final Region claim;
    private boolean cancelled;

    /**
     * Constructs a new instance of <code>ClaimStealEvent</code> with the given newOwner and claim.
     *
     * @param newOwner the new owner of the claim
     * @param claim    the claim object
     */
    public ClaimStealEvent(Player newOwner, Region claim) {
        this.newOwner = newOwner;
        this.previousOwner = claim.getOwner();
        this.claim = claim;
        this.cancelled = false;
    }

    /**
     * @return the new owner of the claim.
     */
    public Player getNewOwner() {
        return newOwner;
    }

    /**
     * @return the previous owner the claim.
     */
    public UUID getPreviousOwner() {
        return previousOwner;
    }

    /**
     * @return the claim before it has been registered.
     */
    public Region getRegion() {
        return claim;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public void setCancelled(boolean value) {
        cancelled = value;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
