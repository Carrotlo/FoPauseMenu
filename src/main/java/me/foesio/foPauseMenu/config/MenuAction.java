package me.foesio.foPauseMenu.config;

import java.util.Locale;

public final class MenuAction {
    private final String id;
    private final boolean enabled;
    private final ActionType type;
    private final String command;
    private final String message;
    private final String permission;
    private final String label;
    private final String tooltip;
    private final int width;
    private final int order;

    public MenuAction(String id, boolean enabled, ActionType type, String command, String message, String permission,
                      String label, String tooltip, int width, int order) {
        this.id = id;
        this.enabled = enabled;
        this.type = type;
        this.command = command;
        this.message = message;
        this.permission = permission;
        this.label = label;
        this.tooltip = tooltip;
        this.width = width;
        this.order = order;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public ActionType type() {
        return type;
    }

    public String command() {
        return command;
    }

    public String message() {
        return message;
    }

    public String permission() {
        return permission;
    }

    public String label() {
        return label;
    }

    public String tooltip() {
        return tooltip;
    }

    public int width() {
        return width;
    }

    public int order() {
        return order;
    }

    public String value() {
        return switch (type) {
            case PLAYER_COMMAND, CONSOLE_COMMAND -> command;
            case MESSAGE -> message;
            case NONE -> "";
        };
    }

    public enum ActionType {
        PLAYER_COMMAND("player-command"),
        CONSOLE_COMMAND("console-command"),
        MESSAGE("message"),
        NONE("none");

        private final String configName;

        ActionType(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public ActionType next() {
            ActionType[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static ActionType fromConfig(String raw) {
            String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
            if (normalized.equals("url")) {
                return MESSAGE;
            }
            for (ActionType type : values()) {
                if (type.configName.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return type;
                }
            }
            return PLAYER_COMMAND;
        }
    }
}
