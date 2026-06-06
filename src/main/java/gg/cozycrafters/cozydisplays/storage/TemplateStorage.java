package gg.cozycrafters.cozydisplays.storage;

import gg.cozycrafters.cozydisplays.display.DisplayData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Lightweight persistence for reusable display templates. Templates intentionally
 * omit id and location; applying one only changes safe text/visual settings.
 */
public final class TemplateStorage {

    private final Plugin plugin;
    private final File file;

    public TemplateStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "templates.yml");
    }

    public List<String> listTemplateIds() {
        YamlConfiguration cfg = loadFile();
        ConfigurationSection root = cfg.getConfigurationSection("templates");
        if (root == null) {
            return List.of();
        }
        return new ArrayList<>(root.getKeys(false));
    }

    public boolean exists(String templateId) {
        YamlConfiguration cfg = loadFile();
        return cfg.isConfigurationSection("templates." + templateId);
    }

    public void saveTemplate(String templateId, DisplayData source) {
        YamlConfiguration cfg = loadFile();
        String base = "templates." + templateId + ".";
        cfg.set(base + "billboard", source.getBillboard().name().toLowerCase(Locale.ROOT));
        cfg.set(base + "alignment", source.getAlignment().name().toLowerCase(Locale.ROOT));
        cfg.set(base + "shadow", source.isShadow());
        cfg.set(base + "see-through", source.isSeeThrough());
        cfg.set(base + "background", source.isBackground());
        cfg.set(base + "line-spacing", source.getLineSpacing());
        cfg.set(base + "scale", source.getScale());
        cfg.set(base + "view-range", source.getViewRange());
        cfg.set(base + "refresh.enabled", source.isRefreshEnabled());
        cfg.set(base + "refresh.interval-seconds", source.getRefreshIntervalSeconds());
        cfg.set(base + "refresh.only-when-viewed", source.isRefreshOnlyWhenViewed());
        cfg.set(base + "refresh.viewer-range", source.getRefreshViewerRange());
        cfg.set(base + "lines", source.getLines());
        saveFile(cfg);
    }

    public boolean applyTemplate(String templateId, DisplayData target) {
        YamlConfiguration cfg = loadFile();
        ConfigurationSection sec = cfg.getConfigurationSection("templates." + templateId);
        if (sec == null) {
            return false;
        }

        target.setBillboard(parseBillboard(sec.getString("billboard", "fixed")));
        target.setAlignment(parseAlignment(sec.getString("alignment", "center")));
        target.setShadow(sec.getBoolean("shadow", true));
        target.setSeeThrough(sec.getBoolean("see-through", false));
        target.setBackground(sec.getBoolean("background", false));
        target.setLineSpacing(readDouble(sec, "line-spacing", 0.28D, 0.05D, 2.0D));
        target.setScale(readDouble(sec, "scale", 1.0D, 0.1D, 10.0D));
        target.setViewRange(readDouble(sec, "view-range", 12.0D, 1.0D, 64.0D));
        target.setRefreshEnabled(sec.getBoolean("refresh.enabled", true));
        target.setRefreshIntervalSeconds(readInt(sec, "refresh.interval-seconds", 10, 1, 86_400));
        target.setRefreshOnlyWhenViewed(sec.getBoolean("refresh.only-when-viewed", true));
        target.setRefreshViewerRange(readDouble(sec, "refresh.viewer-range", 32.0D, 1.0D, 256.0D));
        List<String> lines = sec.getStringList("lines");
        if (!lines.isEmpty()) {
            target.setLines(lines);
        }
        return true;
    }

    private YamlConfiguration loadFile() {
        ensureFile();
        return YamlConfiguration.loadConfiguration(file);
    }

    private void ensureFile() {
        if (file.exists()) {
            return;
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create data folder for CozyDisplays.");
            }
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.options().setHeader(List.of(
                    "CozyDisplays templates.",
                    "Templates store reusable text and safe visual settings only.",
                    "Applying a template never changes a display id or location."));
            cfg.createSection("templates");
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create templates.yml", ex);
        }
    }

    private void saveFile(YamlConfiguration cfg) {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create data folder for CozyDisplays.");
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save templates.yml", ex);
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

    private int readInt(ConfigurationSection sec, String path, int fallback, int min, int max) {
        int value = sec.getInt(path, fallback);
        return Math.max(min, Math.min(max, value));
    }

    private double readDouble(ConfigurationSection sec, String path,
                              double fallback, double min, double max) {
        double value = sec.getDouble(path, fallback);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
