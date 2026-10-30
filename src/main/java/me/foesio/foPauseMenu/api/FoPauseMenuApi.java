package me.foesio.foPauseMenu.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Public integration service. Obtain it from Bukkit's ServicesManager. */
public interface FoPauseMenuApi {
    enum OpenResult {
        OPENED,
        PLAYER_OFFLINE,
        UNAVAILABLE
    }

    /**
     * Opens the registered pause dialog. Requires the server thread. Returns
     * UNAVAILABLE when this plugin or the dialog registry entry is unavailable.
     */
    OpenResult open(Player player);

    /**
     * Attaches a handler to an action ID currently registered in the pause
     * dialog. By default it runs after the configured behavior; a button with
     * type none runs only the handler. Re-registering by the same owner replaces
     * its handler. Returns false if the button is absent, either plugin is disabled, or another owner already
     * holds the ID. Requires the server thread. Registrations are not persisted.
     */
    default boolean registerCustomAction(String actionId, Plugin owner, CustomActionHandler handler) {
        return registerCustomAction(actionId, owner, handler, CustomActionMode.AFTER_CONFIGURED);
    }

    /** Registers a handler with explicit append or replacement behavior. */
    boolean registerCustomAction(String actionId, Plugin owner, CustomActionHandler handler, CustomActionMode mode);

    /** Removes the owner's handler; the button retains its configured behavior. */
    boolean unregisterCustomAction(String actionId, Plugin owner);
}
