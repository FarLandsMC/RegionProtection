package com.kicas.rp.event;

import com.kicas.rp.data.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * The event which is dispatched with a player steals a claim.
 */
public class ClaimAbandonEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final List<Region> claims;
    private boolean cancelled;
    private boolean isAll;

    /**
     * Constructs a new instance of <code>ClaimAbandonEvent</code> with the given newOwner and claim.
     *
     * @param player  the player who abandoned the claim
     * @param claims  the list of claim objects to be removed
     * @param isAll   if all claims are being removed
     */
    public ClaimAbandonEvent(Player player, List<Region> claims, boolean isAll) {
        this.player = player;
        this.claims = claims;
        this.cancelled = false;
        this.isAll = isAll;
    }

    /**
     * @return the player who abandoned the claim
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return the claims before they have been registered.
     */
    public List<Region> getRegions() {
        return claims;
    }

    /**
     * @return if the event is deleting all claims
     */
    public boolean isAll() {
        return isAll;
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
