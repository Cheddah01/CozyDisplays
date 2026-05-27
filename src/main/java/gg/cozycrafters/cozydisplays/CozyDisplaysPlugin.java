package gg.cozycrafters.cozydisplays;

import gg.cozycrafters.cozydisplays.command.DisplayCommand;
import gg.cozycrafters.cozydisplays.display.DisplayManager;
import gg.cozycrafters.cozydisplays.placeholder.PlaceholderService;
import gg.cozycrafters.cozydisplays.storage.DisplayStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Plugin bootstrap: owns the storage + manager + placeholder instances, wires
 * the command, manages the placeholder refresh task, and handles clean
 * startup/shutdown of plugin-owned display entities.
 */
public final class CozyDisplaysPlugin extends JavaPlugin {

    private DisplayManager manager;
    private PlaceholderService placeholders;
    private BukkitTask refreshTask;
    private int refreshSeconds;
    private double wallOffset;
    private double defaultViewRange;
    private int entityWarningThreshold;
    private double defaultNudgeAmount;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.placeholders = new PlaceholderService(this);
        placeholders.detect();

        DisplayStorage storage = new DisplayStorage(this);
        this.manager = new DisplayManager(this, storage, placeholders);

        applyRefreshConfig();
        manager.loadAndSpawnAll();
        startPlaceholderRefreshTask();

        PluginCommand command = getCommand("display");
        if (command == null) {
            getLogger().severe("Command 'display' missing from plugin.yml; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        DisplayCommand executor = new DisplayCommand(this, manager);
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
        if (manager != null) {
            manager.despawnAll();
        }
        getLogger().info("CozyDisplays disabled (saved data preserved).");
    }

    /* --------------------------- reload --------------------------------- */

    /** Reloads config + storage, re-detects PlaceholderAPI, respawns displays. */
    public void reloadAll() {
        reloadConfig();
        placeholders.detect();
        applyRefreshConfig();
        manager.reloadFromStorage();
        restartPlaceholderRefreshTask();
    }

    /* --------------------- placeholder refresh task --------------------- */

    private void applyRefreshConfig() {
        this.refreshSeconds = readInt("placeholder-refresh-seconds", 30, 0, 86_400);
        this.wallOffset = readDouble("wall-offset", 0.03D, 0.001D, 1.0D);
        this.defaultViewRange = readDouble("default-view-range", 12.0D, 1.0D, 64.0D);
        this.entityWarningThreshold = readInt("display-entity-warning-threshold", 50, 1, 10_000);
        this.defaultNudgeAmount = readDouble("default-nudge-amount", 0.05D, 0.001D, 5.0D);
        manager.setDebug(getConfig().getBoolean("debug-placeholder-refresh", false));
        manager.setViewRangeDebug(getConfig().getBoolean("debug-view-range", false));
    }

    public void startPlaceholderRefreshTask() {
        stopPlaceholderRefreshTask();
        if (!placeholders.isEnabled()) {
            getLogger().info("Placeholder refresh task not started: PlaceholderAPI not enabled.");
            return;
        }
        if (refreshSeconds <= 0) {
            getLogger().info("Placeholder refresh task not started: "
                    + "placeholder-refresh-seconds is " + refreshSeconds + " (disabled).");
            return;
        }
        long ticks = refreshSeconds * 20L;
        this.refreshTask = getServer().getScheduler()
                .runTaskTimer(this, manager::refreshAll, ticks, ticks);
        getLogger().info("Placeholder refresh task started: interval=" + refreshSeconds
                + "s, PlaceholderAPI=enabled, tracked displays="
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

    /** Outward offset from a wall face used by /display snapwall. */
    public double getWallOffset() {
        return wallOffset;
    }

    /** Default nudge step (blocks) when no amount is given; min 0.05 fallback. */
    public double getDefaultNudgeAmount() {
        return defaultNudgeAmount;
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
        if (refreshSeconds <= 0) {
            return "PlaceholderAPI refresh: disabled (placeholder-refresh-seconds <= 0).";
        }
        return "PlaceholderAPI refresh: enabled every " + refreshSeconds + "s.";
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
