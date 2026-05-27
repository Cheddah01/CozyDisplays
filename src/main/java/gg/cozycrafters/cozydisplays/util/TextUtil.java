package gg.cozycrafters.cozydisplays.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Translates legacy {@code &} color codes into Adventure Components.
 */
public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {
    }

    /**
     * Converts legacy {@code &}-coded text into an Adventure Component.
     * Italic-by-default on text displays is suppressed so admin text renders as written.
     */
    public static Component legacy(String input) {
        String safe = input == null ? "" : input;
        return Component.empty()
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                .append(LEGACY.deserialize(safe));
    }

    /** Plain colored component for plugin system messages. */
    public static Component info(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    public static Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    public static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
