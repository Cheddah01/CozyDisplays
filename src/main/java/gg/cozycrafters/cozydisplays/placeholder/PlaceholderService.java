package gg.cozycrafters.cozydisplays.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Optional PlaceholderAPI bridge. Displays are fixed/world-bound, so there is
 * no viewing player context — placeholders are resolved globally by passing a
 * {@code null} player to PlaceholderAPI. Player-specific placeholders therefore
 * resolve to their PlaceholderAPI default/empty value rather than per-viewer.
 */
public final class PlaceholderService {

    private final Plugin plugin;
    private boolean enabled;

    public PlaceholderService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Re-detects PlaceholderAPI; call on enable and on reload. */
    public void detect() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Resolves global placeholders in {@code input}. Returns the original
     * string unchanged if PlaceholderAPI is unavailable or expansion errors.
     */
    public String applyPlaceholders(String input) {
        if (input == null || input.isEmpty() || !enabled) {
            return input;
        }
        try {
            return PlaceholderAPI.setPlaceholders(null, input);
        } catch (Throwable t) {
            plugin.getLogger().warning("PlaceholderAPI failed to parse a display line: "
                    + t.getMessage());
            return input;
        }
    }

    public String applyPlaceholders(Player player, String input) {
        if (input == null || input.isEmpty() || !enabled) {
            return input;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, input);
        } catch (Throwable t) {
            plugin.getLogger().warning("PlaceholderAPI failed to parse a display action: "
                    + t.getMessage());
            return input;
        }
    }
}
