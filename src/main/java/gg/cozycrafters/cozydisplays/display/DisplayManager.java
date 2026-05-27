package gg.cozycrafters.cozydisplays.display;

import gg.cozycrafters.cozydisplays.placeholder.PlaceholderService;
import gg.cozycrafters.cozydisplays.storage.DisplayStorage;
import gg.cozycrafters.cozydisplays.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the in-memory display registry and all spawn/despawn logic. Every
 * plugin-created entity is tagged via {@link PersistentDataContainer} so the
 * plugin only ever removes entities it owns. Spawned line entities are tracked
 * per display id so placeholder refreshes can update text in place.
 */
public final class DisplayManager {

    private final Plugin plugin;
    private final DisplayStorage storage;
    private final PlaceholderService placeholders;

    private final NamespacedKey ownedKey;
    private final NamespacedKey idKey;

    private final Map<String, DisplayData> displays = new LinkedHashMap<>();
    /** Display id -> ordered line entity UUIDs (line index == list index). */
    private final Map<String, List<UUID>> spawned = new LinkedHashMap<>();
    /** Display id -> last placeholder-resolved line strings (for change detection). */
    private final Map<String, List<String>> lastResolved = new LinkedHashMap<>();

    private boolean debug;
    private boolean viewRangeDebug;

    /**
     * Minecraft display entities treat view range as a multiplier where the
     * effective render distance is roughly {@code viewRange * 64} blocks
     * (1.0 ≈ 64 blocks). CozyDisplays stores/exposes view range in BLOCKS, so
     * the saved value is divided by this factor before being applied.
     */
    private static final double BLOCKS_PER_VIEW_RANGE_UNIT = 64.0D;

    public DisplayManager(Plugin plugin, DisplayStorage storage, PlaceholderService placeholders) {
        this.plugin = plugin;
        this.storage = storage;
        this.placeholders = placeholders;
        this.ownedKey = new NamespacedKey(plugin, "owned");
        this.idKey = new NamespacedKey(plugin, "id");
    }

    /* ----------------------------- registry ----------------------------- */

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setViewRangeDebug(boolean viewRangeDebug) {
        this.viewRangeDebug = viewRangeDebug;
    }

    public int getTrackedDisplayCount() {
        return spawned.size();
    }

    public int getSpawnedEntityCount() {
        int total = 0;
        for (List<UUID> ids : spawned.values()) {
            total += ids.size();
        }
        return total;
    }

    public int getTotalLineCount() {
        int total = 0;
        for (DisplayData data : displays.values()) {
            total += data.getLines().size();
        }
        return total;
    }

    public int getEnabledCount() {
        int total = 0;
        for (DisplayData data : displays.values()) {
            if (data.isEnabled()) {
                total++;
            }
        }
        return total;
    }

    public int getHiddenCount() {
        return displays.size() - getEnabledCount();
    }

    public Map<String, DisplayData> getDisplays() {
        return displays;
    }

    public DisplayData get(String id) {
        return displays.get(id);
    }

    public boolean exists(String id) {
        return displays.containsKey(id);
    }

    public void put(DisplayData data) {
        displays.put(data.getId(), data);
    }

    public void remove(String id) {
        displays.remove(id);
    }

    public void saveAll() {
        storage.save(displays);
    }

    /* --------------------------- lifecycle ------------------------------ */

    /** Loads storage, cleans stray owned entities, and spawns everything. */
    public void loadAndSpawnAll() {
        displays.clear();
        displays.putAll(storage.load());
        cleanupOwnedEntities();
        spawned.clear();
        lastResolved.clear();
        spawnAll();
    }

    /** Despawns owned entities and respawns from current in-memory state. */
    public void reloadFromStorage() {
        despawnAll();
        displays.clear();
        displays.putAll(storage.load());
        spawnAll();
    }

    public void spawnAll() {
        for (DisplayData data : displays.values()) {
            spawn(data);
        }
    }

    public void despawnAll() {
        cleanupOwnedEntities();
        spawned.clear();
        lastResolved.clear();
    }

    /* ----------------------------- spawn -------------------------------- */

    /** Respawns a single display: removes its entities then spawns fresh. */
    public void respawn(DisplayData data) {
        despawn(data.getId());
        spawn(data);
    }

