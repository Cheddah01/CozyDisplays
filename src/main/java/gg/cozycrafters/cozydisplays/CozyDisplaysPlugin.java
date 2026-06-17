package gg.cozycrafters.cozydisplays;

import gg.cozycrafters.cozydisplays.command.DisplayCommand;
import gg.cozycrafters.cozydisplays.display.DisplayManager;
import gg.cozycrafters.cozydisplays.display.TextRenderMode;
import gg.cozycrafters.cozydisplays.gui.DisplayEditor;
import gg.cozycrafters.cozydisplays.interaction.DisplayInteractionListener;
import gg.cozycrafters.cozydisplays.placeholder.PlaceholderService;
import gg.cozycrafters.cozydisplays.storage.DisplayStorage;
import gg.cozycrafters.cozydisplays.storage.TemplateStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Plugin bootstrap: owns the storage + manager + placeholder instances, wires
 * the command, manages the placeholder refresh task, and handles clean
 * startup/shutdown of plugin-owned display entities.
 */
public final class CozyDisplaysPlugin extends JavaPlugin {

    private static final int DEFAULT_TEXT_BACKGROUND_OPACITY = 25;
    private static final int LEGACY_TEXT_BACKGROUND_OPACITY = 90;

    private DisplayManager manager;
    private DisplayEditor editor;
    private PlaceholderService placeholders;
    private BukkitTask refreshTask;
    private BukkitTask autoRotationTask;
    private int refreshCheckSeconds;
    private int refreshMinimumIntervalMinutes;
    private double wallOffset;
    private double defaultViewRange;
    private int entityWarningThreshold;
    private double defaultNudgeAmount;
    private double nearbyDefaultRadius;
    private double nearbyMaxRadius;
    private double editorNudgeStep;
    private double editorScaleStep;
    private double editorViewRangeStep;
    private int editorOpacityStep;
    private double editorLineSpacingStep;
    private double interactionDefaultWidth;
    private double interactionDefaultHeight;
    private int interactionDefaultCooldownSeconds;
    private double interactionMaxWidth;
    private double interactionMaxHeight;
    private int interactionMaxCooldownSeconds;
    private double editorRotationStepYaw;
    private double editorRotationStepPitch;
    private int rotationAutoUpdateTicks;
    private double rotationMaxDegreesPerSecond;
    private TextRenderMode defaultTextRenderMode;
    private boolean defaultTextBackgroundEnabled;
    private String defaultTextBackgroundColor;
    private int defaultTextBackgroundOpacity;
    private TextDisplay.TextAlignment defaultTextAlignment;
    private double defaultTextLineSpacing;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfigDefaults();

        this.placeholders = new PlaceholderService(this);
        placeholders.detect();

        DisplayStorage storage = new DisplayStorage(this);
        this.manager = new DisplayManager(this, storage, placeholders);
        TemplateStorage templates = new TemplateStorage(this);
        this.editor = new DisplayEditor(this, manager);

        applyRefreshConfig();
        manager.loadAndSpawnAll();
        startPlaceholderRefreshTask();
        startAutoRotationTask();

        PluginCommand command = getCommand("display");
        if (command == null) {
            getLogger().severe("Command 'display' missing from plugin.yml; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getPluginManager().registerEvents(
                new DisplayInteractionListener(manager, placeholders), this);

        DisplayCommand executor = new DisplayCommand(this, manager, editor, templates);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getLogger().info("CozyDisplays enabled with "
                + manager.getDisplays().size() + " display(s).");
        if (placeholders.isEnabled()) {
            getLogger().info("PlaceholderAPI support enabled. " + refreshStatus());
        } else {
            getLogger().info("PlaceholderAPI not installed; placeholders shown as raw text.");
        }
        int spawned = manager.getSpawnedEntityCount();
        if (spawned > getEntityWarningThreshold()) {
            getLogger().warning("Warning: " + spawned + " TextDisplay entities are spawned."
                    + " Dense displays may cause client FPS lag near spawn.");
        }
    }

    @Override
    public void onDisable() {
        stopPlaceholderRefreshTask();
        stopAutoRotationTask();
        if (editor != null) {
            editor.closeAll();
        }
        if (manager != null) {
            manager.despawnAll();
        }
        getLogger().info("CozyDisplays disabled (saved data preserved).");
    }

    /* --------------------------- reload --------------------------------- */

