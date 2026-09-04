package me.foesio.foPauseMenu.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private FileConfiguration config;
    private YamlConfiguration pauseDialog;
    private final Map<String, MenuAction> actions = new LinkedHashMap<>();

    public ConfigManager(JavaPlugin plugin, FoMessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        reload();
    }

    public void reload() {
        this.config = plugin.getConfig();
        this.pauseDialog = load("dialogs/pause-menu.yml");
        migrateActionVisuals();
        loadActions();
    }

    public boolean pauseMenuEnabled() {
        return config.getBoolean("pause-menu.enabled", true);
    }

    public int columns() {
        return clamp(config.getInt("pause-menu.columns", 2), 1, 4);
    }

    public int buttonWidth() {
        return clamp(config.getInt("pause-menu.button-width", 98), 50, 300);
    }

    public Map<String, MenuAction> actions() {
        return Collections.unmodifiableMap(actions);
    }

    public Optional<MenuAction> action(String id) {
        return Optional.ofNullable(actions.get(id.toLowerCase(Locale.ROOT)));
    }

    public YamlConfiguration pauseDialog() {
        return pauseDialog;
    }

    public boolean setConfigValue(String path, Object value) {
        Object previous = config.get(path);
        config.set(path, value);
        if (!saveConfig()) {
            config.set(path, previous);
            loadActions();
            return false;
        }
        loadActions();
        return true;
    }

    public boolean setPauseDialogValue(String path, Object value) {
        Object previous = pauseDialog.get(path);
        pauseDialog.set(path, value);
        if (!saveYaml(pauseDialog, "dialogs/pause-menu.yml")) {
            pauseDialog.set(path, previous);
            return false;
        }
        return true;
    }

    public boolean addAction(String id) {
        String key = normalizeId(id);
        if (key.isBlank() || actions.containsKey(key)) {
            return false;
        }
        config.set("actions." + key + ".enabled", true);
        config.set("actions." + key + ".type", "player-command");
        config.set("actions." + key + ".command", key);
        config.set("actions." + key + ".label", key);
        config.set("actions." + key + ".tooltip", "{white}Run /" + key + ".");
        config.set("actions." + key + ".width", buttonWidth());
        config.set("actions." + key + ".order", nextActionOrder());
        config.set("actions." + key + ".permission", "");
        if (!saveConfig()) {
            reload();
            return false;
        }
        loadActions();
        return true;
    }

    public boolean deleteAction(String id) {
        String key = normalizeId(id);
        config.set("actions." + key, null);
        if (!saveConfig()) {
            reload();
            return false;
        }
        loadActions();
        return true;
    }

    public boolean saveConfig() {
        try {
            File file = new File(plugin.getDataFolder(), "config.yml");
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            config.save(file);
            return true;
        } catch (IOException ex) {
            warnSave("config.yml", ex.getMessage());
            return false;
        }
    }

    public static String normalizeId(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_\\-]", "-");
    }

    private void loadActions() {
        actions.clear();
        ConfigurationSection section = config.getConfigurationSection("actions");
        if (section == null) {
            return;
        }
        List<MenuAction> loaded = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String id = normalizeId(key);
            String path = "actions." + key + ".";
            String rawType = config.getString(path + "type", "player-command");
            MenuAction.ActionType type = MenuAction.ActionType.fromConfig(rawType);
            String message = config.getString(path + "message", "");
            if ((message == null || message.isBlank()) && "url".equalsIgnoreCase(rawType == null ? "" : rawType.trim())) {
                message = config.getString(path + "url", "");
            }
            loaded.add(new MenuAction(
                    id,
                    config.getBoolean(path + "enabled", true),
                    type,
                    config.getString(path + "command", ""),
                    message,
                    config.getString(path + "permission", ""),
                    actionLabel(path, id),
                    actionTooltip(path, id),
                    clamp(config.getInt(path + "width", buttonWidth()), 50, 300),
                    config.getInt(path + "order", loaded.size() + 1)
            ));
        }
        loaded.sort(Comparator.comparingInt(MenuAction::order));
        for (MenuAction action : loaded) {
            actions.put(action.id(), action);
        }
    }

    private void migrateActionVisuals() {
        ConfigurationSection section = config.getConfigurationSection("actions");
        if (section == null) {
            return;
        }
        boolean changed = false;
        int sequence = 1;
        for (String key : section.getKeys(false)) {
            String id = normalizeId(key);
            String path = "actions." + key + ".";
            if (!config.isSet(path + "label")) {
                config.set(path + "label", legacyButtonString(id, "label", id));
                changed = true;
            }
            if (!config.isSet(path + "tooltip")) {
                config.set(path + "tooltip", legacyButtonString(id, "tooltip", "{white}Run action " + id + "."));
                changed = true;
            }
            if (config.isSet(path + "icon")) {
                String iconToken = DialogIcons.token(config.getString(path + "icon", ""));
                String label = config.getString(path + "label", legacyButtonString(id, "label", id));
                if (!iconToken.isBlank() && label != null && !label.contains(iconToken)) {
                    config.set(path + "label", iconToken + " " + label);
                }
                config.set(path + "icon", null);
                changed = true;
            }
            if (!config.isSet(path + "width")) {
                config.set(path + "width", legacyButtonWidth(id, buttonWidth()));
                changed = true;
            }
            if (!config.isSet(path + "order")) {
                config.set(path + "order", sequence);
                changed = true;
            }
            sequence++;
        }
        if (changed) {
            saveConfig();
        }
    }

    private String actionLabel(String path, String id) {
        String label = config.getString(path + "label", id);
        return label == null || label.isBlank() ? id : label;
    }

    private String actionTooltip(String path, String id) {
        return config.getString(path + "tooltip", "{white}Run action " + id + ".");
    }

    private String legacyButtonString(String id, String field, String fallback) {
        if (pauseDialog == null) {
            return fallback;
        }
        String value = pauseDialog.getString("buttons." + id + "." + field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private int legacyButtonWidth(String id, int fallback) {
        if (pauseDialog == null || !pauseDialog.isInt("buttons." + id + ".width")) {
            return fallback;
        }
        return clamp(pauseDialog.getInt("buttons." + id + ".width", fallback), 50, 300);
    }

    private int nextActionOrder() {
        int highest = 0;
        for (MenuAction action : actions.values()) {
            highest = Math.max(highest, action.order());
        }
        return highest + 1;
    }

    private YamlConfiguration load(String path) {
        File file = new File(plugin.getDataFolder(), path);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource(path)) {
            if (stream == null) {
                return yaml;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                yaml.setDefaults(defaults);
                yaml.options().copyDefaults(true);
                if (backfillMissingDefaults(yaml, defaults)) {
                    saveYaml(yaml, path);
                }
            }
        } catch (IOException ex) {
            warnSave(path, ex.getMessage());
        }
        return yaml;
    }

    private boolean backfillMissingDefaults(YamlConfiguration yaml, YamlConfiguration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || yaml.isSet(key)) {
                continue;
            }
            yaml.set(key, defaults.get(key));
            changed = true;
        }
        return changed;
    }

    private boolean saveYaml(YamlConfiguration yaml, String path) {
        try {
            File file = new File(plugin.getDataFolder(), path);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            warnSave(path, ex.getMessage());
            return false;
        }
    }

    private void warnSave(String file, String reason) {
        String fallback = "{prefix}{bad}Could not save " + file + ": " + reason + ".";
        String rendered = messages.render("console.file-save-failed", fallback,
                Map.of("{file}", file, "{reason}", reason));
        plugin.getLogger().warning(FoText.plain(rendered));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public List<String> actionIds() {
        return new ArrayList<>(actions.keySet());
    }
}
