package gg.cozycrafters.cozydisplays.storage;

import gg.cozycrafters.cozydisplays.display.DisplayData;
import gg.cozycrafters.cozydisplays.display.DisplayType;
import gg.cozycrafters.cozydisplays.display.TextRenderMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads and saves {@code displays.yml}. Handles missing files and invalid
 * entries gracefully without crashing the plugin.
 */
public final class DisplayStorage {

    private final Plugin plugin;
    private final File file;

    public DisplayStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "displays.yml");
    }

    /** Loads all displays from disk, creating the file if missing. */
    public Map<String, DisplayData> load() {
        Map<String, DisplayData> result = new LinkedHashMap<>();

        if (!file.exists()) {
            ensureFile();
            return result;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("displays");
        if (root == null) {
            return result;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                plugin.getLogger().warning("Skipping malformed display entry '" + id + "'.");
                continue;
            }
            try {
                DisplayData data = readEntry(id, sec);
                if (data.getType() == DisplayType.TEXT && data.getLines().isEmpty()) {
                    plugin.getLogger().warning("Display '" + id + "' has no lines; skipping.");
                    continue;
                }
                result.put(id, data);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to load display '" + id + "': " + ex.getMessage());
            }
        }
        return result;
    }

    private DisplayData readEntry(String id, ConfigurationSection sec) {
        DisplayData data = new DisplayData(id);
        data.setType(parseType(sec.getString("type", "TEXT")));
        data.setRawLocation(
                sec.getString("world", "world"),
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw", 0.0D),
                (float) sec.getDouble("pitch", 0.0D));

        data.setTextRenderMode(parseTextRenderMode(sec.getString("text-render-mode", "LINE_ENTITIES")));
        data.setBillboard(parseBillboard(sec.getString("billboard", "fixed")));
        data.setAlignment(parseAlignment(sec.getString("alignment", "center")));
        data.setShadow(sec.getBoolean("shadow", true));
        data.setSeeThrough(sec.getBoolean("see-through", false));
        data.setBackground(sec.getBoolean("background", false));
        data.setBackgroundColor(normalizeColor(sec.getString("background-color", "#000000")));
        data.setBackgroundOpacity(readBackgroundOpacity(id, sec));
        data.setLineSpacing(readDouble(id, sec, "line-spacing", 0.28D, 0.05D, 2.0D));
        data.setScale(readDouble(id, sec, "scale", 1.0D, 0.1D, 10.0D));
        data.setViewRange(readDouble(id, sec, "view-range",
                readDefaultViewRange(), 1.0D, 64.0D));
        data.setEnabled(sec.getBoolean("enabled", true));
        data.setRefreshEnabled(sec.getBoolean("refresh.enabled",
                plugin.getConfig().getBoolean("refresh.default-enabled", false)));
        data.setRefreshIntervalMinutes(readInt(id, sec, "refresh.interval-minutes",
                readDefaultRefreshIntervalMinutes(), readMinimumRefreshIntervalMinutes(), 1_440));
        data.setDeprecatedRefreshIntervalKey(!sec.contains("refresh.interval-minutes")
                && sec.contains("refresh.interval-seconds"));
        data.setRefreshOnlyWhenViewed(sec.getBoolean("refresh.only-when-viewed",
                plugin.getConfig().getBoolean("refresh.default-only-when-viewed", true)));
        data.setRefreshViewerRange(readDouble(id, sec, "refresh.viewer-range",
                readDefaultRefreshViewerRange(), 1.0D, 256.0D));
        data.setItemMaterial(parseMaterial(sec.getString("item", "DIAMOND"), Material.DIAMOND));
        data.setBlockMaterial(parseMaterial(sec.getString("block", "DIAMOND_BLOCK"), Material.DIAMOND_BLOCK));
        data.setInteractionEnabled(sec.getBoolean("interaction.enabled", false));
        data.setInteractionWidth(readDouble(id, sec, "interaction.width",
                plugin.getConfig().getDouble("interaction.default-width", 1.0D),
                0.1D, readInteractionMaxWidth()));
        data.setInteractionHeight(readDouble(id, sec, "interaction.height",
                plugin.getConfig().getDouble("interaction.default-height", 1.0D),
                0.1D, readInteractionMaxHeight()));
        data.setInteractionCooldownSeconds(readInt(id, sec, "interaction.cooldown-seconds",
                plugin.getConfig().getInt("interaction.default-cooldown-seconds", 1),
                0, readInteractionMaxCooldownSeconds()));
        data.setInteractionLeftActions(sec.getStringList("interaction.actions.left"));
        data.setInteractionRightActions(sec.getStringList("interaction.actions.right"));
        data.setAutoRotationEnabled(sec.getBoolean("rotation.auto.enabled", false));
        double maxRotationSpeed = readMaxRotationSpeed();
        data.setAutoYawPerSecond(readDouble(id, sec, "rotation.auto.yaw-per-second",
                0.0D, -maxRotationSpeed, maxRotationSpeed));
        data.setAutoPitchPerSecond(readDouble(id, sec, "rotation.auto.pitch-per-second",
                0.0D, -maxRotationSpeed, maxRotationSpeed));

        List<String> lines = sec.getStringList("lines");
        data.setLines(lines);
        return data;
    }

    /** Saves all displays back to disk. */
    public void save(Map<String, DisplayData> displays) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, DisplayData> entry : displays.entrySet()) {
            DisplayData d = entry.getValue();
            String base = "displays." + entry.getKey() + ".";
            cfg.set(base + "type", d.getType().name());
            cfg.set(base + "world", d.getWorld());
            cfg.set(base + "x", d.getX());
            cfg.set(base + "y", d.getY());
            cfg.set(base + "z", d.getZ());
            cfg.set(base + "yaw", (double) d.getYaw());
            cfg.set(base + "pitch", (double) d.getPitch());
            cfg.set(base + "text-render-mode", d.getTextRenderMode().name());
            cfg.set(base + "billboard", d.getBillboard().name().toLowerCase(Locale.ROOT));
            cfg.set(base + "alignment", d.getAlignment().name().toLowerCase(Locale.ROOT));
            cfg.set(base + "shadow", d.isShadow());
            cfg.set(base + "see-through", d.isSeeThrough());
            cfg.set(base + "background", d.isBackground());
            cfg.set(base + "background-color", d.getBackgroundColor());
            cfg.set(base + "background-opacity", d.getBackgroundOpacity());
            cfg.set(base + "line-spacing", d.getLineSpacing());
            cfg.set(base + "scale", d.getScale());
            cfg.set(base + "view-range", d.getViewRange());
            cfg.set(base + "enabled", d.isEnabled());
            cfg.set(base + "refresh.enabled", d.isRefreshEnabled());
            cfg.set(base + "refresh.interval-minutes", d.getRefreshIntervalMinutes());
            cfg.set(base + "refresh.only-when-viewed", d.isRefreshOnlyWhenViewed());
            cfg.set(base + "refresh.viewer-range", d.getRefreshViewerRange());
            cfg.set(base + "item", d.getItemMaterial().name());
            cfg.set(base + "block", d.getBlockMaterial().name());
            cfg.set(base + "interaction.enabled", d.isInteractionEnabled());
            cfg.set(base + "interaction.width", d.getInteractionWidth());
            cfg.set(base + "interaction.height", d.getInteractionHeight());
            cfg.set(base + "interaction.cooldown-seconds", d.getInteractionCooldownSeconds());
            cfg.set(base + "interaction.actions.left", d.getInteractionLeftActions());
            cfg.set(base + "interaction.actions.right", d.getInteractionRightActions());
            cfg.set(base + "rotation.auto.enabled", d.isAutoRotationEnabled());
            cfg.set(base + "rotation.auto.yaw-per-second", d.getAutoYawPerSecond());
            cfg.set(base + "rotation.auto.pitch-per-second", d.getAutoPitchPerSecond());
            cfg.set(base + "lines", d.getLines());
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create data folder for CozyDisplays.");
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save displays.yml", ex);
        }
    }

    private void ensureFile() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create data folder for CozyDisplays.");
            }
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.options().setHeader(List.of(
                    "CozyDisplays storage.",
                    "Each entry under 'displays:' is one fixed text_display hologram.",
                    "Lines support legacy & color codes (e.g. &6 &f &l &r)."));
            cfg.createSection("displays");
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create displays.yml", ex);
        }
    }

    private Display.Billboard parseBillboard(String raw) {
        try {
            return Display.Billboard.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Display.Billboard.FIXED;
        }
    }

    private DisplayType parseType(String raw) {
        try {
            return DisplayType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DisplayType.TEXT;
        }
    }

    private TextRenderMode parseTextRenderMode(String raw) {
        try {
            return TextRenderMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TextRenderMode.LINE_ENTITIES;
        }
    }

    private String normalizeColor(String raw) {
        if (raw != null && raw.matches("#[0-9A-Fa-f]{6}")) {
            return raw.toUpperCase(Locale.ROOT);
        }
        return "#000000";
    }

    private Material parseMaterial(String raw, Material fallback) {
        Material material = Material.matchMaterial(raw == null ? "" : raw.trim());
        return material == null ? fallback : material;
    }

    private TextDisplay.TextAlignment parseAlignment(String raw) {
        try {
            return TextDisplay.TextAlignment.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private double readDefaultViewRange() {
        double value = plugin.getConfig().getDouble("default-view-range", 12.0D);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 12.0D;
        }
        return Math.max(1.0D, Math.min(64.0D, value));
    }

    private int readDefaultRefreshIntervalMinutes() {
        int minimum = readMinimumRefreshIntervalMinutes();
        int value = readConfigMinutes("refresh.default-interval-minutes",
                "refresh.default-interval-seconds", 5);
        return Math.max(minimum, Math.min(1_440, value));
    }

    private int readDefaultBackgroundOpacity() {
        int value = plugin.getConfig().getInt("text-defaults.background-opacity", 90);
        return Math.max(0, Math.min(100, value));
    }

    private int readBackgroundOpacity(String id, ConfigurationSection sec) {
        if (sec.contains("background-opacity")) {
            return readInt(id, sec, "background-opacity", readDefaultBackgroundOpacity(), 0, 100);
        }
        if (sec.contains("backgroundOpacity")) {
            return readInt(id, sec, "backgroundOpacity", readDefaultBackgroundOpacity(), 0, 100);
        }
        return readDefaultBackgroundOpacity();
    }

    private int readMinimumRefreshIntervalMinutes() {
        int value = readConfigMinutes("refresh.minimum-interval-minutes",
                "refresh.minimum-interval-seconds", 1);
        return Math.max(1, Math.min(1_440, value));
    }

    private int readConfigMinutes(String minutesPath, String secondsPath, int fallback) {
        if (plugin.getConfig().contains(minutesPath)) {
            return plugin.getConfig().getInt(minutesPath, fallback);
        }
        if (plugin.getConfig().contains(secondsPath)) {
            return secondsToMinutes(plugin.getConfig().getInt(secondsPath, fallback * 60));
        }
        return fallback;
    }

    private double readDefaultRefreshViewerRange() {
        double value = plugin.getConfig().getDouble("refresh.default-viewer-range", 32.0D);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 32.0D;
        }
        return Math.max(1.0D, Math.min(256.0D, value));
    }

    private double readInteractionMaxWidth() {
        double value = plugin.getConfig().getDouble("interaction.max-width", 5.0D);
        return Math.max(0.1D, Math.min(64.0D, value));
    }

    private double readInteractionMaxHeight() {
        double value = plugin.getConfig().getDouble("interaction.max-height", 5.0D);
        return Math.max(0.1D, Math.min(64.0D, value));
    }

    private int readInteractionMaxCooldownSeconds() {
        int value = plugin.getConfig().getInt("interaction.max-cooldown-seconds", 60);
        return Math.max(0, Math.min(86_400, value));
    }

    private double readMaxRotationSpeed() {
        double value = plugin.getConfig().getDouble("rotation.max-degrees-per-second", 180.0D);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 180.0D;
        }
        return Math.max(0.0D, Math.min(10_000.0D, value));
    }

    private int readInt(String id, ConfigurationSection sec, String path,
                        int fallback, int min, int max) {
        int value;
        if (path.endsWith("interval-minutes") && !sec.contains(path)
                && sec.contains("refresh.interval-seconds")) {
            value = secondsToMinutes(sec.getInt("refresh.interval-seconds", fallback * 60));
        } else {
            value = sec.getInt(path, fallback);
        }
        if (value < min) {
            plugin.getLogger().warning("Display '" + id + "' has " + path
                    + " below " + min + "; using " + min + ".");
            return min;
        }
        if (value > max) {
            plugin.getLogger().warning("Display '" + id + "' has " + path
                    + " above " + max + "; using " + max + ".");
            return max;
        }
        return value;
    }

    private int secondsToMinutes(int seconds) {
        return Math.max(1, (int) Math.ceil(seconds / 60.0D));
    }

    private double readDouble(String id, ConfigurationSection sec, String path,
                              double fallback, double min, double max) {
        double value = sec.getDouble(path, fallback);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            plugin.getLogger().warning("Display '" + id + "' has invalid " + path
                    + "; using " + fallback + ".");
            return fallback;
        }
        if (value < min) {
            plugin.getLogger().warning("Display '" + id + "' has " + path
                    + " below " + min + "; using " + min + ".");
            return min;
        }
        if (value > max) {
            plugin.getLogger().warning("Display '" + id + "' has " + path
                    + " above " + max + "; using " + max + ".");
            return max;
        }
        return value;
    }
}