    /** Reloads config + storage, re-detects PlaceholderAPI, respawns displays. */
    public void reloadAll() {
        stopPlaceholderRefreshTask();
        stopAutoRotationTask();
        if (editor != null) {
            editor.closeAll();
        }
        if (manager != null) {
            manager.despawnAll();
        }
        reloadConfig();
        migrateConfigDefaults();
        placeholders.detect();
        applyRefreshConfig();
        manager.loadAndSpawnAll();
        startPlaceholderRefreshTask();
        startAutoRotationTask();
    }

    /* --------------------- placeholder refresh task --------------------- */

    private void applyRefreshConfig() {
        this.refreshCheckSeconds = getConfig().contains("placeholder-refresh-seconds")
                ? readInt("placeholder-refresh-seconds", 60, 0, 86_400) : 60;
        this.refreshMinimumIntervalMinutes = readRefreshMinutes(
                "refresh.minimum-interval-minutes", "refresh.minimum-interval-seconds", 1);
        this.wallOffset = readDouble("wall-offset", 0.03D, 0.001D, 1.0D);
        this.defaultViewRange = readDouble("default-view-range", 12.0D, 1.0D, 64.0D);
        this.entityWarningThreshold = readInt("display-entity-warning-threshold", 50, 1, 10_000);
        this.defaultNudgeAmount = readDouble("default-nudge-amount", 0.05D, 0.001D, 5.0D);
        this.nearbyDefaultRadius = readDouble("nearby-default-radius", 25.0D, 1.0D, 256.0D);
        this.nearbyMaxRadius = readDouble("nearby-max-radius", 100.0D, 1.0D, 1_000.0D);
        if (nearbyDefaultRadius > nearbyMaxRadius) {
            getLogger().warning("nearby-default-radius is above nearby-max-radius; using max.");
            nearbyDefaultRadius = nearbyMaxRadius;
        }
        this.editorNudgeStep = readDouble("editor.nudge-step", 0.1D, 0.001D, 5.0D);
        this.editorScaleStep = readDouble("editor.scale-step", 0.1D, 0.01D, 5.0D);
        this.editorViewRangeStep = readDouble("editor.view-range-step", 4.0D, 1.0D, 64.0D);
        this.editorOpacityStep = readInt("editor.opacity-step", 5, 1, 100);
        this.editorLineSpacingStep = readDouble("editor.line-spacing-step", 0.05D, 0.01D, 1.0D);
        this.interactionMaxWidth = readDouble("interaction.max-width", 5.0D, 0.1D, 64.0D);
        this.interactionMaxHeight = readDouble("interaction.max-height", 5.0D, 0.1D, 64.0D);
        this.interactionMaxCooldownSeconds = readInt("interaction.max-cooldown-seconds", 60, 0, 86_400);
        this.interactionDefaultWidth = readDouble("interaction.default-width", 1.0D, 0.1D, interactionMaxWidth);
        this.interactionDefaultHeight = readDouble("interaction.default-height", 1.0D, 0.1D, interactionMaxHeight);
        this.interactionDefaultCooldownSeconds = readInt("interaction.default-cooldown-seconds",
                1, 0, interactionMaxCooldownSeconds);
        this.editorRotationStepYaw = readDouble("editor.rotation-step-yaw", 15.0D, 0.1D, 360.0D);
        this.editorRotationStepPitch = readDouble("editor.rotation-step-pitch", 15.0D, 0.1D, 360.0D);
        this.rotationAutoUpdateTicks = readInt("rotation.auto-update-ticks", 2, 1, 200);
        this.rotationMaxDegreesPerSecond = readDouble("rotation.max-degrees-per-second",
                180.0D, 0.0D, 10_000.0D);
        this.defaultTextRenderMode = readTextRenderMode("text-defaults.render-mode", TextRenderMode.LINE_ENTITIES);
        this.defaultTextBackgroundEnabled = getConfig().getBoolean("text-defaults.background-enabled", true);
        this.defaultTextBackgroundColor = readHexColor("text-defaults.background-color", "#000000");
        this.defaultTextBackgroundOpacity = readInt("text-defaults.background-opacity",
                DEFAULT_TEXT_BACKGROUND_OPACITY, 0, 100);
        this.defaultTextAlignment = readTextAlignment("text-defaults.alignment", TextDisplay.TextAlignment.CENTER);
        this.defaultTextLineSpacing = readDouble("text-defaults.line-spacing", 0.25D, 0.05D, 2.0D);
        manager.setDebug(getConfig().getBoolean("debug-placeholder-refresh", false));
        manager.setViewRangeDebug(getConfig().getBoolean("debug-view-range", false));
    }

