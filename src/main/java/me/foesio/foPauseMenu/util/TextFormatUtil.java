package me.foesio.foPauseMenu.util;

import java.util.Map;
import java.util.regex.Pattern;

public final class TextFormatUtil {
    private static final Pattern SHADOW_TAG = Pattern.compile("(?i)</?shadow(?::[^>]*)?>");
    private static final Pattern LEGACY_HEX_COLOR = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY_X_HEX_COLOR = Pattern.compile(
            "(?i)&x&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])"
    );
    private static final Pattern SECTION_X_HEX_COLOR = Pattern.compile(
            "(?i)§x§([0-9a-f])§([0-9a-f])§([0-9a-f])§([0-9a-f])§([0-9a-f])§([0-9a-f])"
    );
    private static final Pattern HASH_COLOR = Pattern.compile("(?i)(^|\\s)#([0-9a-f]{6})");
    private static final Map<String, String> LEGACY_CODES = Map.ofEntries(
            Map.entry("0", "<black>"),
            Map.entry("1", "<dark_blue>"),
            Map.entry("2", "<dark_green>"),
            Map.entry("3", "<dark_aqua>"),
            Map.entry("4", "<dark_red>"),
            Map.entry("5", "<dark_purple>"),
            Map.entry("6", "<gold>"),
            Map.entry("7", "<gray>"),
            Map.entry("8", "<dark_gray>"),
            Map.entry("9", "<blue>"),
            Map.entry("a", "<green>"),
            Map.entry("b", "<aqua>"),
            Map.entry("c", "<red>"),
            Map.entry("d", "<light_purple>"),
            Map.entry("e", "<yellow>"),
            Map.entry("f", "<white>"),
            Map.entry("l", "<bold>"),
            Map.entry("m", "<strikethrough>"),
            Map.entry("n", "<underlined>"),
            Map.entry("o", "<italic>"),
            Map.entry("k", "<obfus" + "cated>"),
            Map.entry("r", "<reset>")
    );

    private TextFormatUtil() {
    }

    public static String colorCodesToMiniMessage(String raw) {
        String parsed = raw == null ? "" : raw;
        parsed = SHADOW_TAG.matcher(parsed).replaceAll("");
        parsed = LEGACY_X_HEX_COLOR.matcher(parsed).replaceAll("<#$1$2$3$4$5$6>");
        parsed = LEGACY_HEX_COLOR.matcher(parsed).replaceAll("<#$1>");
        parsed = SECTION_X_HEX_COLOR.matcher(parsed).replaceAll("<#$1$2$3$4$5$6>");
        parsed = HASH_COLOR.matcher(parsed).replaceAll("$1<#$2>");
        for (Map.Entry<String, String> entry : LEGACY_CODES.entrySet()) {
            parsed = parsed.replaceAll("(?i)&" + entry.getKey(), entry.getValue());
            parsed = parsed.replaceAll("(?i)§" + entry.getKey(), entry.getValue());
        }
        return parsed;
    }
}