    public void spawn(DisplayData data) {
        Location base = data.toLocation();
        if (base == null) {
            plugin.getLogger().warning("Cannot spawn display '" + data.getId()
                    + "': world '" + data.getWorld() + "' is not loaded.");
            return;
        }

        if (!data.isEnabled()) {
            spawned.put(data.getId(), new ArrayList<>());
            lastResolved.put(data.getId(), resolveLines(data));
            return;
        }

        World world = base.getWorld();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < data.getLines().size(); i++) {
            double offsetY = base.getY() - (i * data.getLineSpacing());
            Location lineLoc = new Location(world, base.getX(), offsetY, base.getZ(),
                    data.getYaw(), data.getPitch());

            String rawLine = data.getLines().get(i);
            TextDisplay entity = world.spawn(lineLoc, TextDisplay.class,
                    e -> applySettings(e, data, rawLine));
            ids.add(entity.getUniqueId());
            if (viewRangeDebug) {
                plugin.getLogger().info("[view-range] display='" + data.getId()
                        + "' line=" + i + " uuid=" + entity.getUniqueId()
                        + " blocks=" + data.getViewRange()
                        + " appliedMultiplier=" + entity.getViewRange());
            }
        }
        spawned.put(data.getId(), ids);
        lastResolved.put(data.getId(), resolveLines(data));
    }

    /** Placeholder-resolved (pre-color) strings for each saved line. */
    private List<String> resolveLines(DisplayData data) {
        List<String> out = new ArrayList<>(data.getLines().size());
        for (String raw : data.getLines()) {
            out.add(placeholders.applyPlaceholders(raw));
        }
        return out;
    }

    private void applySettings(TextDisplay entity, DisplayData data, String rawLine) {
        entity.text(renderLine(rawLine));
        entity.setBillboard(data.getBillboard());
        entity.setAlignment(data.getAlignment());
        entity.setShadowed(data.isShadow());
        entity.setSeeThrough(data.isSeeThrough());
        entity.setViewRange((float) (data.getViewRange() / BLOCKS_PER_VIEW_RANGE_UNIT));

        if (data.isBackground()) {
            entity.setDefaultBackground(true);
        } else {
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        }

        float scale = (float) data.getScale();
        entity.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F)));

        entity.setPersistent(true);

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(ownedKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(idKey, PersistentDataType.STRING, data.getId());
    }

    /** raw config line -> placeholders -> legacy color -> Component. */
    private net.kyori.adventure.text.Component renderLine(String rawLine) {
        return TextUtil.legacy(placeholders.applyPlaceholders(rawLine));
    }

    /* --------------------------- refresh -------------------------------- */

    /** Re-resolves placeholders for every spawned display. */
    public void refreshAll() {
        List<DisplayData> all = new ArrayList<>(displays.values());
        int changed = 0;
        for (DisplayData data : all) {
            if (refresh(data)) {
                changed++;
            }
        }
        if (debug) {
            plugin.getLogger().info("[debug] placeholder refresh tick: "
                    + all.size() + " display(s) checked, " + changed + " updated.");
        }
    }

    /**
     * Re-resolves placeholders for one display and respawns it only if the
     * resolved text actually changed (or tracked entities are missing/invalid).
     * Display-entity metadata is only networked to clients on (re)spawn, so an
     * in-place {@code text()} setter does not visually update existing viewers
     * on this Paper version — respawn-on-change guarantees the update while
     * avoiding flicker when nothing changed.
     *
     * @return true if the display was respawned/updated this tick.
     */
    public boolean refresh(DisplayData data) {
        if (!data.isEnabled()) {
            return false;
        }
        List<UUID> ids = spawned.get(data.getId());

        boolean structurallyStale = ids == null || ids.size() != data.getLines().size();
        if (!structurallyStale) {
            for (UUID uuid : ids) {
                Entity entity = Bukkit.getEntity(uuid);
                if (!(entity instanceof TextDisplay) || !entity.isValid()) {
                    structurallyStale = true;
                    break;
                }
            }
        }

        List<String> resolved = resolveLines(data);
        boolean textChanged = !resolved.equals(lastResolved.get(data.getId()));

        if (structurallyStale || textChanged) {
            respawn(data);
            return true;
        }
        return false;
    }

    /* ---------------------------- despawn ------------------------------- */

    /** Removes only the entities owned by the given display id. */
    public void despawn(String id) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof TextDisplay)) {
                    continue;
                }
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                if (!isOwned(pdc)) {
                    continue;
                }
                String ownerId = pdc.get(idKey, PersistentDataType.STRING);
                if (id.equals(ownerId)) {
                    entity.remove();
                }
            }
        }
        spawned.remove(id);
        lastResolved.remove(id);
    }

    /** Removes every plugin-owned text display in loaded worlds. */
    public void cleanupOwnedEntities() {
        for (World world : plugin.getServer().getWorlds()) {
            removeOwnedIn(world.getEntities());
        }
    }

    private void removeOwnedIn(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof TextDisplay
                    && isOwned(entity.getPersistentDataContainer())) {
                entity.remove();
            }
        }
    }

    private boolean isOwned(PersistentDataContainer pdc) {
        Byte flag = pdc.get(ownedKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }
}
