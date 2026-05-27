package gg.cozycrafters.cozydisplays.storage;

import gg.cozycrafters.cozydisplays.display.DisplayData;
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
                if (data.getLines().isEmpty()) {
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
        data.setRawLocation(
                sec.getString("world", "world"),
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw", 0.0D),
                (float) sec.getDouble("pitch", 0.0D));

        data.setBillboard(parseBillboard(sec.getString("billboard", "fixed")));
        data.setAlignment(parseAlignment(sec.getString("alignment", "center")));
        data.setShadow(sec.getBoolean("shadow", true));
        data.setSeeThrough(sec.getBoolean("see-through", false));
        data.setBackground(sec.getBoolean("background", false));
        data.setLineSpacing(readDouble(id, sec, "line-spacing", 0.28D, 0.05D, 2.0D));
        data.setScale(readDouble(id, sec, "scale", 1.0D, 0.1D, 10.0D));
        data.setViewRange(readDouble(id, sec, "view-range",
                readDefaultViewRange(), 1.0D, 64.0D));
        data.setEnabled(sec.getBoolean("enabled", true));

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
            cfg.set(base + "world", d.getWorld());
            cfg.set(base + "x", d.getX());
            cfg.set(base + "y", d.getY());
            cfg.set(base + "z", d.getZ());
            cfg.set(base + "yaw", (double) d.getYaw());
            cfg.set(base + "pitch", (double) d.getPitch());
            cfg.set(base + "billboard", d.getBillboard().name().toLowerCase(Locale.ROOT));
            cfg.set(base + "alignment", d.getAlignment().name().toLowerCase(Locale.ROOT));
            cfg.set(base + "shadow", d.isShadow());
            cfg.set(base + "see-through", d.isSeeThrough());
            cfg.set(base + "background", d.isBackground());
            cfg.set(base + "line-spacing", d.getLineSpacing());
            cfg.set(base + "scale", d.getScale());
            cfg.set(base + "view-range", d.getViewRange());
            cfg.set(base + "enabled", d.isEnabled());
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