    public void startPlaceholderRefreshTask() {
        stopPlaceholderRefreshTask();
        if (!placeholders.isEnabled()) {
            getLogger().info("Placeholder refresh task not started: PlaceholderAPI not enabled.");
            return;
        }
        if (refreshCheckSeconds <= 0) {
            getLogger().info("Placeholder refresh task not started: "
                    + "placeholder-refresh-seconds is " + refreshCheckSeconds + " (disabled).");
            return;
        }
        long ticks = Math.max(20L, refreshCheckSeconds * 20L);
        this.refreshTask = getServer().getScheduler()
                .runTaskTimer(this, manager::refreshAll, ticks, ticks);
        getLogger().info("Placeholder refresh task started: check interval=" + refreshCheckSeconds
                + "s, default display interval="
                + readRefreshMinutes("refresh.default-interval-minutes",
                        "refresh.default-interval-seconds", 5)
                + "m, PlaceholderAPI=enabled, tracked displays="
                + manager.getTrackedDisplayCount() + ".");
    }

    public void stopPlaceholderRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void restartPlaceholderRefreshTask() {
        startPlaceholderRefreshTask();
    }

    public void startAutoRotationTask() {
        stopAutoRotationTask();
        this.autoRotationTask = getServer().getScheduler().runTaskTimer(this, () -> {
            double seconds = rotationAutoUpdateTicks / 20.0D;
            manager.tickAutoRotations(seconds, rotationMaxDegreesPerSecond);
        }, rotationAutoUpdateTicks, rotationAutoUpdateTicks);
    }

    public void stopAutoRotationTask() {
        if (autoRotationTask != null) {
            autoRotationTask.cancel();
            autoRotationTask = null;
        }
    }

    public void restartAutoRotationTask() {
        startAutoRotationTask();
    }

    /** Outward offset from a wall face used by /display snapwall. */
    public double getWallOffset() {
        return wallOffset;
    }

    /** Default nudge step (blocks) when no amount is given; min 0.05 fallback. */
    public double getDefaultNudgeAmount() {
        return defaultNudgeAmount;
    }

    public double getNearbyDefaultRadius() {
        return nearbyDefaultRadius;
    }

    public double getNearbyMaxRadius() {
        return nearbyMaxRadius;
    }

    public double getEditorNudgeStep() {
        return editorNudgeStep;
    }

    public double getEditorScaleStep() {
        return editorScaleStep;
    }

    public double getEditorViewRangeStep() {
        return editorViewRangeStep;
    }

    public int getEditorOpacityStep() {
        return editorOpacityStep;
    }

    public double getEditorLineSpacingStep() {
        return editorLineSpacingStep;
    }

    public double getInteractionDefaultWidth() {
        return interactionDefaultWidth;
    }

    public double getInteractionDefaultHeight() {
        return interactionDefaultHeight;
    }

    public int getInteractionDefaultCooldownSeconds() {
        return interactionDefaultCooldownSeconds;
    }

    public double getInteractionMaxWidth() {
        return interactionMaxWidth;
    }

    public double getInteractionMaxHeight() {
        return interactionMaxHeight;
    }

    public int getInteractionMaxCooldownSeconds() {
        return interactionMaxCooldownSeconds;
    }

    public double getEditorRotationStepYaw() {
        return editorRotationStepYaw;
    }

    public double getEditorRotationStepPitch() {
        return editorRotationStepPitch;
    }

    public double getRotationMaxDegreesPerSecond() {
        return rotationMaxDegreesPerSecond;
    }

    public TextRenderMode getDefaultTextRenderMode() {
        return defaultTextRenderMode;
    }

    public boolean isDefaultTextBackgroundEnabled() {
        return defaultTextBackgroundEnabled;
    }

    public String getDefaultTextBackgroundColor() {
        return defaultTextBackgroundColor;
    }

    public int getDefaultTextBackgroundOpacity() {
        return defaultTextBackgroundOpacity;
    }

    public TextDisplay.TextAlignment getDefaultTextAlignment() {
        return defaultTextAlignment;
    }

    public double getDefaultTextLineSpacing() {
        return defaultTextLineSpacing;
    }

    /** Default TextDisplay view range; falls back to 12.0 if invalid. */
    public double getDefaultViewRange() {
        return defaultViewRange;
    }

    public boolean isPlaceholdersEnabled() {
        return placeholders != null && placeholders.isEnabled();
    }

    /** Spawned-entity count above which a render-lag warning is shown. */
    public int getEntityWarningThreshold() {
        return entityWarningThreshold;
    }

