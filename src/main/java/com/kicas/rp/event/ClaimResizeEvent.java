package com.kicas.rp.event;

import com.kicas.rp.data.Region;
import com.kicas.rp.util.Pair;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This event is dispatched when a claim (not an admin region) is resized.
 */
public class ClaimResizeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player delegate;
    private final Region claim;
    private final Pair<Location, Location> oldBounds;
    private boolean cancelled;

    /**
     * Constructs a new instance of <code>ClaimResizeEvent</code> with the given delegate, claim, and old bounds.
     *
     * @param delegate the actor doing the resizing.
     * @param claim   the claim object.
     * @param oldBounds the old claim bounds before the resizing.
     */
    public ClaimResizeEvent(Player delegate, Region claim, Pair<Location, Location> oldBounds) {
        this.delegate = delegate;
        this.claim = claim;
        this.oldBounds = oldBounds;
        this.cancelled = false;
    }

    /**
     * @return the creator of the claim.
     */
    public Player getDelegate() {
        return delegate;
    }

    /**
     * @return the claim before it has been registered.
     */
    public Region getRegion() {
        return claim;
    }

    /**
     * @return the old minimum corner of the claim.
     */
    public Location getOldMin() {
        return oldBounds.getFirst();
    }

    /**
     * @return the old maximum corner of the claim.
     */
    public Location getOldMax() {
        return oldBounds.getSecond();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
