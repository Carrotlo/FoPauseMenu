package me.foesio.foPauseMenu.api;

import org.bukkit.entity.Player;

/** Called after permission checks and the pre-execute event, after the configured action when appended. */
@FunctionalInterface
public interface CustomActionHandler {
    void execute(Player player);
}