    /** Human-readable refresh status for command/log feedback. */
    public String refreshStatus() {
        if (!placeholders.isEnabled()) {
            return "PlaceholderAPI not installed; placeholders are shown as raw text.";
        }
        if (refreshCheckSeconds <= 0) {
            return "PlaceholderAPI refresh: disabled (placeholder-refresh-seconds <= 0).";
        }
        return "PlaceholderAPI refresh: enabled; scheduler checks every "
                + refreshCheckSeconds + "s with per-display minute intervals.";
    }

    public int getRefreshMinimumIntervalMinutes() {
        return refreshMinimumIntervalMinutes;
    }

    private void migrateConfigDefaults() {
        boolean changed = false;
        if (!getConfig().contains("text-defaults.background-enabled", true)) {
            getConfig().set("text-defaults.background-enabled", true);
            changed = true;
        }
        if (!getConfig().contains("text-defaults.background-color", true)) {
            getConfig().set("text-defaults.background-color", "#000000");
            changed = true;
        }
        if (!getConfig().contains("text-defaults.background-opacity", true)) {
            getConfig().set("text-defaults.background-opacity", DEFAULT_TEXT_BACKGROUND_OPACITY);
            changed = true;
        } else if (isLegacyGeneratedBackgroundDefault()) {
            getConfig().set("text-defaults.background-opacity", DEFAULT_TEXT_BACKGROUND_OPACITY);
            getLogger().info("Updated legacy text-defaults.background-opacity from "
                    + LEGACY_TEXT_BACKGROUND_OPACITY + "% to " + DEFAULT_TEXT_BACKGROUND_OPACITY
                    + "% for safer TextDisplay reload defaults.");
            changed = true;
        }
        if (changed) {
            saveConfig();
        }
    }

    private boolean isLegacyGeneratedBackgroundDefault() {
        return getConfig().getInt("text-defaults.background-opacity", DEFAULT_TEXT_BACKGROUND_OPACITY)
                == LEGACY_TEXT_BACKGROUND_OPACITY
                && getConfig().getBoolean("text-defaults.background-enabled", true)
                && "#000000".equalsIgnoreCase(getConfig().getString("text-defaults.background-color", "#000000"));
    }

    private int readRefreshMinutes(String minutesPath, String secondsPath, int fallback) {
        int value;
        if (getConfig().contains(minutesPath)) {
            value = getConfig().getInt(minutesPath, fallback);
        } else if (getConfig().contains(secondsPath)) {
            value = Math.max(1, (int) Math.ceil(getConfig().getInt(secondsPath, fallback * 60) / 60.0D));
        } else {
            value = fallback;
        }
        if (value < 1) {
            getLogger().warning(minutesPath + " is below 1; using 1.");
            return 1;
        }
        if (value > 1_440) {
            getLogger().warning(minutesPath + " is above 1440; using 1440.");
            return 1_440;
        }
        return value;
    }

    private TextRenderMode readTextRenderMode(String path, TextRenderMode fallback) {
        String raw = getConfig().getString(path, fallback.name());
        try {
            return TextRenderMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getLogger().warning(path + " is invalid; using " + fallback + ".");
            return fallback;
        }
    }

    private TextDisplay.TextAlignment readTextAlignment(String path, TextDisplay.TextAlignment fallback) {
        String raw = getConfig().getString(path, fallback.name());
        try {
            return TextDisplay.TextAlignment.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getLogger().warning(path + " is invalid; using " + fallback + ".");
            return fallback;
        }
    }

    private String readHexColor(String path, String fallback) {
        String raw = getConfig().getString(path, fallback);
        if (raw != null && raw.matches("#[0-9A-Fa-f]{6}")) {
            return raw.toUpperCase(Locale.ROOT);
        }
        getLogger().warning(path + " is invalid; using " + fallback + ".");
        return fallback;
    }

    private int readInt(String path, int fallback, int min, int max) {
        int value = getConfig().getInt(path, fallback);
        if (value < min) {
            getLogger().warning(path + " is below " + min + "; using " + min + ".");
            return min;
        }
        if (value > max) {
            getLogger().warning(path + " is above " + max + "; using " + max + ".");
            return max;
        }
        return value;
    }

    private double readDouble(String path, double fallback, double min, double max) {
        double value = getConfig().getDouble(path, fallback);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            getLogger().warning(path + " is not a finite number; using " + fallback + ".");
            return fallback;
        }
        if (value < min) {
            getLogger().warning(path + " is below " + min + "; using " + min + ".");
            return min;
        }
        if (value > max) {
            getLogger().warning(path + " is above " + max + "; using " + max + ".");
            return max;
        }
        return value;
    }
}
