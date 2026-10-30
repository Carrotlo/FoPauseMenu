package me.foesio.foPauseMenu.api.event;

import java.util.Objects;
import me.foesio.foPauseMenu.api.PauseMenuActionType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the server thread after an action's permission check and before its
 * configured command/message and registered Java handler run. The dialog has
 * already closed. Cancelling suppresses both behaviors; it cannot reopen it.
 */
public final class PauseMenuActionPreExecuteEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String actionId;
    private final PauseMenuActionType actionType;
    private final boolean customActionRegistered;
    private boolean cancelled;

    public PauseMenuActionPreExecuteEvent(Player player, String actionId, PauseMenuActionType actionType,
                                         boolean customActionRegistered) {
        this.player = Objects.requireNonNull(player, "player");
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.customActionRegistered = customActionRegistered;
    }

    public Player getPlayer() {
        return player;
    }

    /** Returns the normalized ID captured when the dialog was registered. */
    public String getActionId() {
        return actionId;
    }

    public PauseMenuActionType getActionType() {
        return actionType;
    }

    /** True when a Java handler is attached to this button for this click. */
    public boolean hasCustomAction() {
        return customActionRegistered;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
