package gg.cozycrafters.cozydisplays.display;

import gg.cozycrafters.cozydisplays.placeholder.PlaceholderService;
import gg.cozycrafters.cozydisplays.storage.DisplayStorage;
import gg.cozycrafters.cozydisplays.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
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
    private final NamespacedKey roleKey;
    private final NamespacedKey interactionKey;
    private static final String ROLE_VISUAL = "visual";
    private static final String ROLE_INTERACTION = "interaction";
    private static final String OWNED_SCOREBOARD_TAG = "cozydisplays";
    private static final String VISUAL_SCOREBOARD_TAG = "cozydisplays:visual";
    private static final String INTERACTION_SCOREBOARD_TAG = "cozydisplays:interaction";

    private final Map<String, DisplayData> displays = new LinkedHashMap<>();
    /** Display id -> ordered line entity UUIDs (line index == list index). */
    private final Map<String, List<UUID>> spawned = new LinkedHashMap<>();
    /** Display id -> spawned interaction hitbox UUID. */
    private final Map<String, UUID> interactions = new LinkedHashMap<>();
    /** Display id -> last placeholder-resolved line strings (for change detection). */
    private final Map<String, List<String>> lastResolved = new LinkedHashMap<>();
    /** Display id -> last automatic placeholder refresh timestamp in millis. */
    private final Map<String, Long> lastRefreshRun = new LinkedHashMap<>();

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
        this.roleKey = new NamespacedKey(plugin, "role");
        this.interactionKey = new NamespacedKey(plugin, "interaction");
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
        total += interactions.size();
        return total;
    }

    public int getFullySpawnedDisplayCount() {
        int total = 0;
        for (DisplayData data : displays.values()) {
            if (isFullySpawned(data)) {
                total++;
            }
        }
        return total;
    }

    public boolean isFullySpawned(DisplayData data) {
        if (data == null || !data.isEnabled()) {
            return false;
        }
        List<UUID> ids = spawned.get(data.getId());
        int expected = expectedVisualEntityCount(data);
        if (ids == null || ids.size() != expected) {
            return false;
        }
        for (UUID uuid : ids) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof Display) || !entity.isValid()) {
                return false;
            }
        }
        return true;
    }

    public boolean isInteractionSpawned(DisplayData data) {
        if (data == null || !data.isEnabled() || !data.isInteractionEnabled()) {
            return false;
        }
        UUID uuid = interactions.get(data.getId());
        Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        return entity instanceof Interaction && entity.isValid();
    }

    public WorldEntityAudit auditWorldEntities() {
        Map<String, Integer> visualCounts = new LinkedHashMap<>();
        Map<String, Integer> interactionCounts = new LinkedHashMap<>();
        int visualEntities = 0;
        int interactionEntities = 0;
        int orphanEntities = 0;

        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isCozyDisplayEntity(entity)) {
                    continue;
                }
                String id = getDisplayId(entity);
                if (id == null || !displays.containsKey(id)) {
                    orphanEntities++;
                }
                if (isCozyDisplayInteractionEntity(entity)) {
                    interactionEntities++;
                    if (id != null) {
                        interactionCounts.merge(id, 1, Integer::sum);
                    }
                } else if (isCozyDisplayVisualEntity(entity)) {
                    visualEntities++;
                    if (id != null) {
                        visualCounts.merge(id, 1, Integer::sum);
                    }
                }
            }
        }

        int duplicateVisualEntities = 0;
        for (Map.Entry<String, Integer> entry : visualCounts.entrySet()) {
            DisplayData data = displays.get(entry.getKey());
            int expected = expectedVisualEntityCount(data);
            if (entry.getValue() > expected) {
                duplicateVisualEntities += entry.getValue() - expected;
            }
        }

        int duplicateInteractionEntities = 0;
        for (Map.Entry<String, Integer> entry : interactionCounts.entrySet()) {
            DisplayData data = displays.get(entry.getKey());
            int expected = data != null && data.isEnabled() && data.isInteractionEnabled() ? 1 : 0;
            if (entry.getValue() > expected) {
                duplicateInteractionEntities += entry.getValue() - expected;
            }
        }

        return new WorldEntityAudit(visualEntities, interactionEntities,
                duplicateVisualEntities, duplicateInteractionEntities, orphanEntities);
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
        interactions.clear();
        lastResolved.clear();
        lastRefreshRun.clear();
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
        interactions.clear();
        lastResolved.clear();
        lastRefreshRun.clear();
    }

    /* ----------------------------- spawn -------------------------------- */

    /** Respawns a single display: removes its entities then spawns fresh. */
    public void respawn(DisplayData data) {
        despawn(data.getId());
        spawn(data);
    }

    public void spawn(DisplayData data) {
        removeOwnedVisualEntities(data.getId());
        removeOwnedInteractionEntities(data.getId());
        spawned.remove(data.getId());
        interactions.remove(data.getId());

        Location base = data.toLocation();
        if (base == null) {
            plugin.getLogger().warning("Cannot spawn display '" + data.getId()
                    + "': world '" + data.getWorld() + "' is not loaded.");
            return;
        }

        if (!data.isEnabled()) {
            spawned.put(data.getId(), new ArrayList<>());
            lastResolved.put(data.getId(), data.getType() == DisplayType.TEXT
                    ? resolveLines(data) : List.of());
            lastRefreshRun.put(data.getId(), System.currentTimeMillis());
            return;
        }

        World world = base.getWorld();
        List<UUID> ids = new ArrayList<>();
        switch (data.getType()) {
            case TEXT -> {
                if (data.getTextRenderMode() == TextRenderMode.SINGLE_ENTITY) {
                    TextDisplay entity = world.spawn(base, TextDisplay.class,
                            e -> applyTextSettings(e, data, String.join("\n", data.getLines())));
                    ids.add(entity.getUniqueId());
                    logViewRange(data, 0, entity);
                } else {
                    for (int i = 0; i < data.getLines().size(); i++) {
                        double offsetY = base.getY() - (i * data.getLineSpacing());
                        Location lineLoc = new Location(world, base.getX(), offsetY, base.getZ(),
                            data.getYaw(), data.getPitch());

                        String rawLine = data.getLines().get(i);
                        TextDisplay entity = world.spawn(lineLoc, TextDisplay.class,
                                e -> applyTextSettings(e, data, rawLine));
                        ids.add(entity.getUniqueId());
                        logViewRange(data, i, entity);
                    }
                }
            }
            case ITEM -> {
                ItemDisplay entity = world.spawn(rotated(base, data), ItemDisplay.class,
                        e -> applyItemSettings(e, data));
                ids.add(entity.getUniqueId());
                logViewRange(data, 0, entity);
            }
            case BLOCK -> {
                BlockDisplay entity = world.spawn(rotated(base, data), BlockDisplay.class,
                        e -> applyBlockSettings(e, data));
                ids.add(entity.getUniqueId());
                logViewRange(data, 0, entity);
            }
        }
        spawned.put(data.getId(), ids);
        if (data.isInteractionEnabled()) {
            removeOwnedInteractionEntities(data.getId());
            Interaction interaction = world.spawn(base, Interaction.class,
                    e -> applyInteractionSettings(e, data));
            interactions.put(data.getId(), interaction.getUniqueId());
        }
        lastResolved.put(data.getId(), data.getType() == DisplayType.TEXT ? resolveLines(data) : List.of());
        lastRefreshRun.put(data.getId(), System.currentTimeMillis());
    }

    /** Placeholder-resolved (pre-color) strings for each saved line. */
    private List<String> resolveLines(DisplayData data) {
        List<String> out = new ArrayList<>(data.getLines().size());
        for (String raw : data.getLines()) {
            out.add(placeholders.applyPlaceholders(raw));
        }
        return out;
    }

    private void applyTextSettings(TextDisplay entity, DisplayData data, String rawLine) {
        entity.text(renderLine(rawLine));
        entity.setBillboard(data.getBillboard());
        entity.setAlignment(data.getAlignment());
        entity.setShadowed(data.isShadow());
        entity.setSeeThrough(data.isSeeThrough());
        applyCommonDisplaySettings(entity, data);

        if (data.isBackground()) {
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(backgroundColor(data));
        } else {
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        }
        tagOwned(entity, data.getId(), false);
    }

    private void applyItemSettings(ItemDisplay entity, DisplayData data) {
        entity.setItemStack(new ItemStack(data.getItemMaterial()));
        applyCommonDisplaySettings(entity, data);
        tagOwned(entity, data.getId(), false);
    }

    private void applyBlockSettings(BlockDisplay entity, DisplayData data) {
        Material material = data.getBlockMaterial();
        BlockData blockData = material.isBlock()
                ? Bukkit.createBlockData(material)
                : Bukkit.createBlockData(Material.DIAMOND_BLOCK);
        entity.setBlock(blockData);
        applyCommonDisplaySettings(entity, data);
        tagOwned(entity, data.getId(), false);
    }

    private void applyCommonDisplaySettings(Display entity, DisplayData data) {
        entity.setBillboard(data.getBillboard());
        entity.setViewRange((float) (data.getViewRange() / BLOCKS_PER_VIEW_RANGE_UNIT));

        float scale = (float) data.getScale();
        entity.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F)));

        entity.setPersistent(true);
    }

    private Location rotated(Location base, DisplayData data) {
        return new Location(base.getWorld(), base.getX(), base.getY(), base.getZ(),
                data.getYaw(), data.getPitch());
    }

    private void applyInteractionSettings(Interaction entity, DisplayData data) {
        entity.setInteractionWidth((float) data.getInteractionWidth());
        entity.setInteractionHeight((float) data.getInteractionHeight());
        entity.setResponsive(true);
        entity.setPersistent(true);
        tagOwned(entity, data.getId(), true);
    }

    private void tagOwned(Entity entity, String id, boolean interaction) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(ownedKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(idKey, PersistentDataType.STRING, id);
        pdc.set(roleKey, PersistentDataType.STRING, interaction ? ROLE_INTERACTION : ROLE_VISUAL);
        if (interaction) {
            pdc.set(interactionKey, PersistentDataType.BYTE, (byte) 1);
        }
        entity.addScoreboardTag(OWNED_SCOREBOARD_TAG);
        entity.addScoreboardTag("cozydisplays:id:" + id);
        entity.addScoreboardTag(interaction ? INTERACTION_SCOREBOARD_TAG : VISUAL_SCOREBOARD_TAG);
    }

    private void logViewRange(DisplayData data, int index, Display entity) {
        if (viewRangeDebug) {
            plugin.getLogger().info("[view-range] display='" + data.getId()
                    + "' part=" + index + " uuid=" + entity.getUniqueId()
                    + " blocks=" + data.getViewRange()
                    + " appliedMultiplier=" + entity.getViewRange());
        }
    }

    /** raw config line -> placeholders -> legacy color -> Component. */
    private net.kyori.adventure.text.Component renderLine(String rawLine) {
        return TextUtil.legacy(placeholders.applyPlaceholders(rawLine));
    }

    private Color backgroundColor(DisplayData data) {
        String hex = data.getBackgroundColor();
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            hex = "#000000";
        }
        int red = Integer.parseInt(hex.substring(1, 3), 16);
        int green = Integer.parseInt(hex.substring(3, 5), 16);
        int blue = Integer.parseInt(hex.substring(5, 7), 16);
        int alpha = (int) Math.round(Math.max(0, Math.min(100, data.getBackgroundOpacity())) * 255.0D / 100.0D);
        return Color.fromARGB(alpha, red, green, blue);
    }

    /* --------------------------- refresh -------------------------------- */

    /** Re-resolves placeholders for every spawned display. */
    public void refreshAll() {
        List<DisplayData> all = new ArrayList<>(displays.values());
        int changed = 0;
        int checked = 0;
        for (DisplayData data : all) {
            if (!data.isRefreshEnabled()) {
                continue;
            }
            checked++;
            if (refresh(data, false)) {
                changed++;
            }
        }
        if (debug) {
            plugin.getLogger().info("[debug] placeholder refresh tick: "
                    + checked + " refresh-enabled display(s) checked, " + changed + " updated.");
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
        return refresh(data, false);
    }

    public boolean forceRefresh(DisplayData data) {
        return refresh(data, true);
    }

    private boolean refresh(DisplayData data, boolean force) {
        if (!data.isEnabled()) {
            return false;
        }
        if (data.getType() != DisplayType.TEXT) {
            return false;
        }
        if (!force) {
            if (!data.isRefreshEnabled()) {
                return false;
            }
            if (!isRefreshDue(data)) {
                return false;
            }
            if (data.isRefreshOnlyWhenViewed() && !hasNearbyViewer(data)) {
                lastRefreshRun.put(data.getId(), System.currentTimeMillis());
                return false;
            }
        }
        List<UUID> ids = spawned.get(data.getId());

        boolean structurallyStale = ids == null || ids.size() != expectedVisualEntityCount(data);
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
            lastRefreshRun.put(data.getId(), System.currentTimeMillis());
            return true;
        }
        lastRefreshRun.put(data.getId(), System.currentTimeMillis());
        return false;
    }

    private boolean isRefreshDue(DisplayData data) {
        long now = System.currentTimeMillis();
        long last = lastRefreshRun.getOrDefault(data.getId(), 0L);
        long intervalMillis = Math.max(1L, data.getRefreshIntervalMinutes()) * 60_000L;
        return now - last >= intervalMillis;
    }

    private boolean hasNearbyViewer(DisplayData data) {
        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        double rangeSquared = data.getRefreshViewerRange() * data.getRefreshViewerRange();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(loc.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(loc) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    public void tickAutoRotations(double seconds, double maxDegreesPerSecond) {
        for (DisplayData data : new ArrayList<>(displays.values())) {
            if (!data.isEnabled() || !data.isAutoRotationEnabled()) {
                continue;
            }
            double yawSpeed = clamp(data.getAutoYawPerSecond(),
                    -maxDegreesPerSecond, maxDegreesPerSecond);
            double pitchSpeed = clamp(data.getAutoPitchPerSecond(),
                    -maxDegreesPerSecond, maxDegreesPerSecond);
            if (yawSpeed == 0.0D && pitchSpeed == 0.0D) {
                continue;
            }
            data.setRawLocation(data.getWorld(), data.getX(), data.getY(), data.getZ(),
                    (float) (data.getYaw() + yawSpeed * seconds),
                    (float) (data.getPitch() + pitchSpeed * seconds));
            respawn(data);
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /* ---------------------------- despawn ------------------------------- */

    /** Removes only the entities owned by the given display id. */
    public void despawn(String id) {
        removeOwnedVisualEntities(id);
        removeOwnedInteractionEntities(id);
        spawned.remove(id);
        interactions.remove(id);
        lastResolved.remove(id);
        lastRefreshRun.remove(id);
    }

    private void removeOwnedVisualEntities(String id) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (id.equals(getDisplayId(entity)) && isCozyDisplayVisualEntity(entity)) {
                    entity.remove();
                }
            }
        }
    }

    private void removeOwnedInteractionEntities(String id) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (id.equals(getDisplayId(entity)) && isCozyDisplayInteractionEntity(entity)) {
                    entity.remove();
                }
            }
        }
    }

    /** Removes every plugin-owned display or interaction entity in loaded worlds. */
    public void cleanupOwnedEntities() {
        for (World world : plugin.getServer().getWorlds()) {
            removeOwnedIn(world.getEntities());
        }
    }

    private void removeOwnedIn(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (isCozyDisplayEntity(entity)) {
                entity.remove();
            }
        }
    }

    public boolean isCozyDisplayEntity(Entity entity) {
        return isOwned(entity.getPersistentDataContainer());
    }

    public boolean isCozyDisplayVisualEntity(Entity entity) {
        if (!(entity instanceof Display)) {
            return false;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!isOwned(pdc)) {
            return false;
        }
        String role = pdc.get(roleKey, PersistentDataType.STRING);
        return ROLE_VISUAL.equals(role) || (!ROLE_INTERACTION.equals(role) && !isInteraction(pdc));
    }

    public boolean isCozyDisplayInteractionEntity(Entity entity) {
        if (!(entity instanceof Interaction)) {
            return false;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!isOwned(pdc)) {
            return false;
        }
        String role = pdc.get(roleKey, PersistentDataType.STRING);
        return ROLE_INTERACTION.equals(role) || isInteraction(pdc);
    }

    public String getDisplayId(Entity entity) {
        if (!isCozyDisplayEntity(entity)) {
            return null;
        }
        return entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    private boolean isOwned(PersistentDataContainer pdc) {
        Byte flag = pdc.get(ownedKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public String getOwnedInteractionDisplayId(Entity entity) {
        if (!(entity instanceof Interaction)) {
            return null;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!isOwned(pdc) || !isInteraction(pdc)) {
            return null;
        }
        return pdc.get(idKey, PersistentDataType.STRING);
    }

    private boolean isInteraction(PersistentDataContainer pdc) {
        Byte flag = pdc.get(interactionKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private int expectedVisualEntityCount(DisplayData data) {
        if (data == null || !data.isEnabled()) {
            return 0;
        }
        if (data.getType() != DisplayType.TEXT) {
            return 1;
        }
        if (data.getTextRenderMode() == TextRenderMode.SINGLE_ENTITY) {
            return 1;
        }
        return data.getLines().size();
    }

    public record WorldEntityAudit(int visualEntities,
                                   int interactionEntities,
                                   int duplicateVisualEntities,
                                   int duplicateInteractionEntities,
                                   int orphanEntities) {
    }
}
