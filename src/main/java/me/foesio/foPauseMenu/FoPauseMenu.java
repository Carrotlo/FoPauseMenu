package me.foesio.foPauseMenu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.logging.Level;
import me.foesio.foPauseMenu.api.CustomActionHandler;
import me.foesio.foPauseMenu.api.CustomActionMode;
import me.foesio.foPauseMenu.api.FoPauseMenuApi;
import me.foesio.foPauseMenu.config.ConfigManager;
import me.foesio.foPauseMenu.editor.EditorManager;
import me.foesio.foPauseMenu.message.ComponentTextService;
import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.config.FoConfigDefaults;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundMigrations;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.text.FoText;
import me.foesio.core.update.UpdateNoticeService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoPauseMenu extends JavaPlugin implements FoPauseMenuApi, Listener {
    private final Map<String, CustomRegistration> customActions = new HashMap<>();
    private ConfigManager configManager;
    private FoCoreContext core;
    private FoMessageService coreMessages;
    private ComponentTextService text;
    private DialogService dialogs;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private EditorManager editorManager;
    private UpdateNoticeService updates;
    private FoReloadRegistry reloadRegistry;
    private FoAdminCommand adminCommand;

    @Override
    public void onEnable() {
        FoConfigDefaults.ensureDefaultConfig(this);
        getConfig().set("native-dialogs", null);
        saveConfig();
        saveDefaults();

        this.core = FoPluginCore.create(this);
        this.core.metrics(33822);
        this.core.warnIfNativeDialogsUnavailable();
        this.sounds = core.createSounds(FoSoundMigrations.none());
        this.adminSounds = FoAdminSounds.create(sounds);
        this.editorSounds = FoEditorSounds.create(sounds);
        this.coreMessages = FoMessageService.load(this, messageMigrations());
        this.text = new ComponentTextService(coreMessages);
        this.configManager = new ConfigManager(this, coreMessages);
        this.dialogs = core.dialogService();
        this.editorManager = new EditorManager(this, configManager, text, coreMessages, dialogs);
        this.reloadRegistry = createReloadRegistry();
        this.updates = core.createUpdateNotices(coreMessages, "fopausemenu", adminSounds).start();
        this.adminCommand = createAdminCommand();

        registerCommands();
        registerListener(editorManager);
        registerListener(this);
        getServer().getServicesManager().register(FoPauseMenuApi.class, this, this, ServicePriority.Normal);

        coreMessages.info("console.startup", "{prefix}FoPauseMenu enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(FoPauseMenuApi.class, this);
        customActions.clear();
        if (coreMessages != null) {
            coreMessages.info("console.shutdown", "{prefix}FoPauseMenu disabled.");
        }
        if (core != null) {
            core.close();
            core = null;
        }
    }

    @Override
    public OpenResult open(Player player) {
        requireServerThread();
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            return OpenResult.PLAYER_OFFLINE;
        }
        if (!isEnabled()) {
            return OpenResult.UNAVAILABLE;
        }
        Dialog dialog = RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG)
                .get(FoPauseMenuBootstrap.pauseDialogKey());
        if (dialog == null) {
            return OpenResult.UNAVAILABLE;
        }
        ((Audience) player).showDialog(dialog);
        return OpenResult.OPENED;
    }

    @Override
    public boolean registerCustomAction(String actionId, Plugin owner, CustomActionHandler handler, CustomActionMode mode) {
        requireServerThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(mode, "mode");
        String id = validActionId(actionId);
        if (id == null || !isEnabled() || !owner.isEnabled()
                || !FoPauseMenuBootstrap.activeActionIds().contains(id)) {
            return false;
        }
        CustomRegistration old = customActions.get(id);
        if (old != null && old.owner() != owner) {
            return false;
        }
        customActions.put(id, new CustomRegistration(owner, handler, mode));
        return true;
    }

    @Override
    public boolean unregisterCustomAction(String actionId, Plugin owner) {
        requireServerThread();
        Objects.requireNonNull(owner, "owner");
        String id = validActionId(actionId);
        if (id == null) {
            return false;
        }
        CustomRegistration current = customActions.get(id);
        return current != null && current.owner() == owner && customActions.remove(id, current);
    }

    CustomRegistration customActionRegistration(String actionId) {
        CustomRegistration registration = customActions.get(actionId);
        return registration != null && registration.owner().isEnabled() ? registration : null;
    }

    void runCustomAction(String actionId, Player player, CustomActionHandler handler) {
        try {
            handler.execute(player);
        } catch (RuntimeException ex) {
            String rendered = coreMessages.render("console.custom-action-failed",
                    "{prefix}Custom action {action} failed: {reason}",
                    Map.of("{action}", actionId, "{reason}", String.valueOf(ex.getMessage())));
            getLogger().log(Level.WARNING, FoText.plain(rendered), ex);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        customActions.entrySet().removeIf(entry -> entry.getValue().owner() == event.getPlugin());
    }

    private static String validActionId(String actionId) {
        if (actionId == null) {
            return null;
        }
        String id = actionId.trim().toLowerCase(Locale.ROOT);
        return id.matches("[a-z0-9_-]{1,128}") ? id : null;
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("FoPauseMenu API must be called on the server thread");
        }
    }

    record CustomRegistration(Plugin owner, CustomActionHandler handler, CustomActionMode mode) {
    }

    public boolean reloadPlugin() {
        try {
            FoReloadResult result = reloadRegistry.reload();
            return result.successful();
        } catch (RuntimeException ex) {
            String rendered = coreMessages == null
                    ? "FoPauseMenu reload failed: " + ex.getMessage()
                    : coreMessages.render("console.reload-failed", "{prefix}Reload failed: {reason}.",
                    java.util.Map.of("{reason}", String.valueOf(ex.getMessage())));
            getLogger().warning(FoText.plain(rendered));
            return false;
        }
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public FoCoreContext core() {
        return core;
    }

    public ComponentTextService text() {
        return text;
    }

    public FoMessageService coreMessages() {
        return coreMessages;
    }

    public FoSoundService sounds() {
        return sounds;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public FoEditorSounds editorSounds() {
        return editorSounds;
    }

    public EditorManager editorManager() {
        return editorManager;
    }

    public UpdateNoticeService updates() {
        return updates;
    }

    private void saveDefaults() {
        ResourceFiles.saveDefault(this, "dialogs/pause-menu.yml");
    }

    private FoReloadRegistry createReloadRegistry() {
        return FoReloadRegistry.create()
                .addConfig(this)
                .add("config-manager", configManager::reload)
                .add("sounds", sounds::reload)
                .addMessages(coreMessages)
                .add("editor", editorManager::reload);
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .replaceExact("tokens.prefix", "<#03fc88>FoPauseMenu <dark_gray>» <#a7b8b0>", "&#03fc88FoPauseMenu &8» &#a7b8b0")
                .replaceExact("tokens.theme", "<#03fc88>", "&#03fc88")
                .replaceExact("tokens.muted", "<#a7b8b0>", "&#a7b8b0")
                .replaceExact("tokens.white", "<white>", "&#ffffff")
                .replaceExact("tokens.good", "<#3ecf8e>", "&#3ecf8e")
                .replaceExact("tokens.bad", "<#ff5d73>", "&#ff5d73")
                .removeExact("messages.prompt-start", "{prefix}{muted}Type {theme}{format}{muted} in chat, or {bad}cancel{muted}.")
                .removeExact("messages.prompt-cancelled", "{prefix}{bad}Input cancelled.")
                .removeExact("messages.native-dialog-fallback", "{prefix}{bad}Native dialogs unavailable. {muted}Using chat/fallback input.")
                .removeExact("console.native-dialogs-unavailable", "{prefix}Native dialogs are unavailable; native pause menu requires modern Paper.")
                .remove("messages.version")
                .remove("messages.update-available")
                .remove("messages.update-current")
                .remove("messages.update-failed")
                .remove("console.update-available")
                .remove("console.update-current")
                .remove("console.update-failed")
                .build();
    }

    private void registerCommands() {
        registerBasicCommand("fopausemenu", "FoPauseMenu admin command.", List.of("pausemenu"),
                "fopausemenu.admin", List.of("fopausemenu.admin"), adminCommand);
    }

    private FoAdminCommand createAdminCommand() {
        FoAdminMessages adminMessages = FoAdminMessages.builder()
                .generalNoPermission("messages.no-permission", "{prefix}{bad}No permission.")
                .generalPlayerOnly("messages.player-only", "{prefix}{bad}Player only.")
                .usage("messages.usage-admin", "{prefix}{muted}Use {theme}/fopausemenu <version|reload|editor>{muted}.")
                .reloadSuccess("messages.reload-success", "{prefix}{good}Reloaded config, sounds, messages, and dialog text.")
                .reloadFailed("messages.reload-failed", "{prefix}{bad}Reload failed. {muted}Check console.")
                .editorOpened("messages.editor-opened", "{prefix}{good}Editor opened.")
                .build();
        return FoAdminCommand.builder(this, coreMessages)
                .commandName("fopausemenu")
                .permission("fopausemenu.admin")
                .reloads(reloadRegistry)
                .updates(updates)
                .editor(player -> {
                    editorManager.openMain(player);
                    editorSounds.open(player);
                })
                .adminMessages(adminMessages)
                .adminSounds(adminSounds)
                .build();
    }

    private void registerBasicCommand(String name, String description, Collection<String> aliases,
                                      String visibilityPermission, Collection<String> usePermissions,
                                      org.bukkit.command.CommandExecutor executor) {
        registerCommand(name, description, aliases, new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                executor.onCommand(source.getSender(), null, name, args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (executor instanceof org.bukkit.command.TabCompleter completer) {
                    return completer.onTabComplete(source.getSender(), null, name, args);
                }
                return List.of();
            }

            @Override
            public boolean canUse(org.bukkit.command.CommandSender sender) {
                if (usePermissions == null || usePermissions.isEmpty()) {
                    return true;
                }
                for (String permission : usePermissions) {
                    if (permission == null || permission.isBlank() || sender.hasPermission(permission)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String permission() {
                return visibilityPermission;
            }
        });
    }

    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

}
