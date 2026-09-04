package me.foesio.foPauseMenu.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class CommandDispatchUtil {
    private CommandDispatchUtil() {
    }

    public static void dispatchAsTypedPlayerCommand(Player player, String command) {
        String parsed = stripSlash(command);
        if (parsed.isBlank()) {
            return;
        }

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/" + parsed);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        parsed = stripSlash(event.getMessage());
        if (!parsed.isBlank()) {
            Bukkit.dispatchCommand(player, parsed);
        }
    }

    public static void dispatchConsoleCommand(String command) {
        String parsed = stripSlash(command);
        if (!parsed.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    public static String stripSlash(String command) {
        String parsed = command == null ? "" : command.trim();
        while (parsed.startsWith("/")) {
            parsed = parsed.substring(1);
        }
        return parsed;
    }
}
