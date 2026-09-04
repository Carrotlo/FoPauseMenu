package me.foesio.foPauseMenu.message;

import java.util.LinkedHashMap;
import java.util.Map;
import me.foesio.core.message.FoMessageService;
import me.foesio.foPauseMenu.util.TextFormatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Adventure component adapter for Paper dialog/editor surfaces. FoPluginCore
 * owns messages.yml I/O, sends, migrations, tokens, and reloads.
 */
public final class ComponentTextService {
    private static final char LITERAL_OPEN_BRACE = '\uE000';
    private static final char LITERAL_CLOSE_BRACE = '\uE001';
    private static final char LITERAL_AMPERSAND = '\uE002';
    private static final char LITERAL_HASH = '\uE003';

    private final FoMessageService core;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ComponentTextService(FoMessageService core) {
        this.core = core;
    }

    public Component component(String raw) {
        return component(raw, Map.of());
    }

    public Component component(String raw, Map<String, String> replacements) {
        String parsed = raw == null ? "" : raw;
        Map<String, String> all = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : core.tokenValues().entrySet()) {
            all.put("{" + entry.getKey() + "}", entry.getValue());
        }
        all.putAll(replacements == null ? Map.of() : replacements);
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<String, String> entry : all.entrySet()) {
                parsed = parsed.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        parsed = TextFormatUtil.colorCodesToMiniMessage(parsed);
        parsed = restoreLiterals(parsed);
        try {
            return miniMessage.deserialize(parsed);
        } catch (RuntimeException ex) {
            return Component.text(stripFormatting(parsed));
        }
    }

    public Component itemComponent(String raw) {
        return itemComponent(raw, Map.of());
    }

    public Component itemComponent(String raw, Map<String, String> replacements) {
        return component(raw, replacements).decoration(TextDecoration.ITALIC, false);
    }

    public String literal(String raw) {
        String parsed = miniMessage.escapeTags(raw == null ? "" : raw);
        return parsed
                .replace('{', LITERAL_OPEN_BRACE)
                .replace('}', LITERAL_CLOSE_BRACE)
                .replace('&', LITERAL_AMPERSAND)
                .replace('#', LITERAL_HASH);
    }

    private String stripFormatting(String raw) {
        return raw.replaceAll("<[^>]+>", "")
                .replaceAll("(?i)&[0-9a-fk-orx]", "");
    }

    private String restoreLiterals(String raw) {
        return raw
                .replace(LITERAL_OPEN_BRACE, '{')
                .replace(LITERAL_CLOSE_BRACE, '}')
                .replace(LITERAL_AMPERSAND, '&')
                .replace(LITERAL_HASH, '#');
    }
}
