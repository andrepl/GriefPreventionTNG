package com.norcode.bukkit.griefprevention.events;

import com.norcode.bukkit.griefprevention.data.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Whenever a claim is created this event is called.
 */
public class NewClaimCreated extends Event implements Cancellable {

    // Custom Event Requirements
    private static final HandlerList handlers = new HandlerList();

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    Claim claim;
    Player p;

    public Player getPlayer() {
        return p;
    }

    public NewClaimCreated(Claim claim, Player p) {
        this.claim = claim;
    }

    public Claim getClaim() {
        return claim;
    }

    boolean canceled = false;

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public void setCancelled(boolean iscancelled) {
        canceled = iscancelled;
    }
}
