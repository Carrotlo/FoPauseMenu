package me.foesio.foPauseMenu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.foesio.foPauseMenu.api.CustomActionMode;
import me.foesio.foPauseMenu.api.PauseMenuActionType;
import me.foesio.foPauseMenu.api.event.PauseMenuActionPreExecuteEvent;
import me.foesio.foPauseMenu.util.TextFormatUtil;
import me.foesio.foPauseMenu.util.CommandDispatchUtil;
import me.foesio.core.dialog.DialogIcons;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class FoPauseMenuBootstrap implements PluginBootstrap {
    private static final String NAMESPACE = "fopausemenu";
    private static volatile Set<String> activeActionIds = Set.of();
    private static final io.papermc.paper.registry.TypedKey<Dialog> PAUSE_DIALOG =
            DialogKeys.create(Key.key(NAMESPACE, "pause_menu"));
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ClickCallback.Options CALLBACK_OPTIONS =
            ClickCallback.Options.builder()
                    .uses(ClickCallback.UNLIMITED_USES)
                    .lifetime(Duration.ofDays(3650))
                    .build();

    @Override
    public void bootstrap(BootstrapContext context) {
        BootstrapConfig config = BootstrapConfig.load(context.getDataDirectory());
        activeActionIds = config.enabled() ? config.actionIds() : Set.of();
        if (!config.enabled()) {
            context.getLogger().info(config.component(config.consoleRegistryDisabled()));
            return;
        }

        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event ->
                event.registry().register(PAUSE_DIALOG, builder -> builder
                        .base(DialogBase.builder(config.component(config.title()))
                                .externalTitle(config.component(config.externalTitle()))
                                .canCloseWithEscape(true)
                                .pause(false)
                                .afterAction(DialogBase.DialogAfterAction.NONE)
                                .body(config.body().stream()
                                        .map(line -> DialogBody.plainMessage(config.component(line), 320))
                                        .toList())
                                .build())
                        .type(DialogType.multiAction(config.buttons(), config.exitButton(), config.columns()))
                )
        );
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event ->
                event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, List.of(PAUSE_DIALOG))
        );
        context.getLogger().info(config.component(config.consoleRegistryRegistered()));
    }

    static io.papermc.paper.registry.TypedKey<Dialog> pauseDialogKey() {
        return PAUSE_DIALOG;
    }

    static Set<String> activeActionIds() {
        return activeActionIds;
    }

    private static Component component(String raw, Tokens tokens) {
        return component(raw, tokens, Map.of());
    }

    private static Component component(String raw, Tokens tokens, Map<String, String> replacements) {
        String parsed = raw == null ? "" : raw;
        Map<String, String> all = new LinkedHashMap<>();
        all.put("{prefix}", tokens.prefix());
        all.put("{theme}", tokens.theme());
        all.put("{muted}", tokens.muted());
        all.put("{white}", tokens.white());
        all.put("{good}", tokens.good());
        all.put("{bad}", tokens.bad());
        all.putAll(replacements);
        for (Map.Entry<String, String> entry : all.entrySet()) {
            parsed = parsed.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        parsed = TextFormatUtil.colorCodesToMiniMessage(parsed);
        try {
            return DialogIcons.inlineTokens(MINI_MESSAGE.deserialize(parsed));
        } catch (RuntimeException ex) {
            return Component.text(stripFormatting(parsed));
        }
    }

    private static String stripFormatting(String raw) {
        return raw.replaceAll("<[^>]+>", "")
                .replaceAll("(?i)&[0-9a-fk-orx]", "");
    }

    private record Tokens(String prefix, String theme, String muted, String white, String good, String bad) {
        private static Tokens load(YamlConfiguration messages) {
            return new Tokens(
                    messages.getString("tokens.prefix", "<#03fc88>FoPauseMenu <dark_gray>» <#a7b8b0>"),
                    messages.getString("tokens.theme", "<#03fc88>"),
                    messages.getString("tokens.muted", "<#a7b8b0>"),
                    messages.getString("tokens.white", "<white>"),
                    messages.getString("tokens.good", "<#3ecf8e>"),
                    messages.getString("tokens.bad", "<#ff5d73>")
            );
        }
    }

    private record BootstrapAction(
            String id,
            BootstrapActionType type,
            String command,
            String message,
            String permission
    ) {
    }

    private record BootstrapButton(int order, ActionButton button) {
    }

    private record LoadedButtons(List<ActionButton> buttons, Set<String> actionIds) {
    }

    private enum BootstrapActionType {
        PLAYER_COMMAND("player-command"),
        CONSOLE_COMMAND("console-command"),
        MESSAGE("message"),
        NONE("none");

        private final String configName;

        BootstrapActionType(String configName) {
            this.configName = configName;
        }

        private static BootstrapActionType fromConfig(String raw) {
            String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
            if (normalized.equals("url")) {
                return MESSAGE;
            }
            for (BootstrapActionType type : values()) {
                if (type.configName.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return type;
                }
            }
            return PLAYER_COMMAND;
        }
    }

    private record BootstrapConfig(
            boolean enabled,
            String title,
            String externalTitle,
            List<String> body,
            List<ActionButton> buttons,
            ActionButton exitButton,
            int columns,
            Tokens tokens,
            String consoleRegistryDisabled,
            String consoleRegistryRegistered,
            String actionPermission,
            Set<String> actionIds
    ) {
        private static BootstrapConfig load(Path dataDirectory) {
            File dataFolder = dataDirectory.toFile();
            YamlConfiguration config = loadYaml(dataFolder, "config.yml");
            YamlConfiguration dialog = loadYaml(dataFolder, "dialogs/pause-menu.yml");
            YamlConfiguration messages = loadYaml(dataFolder, "messages.yml");
            Tokens tokens = Tokens.load(messages);

            boolean enabled = config.getBoolean("pause-menu.enabled", true);
            int columns = clamp(config.getInt("pause-menu.columns", 2), 1, 4);
            int defaultWidth = clamp(config.getInt("pause-menu.button-width", 98), 50, 300);
            String actionPermission = messages.getString("messages.action-permission", "{prefix}{bad}You cannot use this action.");
            LoadedButtons loadedButtons = loadButtons(config, dialog, tokens, actionPermission, defaultWidth);
            List<ActionButton> buttons = loadedButtons.buttons();
            Set<String> actionIds = loadedButtons.actionIds();
            if (buttons.isEmpty()) {
                BootstrapAction fallback = new BootstrapAction("menu", BootstrapActionType.PLAYER_COMMAND,
                        "menu", "", "");
                buttons = List.of(actionButton(fallback, ":book: Menu", "{white}Open fallback menu.",
                        defaultWidth, tokens, actionPermission));
                actionIds = Set.of("menu");
            }
            ActionButton exit = ActionButton.builder(FoPauseMenuBootstrap.component(dialog.getString("exit-button.label", "{bad}Close"), tokens))
                    .tooltip(FoPauseMenuBootstrap.component(dialog.getString("exit-button.tooltip", "{white}Return to game."), tokens))
                    .width(clamp(dialog.getInt("exit-button.width", defaultWidth), 50, 300))
                    .action(DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player player) {
                            closeScreenThenRun(player, () -> {
                            });
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            return new BootstrapConfig(
                    enabled,
                    dialog.getString("title", "{theme}FoPauseMenu"),
                    dialog.getString("external-title", "FoPauseMenu"),
                    dialog.getStringList("body").isEmpty()
                            ? List.of("{muted}Choose an action.")
                            : dialog.getStringList("body"),
                    buttons,
                    exit,
                    columns,
                    tokens,
                    messages.getString("console.registry-disabled", "{prefix}Pause menu registry disabled in config."),
                    messages.getString("console.registry-registered", "{prefix}Registered native pause menu dialog."),
                    actionPermission,
                    actionIds
            );
        }

        private Component component(String raw) {
            return FoPauseMenuBootstrap.component(raw, tokens);
        }

        private static LoadedButtons loadButtons(YamlConfiguration config, YamlConfiguration dialog, Tokens tokens,
                                                      String actionPermission, int defaultWidth) {
            ConfigurationSection section = config.getConfigurationSection("actions");
            if (section == null) {
                return new LoadedButtons(List.of(), Set.of());
            }
            List<BootstrapButton> buttons = new ArrayList<>();
            Set<String> actionIds = new LinkedHashSet<>();
            int sequence = 1;
            for (String rawId : section.getKeys(false)) {
                String id = normalizeId(rawId);
                if (id.isBlank() || !config.getBoolean("actions." + rawId + ".enabled", true)) {
                    sequence++;
                    continue;
                }
                String path = "actions." + rawId + ".";
                String label = actionLabel(config, dialog, path, id);
                String tooltip = actionTooltip(config, dialog, path, id);
                int width = actionWidth(config, dialog, path, id, defaultWidth);
                String rawType = config.getString(path + "type", "player-command");
                String message = config.getString(path + "message", "");
                if ((message == null || message.isBlank()) && "url".equalsIgnoreCase(rawType == null ? "" : rawType.trim())) {
                    message = config.getString(path + "url", "");
                }
                BootstrapAction action = new BootstrapAction(
                        id,
                        BootstrapActionType.fromConfig(rawType),
                        config.getString(path + "command", ""),
                        message,
                        config.getString(path + "permission", "")
                );
                buttons.add(new BootstrapButton(config.getInt(path + "order", sequence),
                        actionButton(action, label, tooltip, width, tokens, actionPermission)));
                actionIds.add(id);
                sequence++;
            }
            buttons.sort(Comparator.comparingInt(BootstrapButton::order));
            return new LoadedButtons(buttons.stream().map(BootstrapButton::button).toList(), Set.copyOf(actionIds));
        }

        private static ActionButton actionButton(BootstrapAction action, String label, String tooltip, int width,
                                                 Tokens tokens, String actionPermission) {
            return ActionButton.builder(FoPauseMenuBootstrap.component(label, tokens))
                    .tooltip(FoPauseMenuBootstrap.component(tooltip, tokens))
                    .width(width)
                    .action(DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player player) {
                            handleActionClick(player, action, tokens, actionPermission);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();
        }

        private static void executeAction(Player player, BootstrapAction action, Tokens tokens,
                                          String actionPermission) {
            if (!player.isOnline()) {
                return;
            }
            if (!canUse(player, action, tokens, actionPermission)) {
                return;
            }
            Plugin found = Bukkit.getPluginManager().getPlugin("FoPauseMenu");
            if (!(found instanceof FoPauseMenu plugin) || !plugin.isEnabled()) {
                return;
            }
            FoPauseMenu.CustomRegistration custom = plugin.customActionRegistration(action.id());
            boolean configuredAction = hasConfiguredAction(action);
            if (custom == null && !configuredAction) {
                return;
            }
            PauseMenuActionType type = switch (action.type()) {
                case PLAYER_COMMAND -> PauseMenuActionType.PLAYER_COMMAND;
                case CONSOLE_COMMAND -> PauseMenuActionType.CONSOLE_COMMAND;
                case MESSAGE -> PauseMenuActionType.MESSAGE;
                case NONE -> PauseMenuActionType.NONE;
            };
            PauseMenuActionPreExecuteEvent event =
                    new PauseMenuActionPreExecuteEvent(player, action.id(), type, custom != null);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            if (configuredAction && (custom == null || custom.mode() == CustomActionMode.AFTER_CONFIGURED)) {
                Map<String, String> replacements = Map.of(
                        "%player%", player.getName(),
                        "%uuid%", player.getUniqueId().toString()
                );
                switch (action.type()) {
                    case PLAYER_COMMAND -> runPlayerCommand(player, action.command(), replacements);
                    case CONSOLE_COMMAND -> runConsoleCommand(action.command(), replacements);
                    case MESSAGE -> player.sendMessage(FoPauseMenuBootstrap.component(action.message(), tokens, replacements));
                    case NONE -> {
                    }
                }
            }
            if (custom != null && player.isOnline() && plugin.customActionRegistration(action.id()) == custom) {
                plugin.runCustomAction(action.id(), player, custom.handler());
            }
        }

        private static boolean hasConfiguredAction(BootstrapAction action) {
            return switch (action.type()) {
                case PLAYER_COMMAND, CONSOLE_COMMAND -> !CommandDispatchUtil.stripSlash(action.command()).isBlank();
                case MESSAGE -> !action.message().isBlank();
                case NONE -> false;
            };
        }

        private static boolean canUse(Player player, BootstrapAction action, Tokens tokens, String actionPermission) {
            if (action.permission() == null || action.permission().isBlank() || player.hasPermission(action.permission())) {
                return true;
            }
            player.sendMessage(FoPauseMenuBootstrap.component(actionPermission, tokens));
            return false;
        }

        private static void runPlayerCommand(Player player, String command, Map<String, String> replacements) {
            CommandDispatchUtil.dispatchAsTypedPlayerCommand(player, replace(command, replacements));
        }

        private static void runConsoleCommand(String command, Map<String, String> replacements) {
            CommandDispatchUtil.dispatchConsoleCommand(replace(command, replacements));
        }

        private static void handleActionClick(Player player, BootstrapAction action, Tokens tokens,
                                              String actionPermission) {
            Plugin found = Bukkit.getPluginManager().getPlugin("FoPauseMenu");
            if (!(found instanceof FoPauseMenu plugin) || !plugin.isEnabled()) {
                return;
            }
            Runnable click = () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (action.type() == BootstrapActionType.NONE && plugin.customActionRegistration(action.id()) == null) {
                    return;
                }
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> executeAction(player, action, tokens, actionPermission), 1L);
            };
            if (Bukkit.isPrimaryThread()) {
                click.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, click);
            }
        }

        private static void closeScreenThenRun(Player player, Runnable task) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("FoPauseMenu");
            if (plugin == null || !plugin.isEnabled()) {
                return;
            }
            if (Bukkit.isPrimaryThread()) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, task, 1L);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, task, 1L);
            });
        }

        private static String replace(String raw, Map<String, String> replacements) {
            String parsed = raw == null ? "" : raw;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                parsed = parsed.replace(entry.getKey(), entry.getValue());
            }
            return parsed;
        }

        private static String normalizeId(String raw) {
            return raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_\\-]", "-");
        }

        private static String actionLabel(YamlConfiguration config, YamlConfiguration dialog, String path, String id) {
            String label = config.getString(path + "label", "");
            if (label != null && !label.isBlank()) {
                return withLegacyIcon(config, path, label);
            }
            String legacy = dialog.getString("buttons." + id + ".label", "");
            return withLegacyIcon(config, path, legacy == null || legacy.isBlank() ? id : legacy);
        }

        private static String withLegacyIcon(YamlConfiguration config, String path, String label) {
            String token = DialogIcons.token(config.getString(path + "icon", ""));
            if (token.isBlank() || label.contains(token)) {
                return label;
            }
            return token + " " + label;
        }

        private static String actionTooltip(YamlConfiguration config, YamlConfiguration dialog, String path, String id) {
            if (config.isSet(path + "tooltip")) {
                return config.getString(path + "tooltip", "");
            }
            return dialog.getString("buttons." + id + ".tooltip", "{white}Run action " + id + ".");
        }

        private static int actionWidth(YamlConfiguration config, YamlConfiguration dialog, String path, String id,
                                       int defaultWidth) {
            if (config.isSet(path + "width")) {
                return clamp(config.getInt(path + "width", defaultWidth), 50, 300);
            }
            return clamp(dialog.getInt("buttons." + id + ".width", defaultWidth), 50, 300);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static YamlConfiguration loadYaml(File dataFolder, String path) {
            File file = new File(dataFolder, path);
            if (file.exists()) {
                return YamlConfiguration.loadConfiguration(file);
            }
            InputStream stream = FoPauseMenuBootstrap.class.getResourceAsStream("/" + path);
            if (stream == null) {
                return new YamlConfiguration();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            } catch (Exception ex) {
                return new YamlConfiguration();
            }
        }
    }
}
