package me.foesio.foPauseMenu.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.foesio.foPauseMenu.FoPauseMenu;
import me.foesio.foPauseMenu.config.ConfigManager;
import me.foesio.foPauseMenu.config.MenuAction;
import me.foesio.foPauseMenu.message.ComponentTextService;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.CycleOption;
import me.foesio.core.editor.CycleOptions;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.GuiButtons;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.message.FoMessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class EditorManager implements Listener {
    private static final int MAIN_ROWS = 3;
    private static final int ACTION_EDIT_ROWS = 4;
    private static final int CONFIRM_DELETE_ROWS = 3;
    private static final int MAIN_PAUSE_SLOT = 10;
    private static final int MAIN_TEXT_SLOT = 12;
    private static final int MAIN_COLUMNS_SLOT = 13;
    private static final int MAIN_ACTIONS_SLOT = 14;
    private static final int MAIN_RELOAD_SLOT = 16;
    private static final int ACTION_ENABLED_SLOT = 10;
    private static final int ACTION_TYPE_SLOT = 12;
    private static final int ACTION_HOVER_SLOT = 11;
    private static final int ACTION_TEXT_SLOT = 13;
    private static final int ACTION_VALUE_SLOT = 14;
    private static final int ACTION_ORDER_SLOT = 15;
    private static final int ACTION_PERMISSION_SLOT = 16;
    private static final int ACTION_WIDTH_SLOT = 19;
    private static final int ACTION_DELETE_SLOT = 20;
    private static final int ACTION_EDIT_BACK_SLOT = GuiSlots.bottomMiddleSlot(ACTION_EDIT_ROWS);
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;
    private static final List<CycleOption> ACTION_TYPE_OPTIONS = List.of(
            new CycleOption("player-command", "Player Command"),
            new CycleOption("console-command", "Console Command"),
            new CycleOption("message", "Message"),
            new CycleOption("none", "None")
    );
    private final FoPauseMenu plugin;
    private final ConfigManager config;
    private final ComponentTextService text;
    private final FoMessageService messages;
    private final DialogService dialogs;
    private final Set<UUID> ignoreConfirmClose = new HashSet<>();
    private final Map<UUID, BrowserState> browserStates = new HashMap<>();

    public EditorManager(FoPauseMenu plugin, ConfigManager config, ComponentTextService text,
                         FoMessageService messages, DialogService dialogs) {
        this.plugin = plugin;
        this.config = config;
        this.text = text;
        this.messages = messages;
        this.dialogs = dialogs;
    }

    public void reload() {
        browserStates.clear();
    }

    public void openMain(Player player) {
        Inventory inventory = createInventory(Page.MAIN, null, 0, MAIN_ROWS, "FoPauseMenu Editor");
        fill(inventory);
        put(inventory, MAIN_PAUSE_SLOT, Material.LIME_DYE, "{state}Pause Menu", List.of(
                "{white}Toggle native ESC menu handling.",
                "{muted}Current: {state}{value}"
        ), Map.of(
                "{state}", config.pauseMenuEnabled() ? "{good}" : "{bad}",
                "{value}", config.pauseMenuEnabled() ? "Enabled" : "Disabled"
        ));
        put(inventory, MAIN_TEXT_SLOT, Material.NAME_TAG, "{theme}ESC Button Text", List.of(
                "{white}Change text shown in the ESC menu.",
                "{muted}Current: {white}{value}",
                "{muted}Supports inline color codes.",
                "{muted}Needs restart and player rejoin."
        ), Map.of("{value}", text.literal(config.pauseDialog().getString("external-title", "FoPauseMenu"))));
        put(inventory, MAIN_COLUMNS_SLOT, Material.REPEATER, "{theme}Columns: {white}{value}", List.of(
                "{white}Set buttons per row.",
                "{muted}Range: {theme}1 {white}to {theme}4",
                "{muted}Needs restart and player rejoin."
        ), Map.of("{value}", String.valueOf(config.columns())));
        put(inventory, MAIN_ACTIONS_SLOT, Material.COMPASS, "{theme}Actions", List.of(
                "{white}Edit configured pause menu actions."
        ), Map.of());
        put(inventory, MAIN_RELOAD_SLOT, Material.EMERALD, "{good}Reload", List.of(
                "{white}Reload files from disk."
        ), Map.of());
        player.openInventory(inventory);
    }

    public void openActions(Player player, int page) {
        BrowserState state = browserStates.computeIfAbsent(player.getUniqueId(), uuid -> new BrowserState());
        state.page = Math.max(0, page);
        List<String> ids = filteredActionIds(state.search);
        List<EntryBrowserRequest.Entry> entries = ids.stream()
                .map(config::action)
                .flatMap(java.util.Optional::stream)
                .map(action -> EntryBrowserRequest.Entry.of(action.id(), actionItem(action)))
                .toList();
        EntryBrowserRequest request = EntryBrowserRequest.builder()
                .title("Pause Actions")
                .entries(entries)
                .page(state.page)
                .filter(state.search)
                .showBack(true)
                .addButton(item(Material.ANVIL, "{good}Add Action", List.of(
                        "{white}Create a new action id."
                ), Map.of()))
                .build();
        int maxPage = EntryBrowserMenus.maxPage(request);
        state.page = Math.min(state.page, maxPage);
        EntryBrowserMenus.open(player, request.withPage(state.page));
    }

    public void openActionEdit(Player player, String actionId) {
        MenuAction action = config.action(actionId).orElse(null);
        if (action == null) {
            messages.send(player, "messages.action-missing", "{prefix}{bad}Unknown action.");
            openActions(player, browserStates.getOrDefault(player.getUniqueId(), new BrowserState()).page);
            return;
        }
        Inventory inventory = createInventory(Page.ACTION_EDIT, action.id(), 0, ACTION_EDIT_ROWS, "Action Edit");
        fill(inventory);
        setActionEnabled(inventory, action);
        setType(inventory, action.type());
        put(inventory, ACTION_HOVER_SLOT, Material.PAPER, "{theme}Hover Text", List.of(
                "{white}Edit text shown when hovering.",
                "{muted}Current: {white}{value}",
                "{muted}Supports inline color codes.",
                "{muted}Needs restart and player rejoin."
        ), Map.of("{value}", text.literal(action.tooltip())));
        put(inventory, ACTION_TEXT_SLOT, Material.NAME_TAG, "{theme}Button Text", List.of(
                "{white}Edit text shown on the ESC button.",
                "{muted}Current: {white}{value}",
                "{muted}Supports inline color codes.",
                "{muted}Needs restart and player rejoin."
        ), Map.of("{value}", text.literal(action.label())));
        put(inventory, ACTION_VALUE_SLOT, Material.OAK_SIGN, "{theme}Action Value", List.of(
                "{white}Edit command or message.",
                "{muted}None allows another plugin to handle this button."
        ), Map.of("{value}", text.literal(action.value())));
        put(inventory, ACTION_ORDER_SLOT, Material.HOPPER, "{theme}Order: {white}{value}", List.of(
                "{white}Lower numbers show first.",
                "{muted}Higher numbers show later.",
                "{muted}Needs restart and player rejoin."
        ), Map.of("{value}", String.valueOf(action.order())));
        put(inventory, ACTION_PERMISSION_SLOT, Material.IRON_DOOR, "{theme}Permission", List.of(
                "{white}Edit required permission.",
                "{muted}Empty means everyone.",
                "{muted}Current: {white}{value}"
        ), Map.of("{value}", text.literal(action.permission().isBlank() ? "none" : action.permission())));
        put(inventory, ACTION_WIDTH_SLOT, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "{theme}Width: {white}{value}", List.of(
                "{white}Edit native button width.",
                "{muted}Range: {theme}50 {white}- {theme}300",
                "{muted}Needs restart and player rejoin."
        ), Map.of("{value}", String.valueOf(action.width())));
        put(inventory, ACTION_DELETE_SLOT, Material.LAVA_BUCKET, "{bad}Delete Action", List.of(
                "{white}Open confirmation."
        ), Map.of());
        inventory.setItem(ACTION_EDIT_BACK_SLOT, GuiButtons.back());
        player.openInventory(inventory);
    }

    public void openConfirmDelete(Player player, String actionId) {
        Inventory inventory = createInventory(Page.CONFIRM_DELETE, actionId, 0, CONFIRM_DELETE_ROWS, "Confirm Delete");
        fill(inventory);
        put(inventory, CONFIRM_SLOT, Material.LAVA_BUCKET, "{bad}Confirm Delete", List.of(
                "{white}Delete action {theme}{action}{white}."
        ), Map.of("{action}", actionId));
        inventory.setItem(CANCEL_SLOT, EditorItemFactory.cancel());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Holder) && !(holder instanceof EntryBrowserHolder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (holder instanceof EntryBrowserHolder browserHolder) {
            clickActions(player, EntryBrowserMenus.handleClick(event.getRawSlot(), browserHolder, event.getClick()),
                    browserHolder.request().page());
            return;
        }
        Holder editorHolder = (Holder) holder;
        switch (editorHolder.page()) {
            case MAIN -> clickMain(player, slot);
            case ACTIONS -> {
                // Older open inventories can still exist while the plugin is reloaded.
            }
            case ACTION_EDIT -> clickActionEdit(player, slot, editorHolder.actionId());
            case CONFIRM_DELETE -> clickConfirmDelete(player, slot, editorHolder.actionId());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && plugin.core().inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (holder.page() == Page.CONFIRM_DELETE && !ignoreConfirmClose.remove(uuid)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openActionEdit(player, holder.actionId()));
        }
    }

    private void clickMain(Player player, int slot) {
        if (slot == MAIN_PAUSE_SLOT) {
            boolean value = !config.pauseMenuEnabled();
            if (config.setConfigValue("pause-menu.enabled", value)) {
                plugin.editorSounds().toggle(player, value);
                messages.send(player, "messages.setting-saved", Map.of("{setting}", "pause-menu.enabled"));
                messages.send(player, "messages.restart-required");
            } else {
                messages.send(player, "messages.setting-save-failed", Map.of("{setting}", "pause-menu.enabled"));
            }
            openMain(player);
        } else if (slot == MAIN_TEXT_SLOT) {
            String current = config.pauseDialog().getString("external-title", "FoPauseMenu");
            requestText(player, "ESC Button Text", current, "button text, supports {theme}, &a, #03fc88, &#03fc88", 96,
                    value -> {
                        String text = value.trim();
                        if (text.isBlank()) {
                            messages.send(player, "messages.input-empty");
                            openMain(player);
                            return;
                        }
                        if (config.setPauseDialogValue("external-title", text)) {
                            messages.send(player, "messages.setting-saved", Map.of("{setting}", "ESC button text"));
                            messages.send(player, "messages.restart-required");
                        } else {
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", "ESC button text"));
                        }
                        openMain(player);
                    },
                    () -> openMain(player));
        } else if (slot == MAIN_COLUMNS_SLOT) {
            requestColumns(player);
        } else if (slot == MAIN_ACTIONS_SLOT) {
            openActions(player, 0);
        } else if (slot == MAIN_RELOAD_SLOT) {
            if (plugin.reloadPlugin()) {
                plugin.adminSounds().reload(player);
                messages.send(player, "messages.reload-success");
            } else {
                plugin.adminSounds().reloadError(player);
                messages.send(player, "messages.reload-failed");
            }
            openMain(player);
        }
    }

    private void clickActions(Player player, EntryBrowserClick click, int page) {
        switch (click.action()) {
            case BACK -> {
                plugin.editorSounds().back(player);
                openMain(player);
                return;
            }
            case PREVIOUS_PAGE -> {
                plugin.editorSounds().previousPage(player);
                openActions(player, Math.max(0, page - 1));
                return;
            }
            case NEXT_PAGE -> {
                plugin.editorSounds().nextPage(player);
                openActions(player, page + 1);
                return;
            }
            case ADD -> {
            requestText(player, "Action ID", "", "lowercase id, example: shop", 48,
                    value -> {
                        String id = ConfigManager.normalizeId(value);
                        if (id.isBlank()) {
                            messages.send(player, "messages.invalid-action-id");
                            openActions(player, page);
                            return;
                        }
                        if (config.action(id).isPresent()) {
                            messages.send(player, "messages.action-exists");
                            openActions(player, page);
                            return;
                        }
                        if (!config.addAction(id)) {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", "action " + id));
                            openActions(player, page);
                            return;
                        }
                        plugin.editorSounds().add(player);
                        messages.send(player, "messages.action-added", Map.of("{action}", id));
                        openActionEdit(player, id);
                    },
                    () -> openActions(player, page));
            return;
            }
            case SEARCH, CLEAR_SEARCH -> {
            BrowserState state = browserStates.computeIfAbsent(player.getUniqueId(), uuid -> new BrowserState());
            if (click.action() == EntryBrowserClick.Action.CLEAR_SEARCH) {
                state.search = "";
                state.page = 0;
                plugin.editorSounds().clearSearch(player);
                openActions(player, 0);
                return;
            }
            requestText(player, "Search Actions", state.search, "action id text or empty", 48,
                    value -> {
                        state.search = value.trim();
                        state.page = 0;
                        if (state.search.isBlank()) {
                            plugin.editorSounds().clearSearch(player);
                        } else {
                            plugin.editorSounds().search(player);
                        }
                        openActions(player, 0);
                    },
                    () -> openActions(player, page));
            return;
            }
            case ENTRY -> {
                openActionEdit(player, click.entryId());
            }
            case NONE, EXTRA -> {
                return;
            }
        }
    }

    private void clickActionEdit(Player player, int slot, String actionId) {
        MenuAction action = config.action(actionId).orElse(null);
        if (action == null) {
            messages.send(player, "messages.action-missing");
            openActions(player, 0);
            return;
        }
        String base = "actions." + action.id() + ".";
        if (slot == ACTION_ENABLED_SLOT) {
            boolean enabled = !action.enabled();
            if (config.setConfigValue(base + "enabled", enabled)) {
                plugin.editorSounds().toggle(player, enabled);
                messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                messages.send(player, "messages.restart-required");
            } else {
                plugin.editorSounds().error(player);
                messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
            }
            openActionEdit(player, action.id());
        } else if (slot == ACTION_HOVER_SLOT) {
            requestText(player, "Hover Text", action.tooltip(), "hover text, supports {theme}, &a, #03fc88, &#03fc88", 160,
                    value -> {
                        if (config.setConfigValue(base + "tooltip", value.trim())) {
                            plugin.editorSounds().save(player);
                            messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                            messages.send(player, "messages.restart-required");
                        } else {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
                        }
                        openActionEdit(player, action.id());
                    },
                    () -> openActionEdit(player, action.id()));
        } else if (slot == ACTION_TYPE_SLOT) {
            String nextType = CycleOptions.nextValue(action.type().configName(), ACTION_TYPE_OPTIONS);
            if (config.setConfigValue(base + "type", nextType)) {
                plugin.editorSounds().cycle(player);
                messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                messages.send(player, "messages.restart-required");
            } else {
                plugin.editorSounds().error(player);
                messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
            }
            openActionEdit(player, action.id());
        } else if (slot == ACTION_TEXT_SLOT) {
            String path = "actions." + action.id() + ".label";
            String current = action.label();
            requestText(player, "Button Text", current, "button text, supports {theme}, &a, #03fc88, &#03fc88, :diamond:, :head/Notch:", 96,
                    value -> {
                        String text = value.trim();
                        if (text.isBlank()) {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.button-text-empty");
                            openActionEdit(player, action.id());
                            return;
                        }
                        if (config.setConfigValue(path, text)) {
                            plugin.editorSounds().save(player);
                            messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                            messages.send(player, "messages.restart-required");
                        } else {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
                        }
                        openActionEdit(player, action.id());
                    },
                    () -> openActionEdit(player, action.id()));
        } else if (slot == ACTION_VALUE_SLOT) {
            String field = switch (action.type()) {
                case PLAYER_COMMAND, CONSOLE_COMMAND -> "Command";
                case MESSAGE -> "Message";
                case NONE -> "Value";
            };
            requestText(player, field, action.value(), valueFormat(action.type()), 256,
                    value -> {
                        if (saveActionValue(action.id(), action.type(), value)) {
                            plugin.editorSounds().save(player);
                            messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                            messages.send(player, "messages.restart-required");
                        } else {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
                        }
                        openActionEdit(player, action.id());
                    },
                    () -> openActionEdit(player, action.id()));
        } else if (slot == ACTION_ORDER_SLOT) {
            requestText(player, "Order", String.valueOf(action.order()), "whole number, lower shows first", 16,
                    value -> {
                        Integer order = parseOrder(value);
                        if (order == null) {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.order-invalid");
                            openActionEdit(player, action.id());
                            return;
                        }
                        if (config.setConfigValue(base + "order", order)) {
                            plugin.editorSounds().save(player);
                            messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                            messages.send(player, "messages.restart-required");
                        } else {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
                        }
                        openActionEdit(player, action.id());
                    },
                    () -> openActionEdit(player, action.id()));
        } else if (slot == ACTION_WIDTH_SLOT) {
            requestText(player, "Button Width", String.valueOf(action.width()), "whole number from 50 to 300", 16,
                    value -> {
                        Integer width = parseRange(value, 50, 300);
                        if (width == null) {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.width-invalid");
                            openActionEdit(player, action.id());
                            return;
                        }
                        if (config.setConfigValue(base + "width", width)) {
                            plugin.editorSounds().save(player);
                            messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                            messages.send(player, "messages.restart-required");
                        } else {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
                        }
                        openActionEdit(player, action.id());
                    },
                    () -> openActionEdit(player, action.id()));
        } else if (slot == ACTION_PERMISSION_SLOT) {
            requestText(player, "Permission", action.permission(), "permission node or empty", 96,
                    value -> {
                        if (config.setConfigValue(base + "permission", value.trim())) {
                            plugin.editorSounds().save(player);
                            messages.send(player, "messages.action-saved", Map.of("{action}", action.id()));
                            messages.send(player, "messages.restart-required");
                        } else {
                            plugin.editorSounds().error(player);
                            messages.send(player, "messages.setting-save-failed", Map.of("{setting}", action.id()));
                        }
                        openActionEdit(player, action.id());
                    },
                    () -> openActionEdit(player, action.id()));
        } else if (slot == ACTION_DELETE_SLOT) {
            openConfirmDelete(player, action.id());
        } else if (slot == ACTION_EDIT_BACK_SLOT) {
            plugin.editorSounds().back(player);
            openActions(player, browserStates.getOrDefault(player.getUniqueId(), new BrowserState()).page);
        }
    }

    private void clickConfirmDelete(Player player, int slot, String actionId) {
        if (slot == CONFIRM_SLOT) {
            ignoreConfirmClose.add(player.getUniqueId());
            if (config.deleteAction(actionId)) {
                plugin.editorSounds().delete(player);
                messages.send(player, "messages.action-deleted", Map.of("{action}", actionId));
            } else {
                plugin.editorSounds().error(player);
                messages.send(player, "messages.setting-save-failed", Map.of("{setting}", actionId));
            }
            openActions(player, 0);
        } else if (slot == CANCEL_SLOT) {
            ignoreConfirmClose.add(player.getUniqueId());
            openActionEdit(player, actionId);
        }
    }

    private void requestText(Player player, String field, String current, String format, int maxLength,
                             java.util.function.Consumer<String> submit, Runnable cancel) {
        String initial = current == null ? "" : current;
        TextDialogRequest request = new TextDialogRequest(
                "#03fc88" + field,
                List.of(
                        "#a7b8b0Expected: #03fc88" + format,
                        "#a7b8b0Current: #ffffff" + (initial.isBlank() ? "empty" : initial)
                ),
                "#ffffffValue",
                initial,
                format,
                DialogButton.save(),
                DialogButton.cancel(),
                320,
                300,
                maxLength,
                true,
                true,
                false
        );
        EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.core().inventoryCloseSuppressor(),
                dialogs,
                player,
                request,
                submit,
                cancel
        );
    }

    private void requestColumns(Player player) {
        TextDialogRequest request = TextDialogRequest.number(
                List.of("#a7b8b0Enter the number of buttons shown per row.", "#a7b8b0Allowed range: #03fc881-4"),
                String.valueOf(config.columns()),
                "1-4"
        );
        EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.core().inventoryCloseSuppressor(),
                dialogs,
                player,
                request,
                value -> {
                    Integer columns = parseRange(value, 1, 4);
                    if (columns == null) {
                        plugin.editorSounds().error(player);
                        messages.send(player, "messages.invalid-input", Map.of("{reason}", "Columns must be a whole number from 1 to 4."));
                        openMain(player);
                        return;
                    }
                    if (config.setConfigValue("pause-menu.columns", columns)) {
                        plugin.editorSounds().save(player);
                        messages.send(player, "messages.setting-saved", Map.of("{setting}", "pause-menu.columns"));
                        messages.send(player, "messages.restart-required");
                    } else {
                        plugin.editorSounds().error(player);
                        messages.send(player, "messages.setting-save-failed", Map.of("{setting}", "pause-menu.columns"));
                    }
                    openMain(player);
                },
                () -> openMain(player)
        );
    }

    private boolean saveActionValue(String id, MenuAction.ActionType type, String value) {
        String base = "actions." + id + ".";
        return switch (type) {
            case PLAYER_COMMAND, CONSOLE_COMMAND -> config.setConfigValue(base + "command", value);
            case MESSAGE -> config.setConfigValue(base + "message", value);
            case NONE -> true;
        };
    }

    private String valueFormat(MenuAction.ActionType type) {
        return switch (type) {
            case PLAYER_COMMAND, CONSOLE_COMMAND -> "command with or without slash, supports %player%";
            case MESSAGE -> "message text, supports %player%";
            case NONE -> "no value";
        };
    }

    private List<String> filteredActionIds(String search) {
        String normalized = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        List<String> ids = new ArrayList<>();
        for (String id : config.actionIds()) {
            if (normalized.isBlank() || id.contains(normalized)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private ItemStack actionItem(MenuAction action) {
        ItemStack item = new ItemStack(action.enabled() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text.itemComponent((action.enabled() ? "{good}" : "{bad}") + action.id()));
        meta.lore(List.of(
                text.itemComponent("{white}Order: {theme}" + action.order()),
                text.itemComponent("{white}Width: {theme}" + action.width()),
                text.itemComponent("{white}Type: {theme}" + action.type().configName()),
                text.itemComponent("{white}Value: {muted}" + text.literal(empty(action.value()))),
                text.itemComponent("{white}Permission: {muted}" + text.literal(empty(action.permission())))
        ));
        meta.getPersistentDataContainer().set(Keys.ACTION_ID, org.bukkit.persistence.PersistentDataType.STRING, action.id());
        item.setItemMeta(meta);
        return item;
    }

    private void setActionEnabled(Inventory inventory, MenuAction action) {
        put(inventory, ACTION_ENABLED_SLOT, action.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "{state}{value}", List.of(
                        "{white}Toggle this action.",
                        "{muted}Current: {state}{value}"
                ), Map.of(
                        "{state}", action.enabled() ? "{good}" : "{bad}",
                        "{value}", action.enabled() ? "Enabled" : "Disabled"
                ));
    }

    private void setType(Inventory inventory, MenuAction.ActionType selected) {
        inventory.setItem(ACTION_TYPE_SLOT, EditorItemFactory.cycle(
                messages,
                "Type: " + selected.configName(),
                selected.configName(),
                ACTION_TYPE_OPTIONS
        ));
    }

    private Inventory createInventory(Page page, String actionId, int pageIndex, int rows, String title) {
        return Bukkit.createInventory(new Holder(page, actionId, pageIndex), GuiSlots.sizeForRows(rows),
                GuiTitles.format(title));
    }

    private void put(Inventory inventory, int slot, Material material, String name, List<String> lore,
                     Map<String, String> replacements) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item(material, name, lore, replacements));
        }
    }

    private ItemStack item(Material material, String name, List<String> lore, Map<String, String> replacements) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text.itemComponent(name, replacements));
        meta.lore(lore.stream().map(line -> text.itemComponent(line, replacements)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = EditorItemFactory.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }
    }

    private String empty(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private Integer parseOrder(String raw) {
        return parseRange(raw, 0, Integer.MAX_VALUE);
    }

    private Integer parseRange(String raw, int min, int max) {
        if (raw == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private enum Page {
        MAIN,
        ACTIONS,
        ACTION_EDIT,
        CONFIRM_DELETE
    }

    private record Holder(Page page, String actionId, int pageIndex) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class BrowserState {
        private String search = "";
        private int page;
    }
}
