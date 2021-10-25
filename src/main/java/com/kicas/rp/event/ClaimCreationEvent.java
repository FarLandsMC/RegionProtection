package com.kicas.rp.event;

import com.kicas.rp.data.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The event which is dispatched when a claim (not an admin region) is created. This event constitutes the final check
 * before a claim is created.
 */
public class ClaimCreationEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player creator;
    private final Region claim;
    private boolean cancelled;

    /**
     * Constructs a new instance of <code>ClaimCreationEvent</code> with the given creator and claim.
     *
     * @param creator the creator of the claim.
     * @param claim   the claim object.
     */
    public ClaimCreationEvent(Player creator, Region claim) {
        this.creator = creator;
        this.claim = claim;
        this.cancelled = false;
    }

    /**
     * @return the creator of the claim.
     */
    public Player getCreator() {
        return creator;
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
