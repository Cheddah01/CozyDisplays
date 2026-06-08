package gg.cozycrafters.cozydisplays.display;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory representation of a single saved display. The source of truth for
 * persistence is {@code displays.yml}; this object mirrors one entry.
 */
public final class DisplayData {

    private final String id;

    private DisplayType type = DisplayType.TEXT;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    private TextRenderMode textRenderMode = TextRenderMode.LINE_ENTITIES;
    private Display.Billboard billboard = Display.Billboard.FIXED;
    private TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;
    private boolean shadow = true;
    private boolean seeThrough = false;
    private boolean background = false;
    private String backgroundColor = "#000000";
    private int backgroundOpacity = 90;
    private double lineSpacing = 0.28D;
    private double scale = 1.0D;
    private double viewRange = 12.0D;
    private boolean enabled = true;
    private boolean refreshEnabled = false;
    private int refreshIntervalMinutes = 5;
    private boolean refreshOnlyWhenViewed = true;
    private double refreshViewerRange = 32.0D;
    private boolean deprecatedRefreshIntervalKey = false;
    private Material itemMaterial = Material.DIAMOND;
    private Material blockMaterial = Material.DIAMOND_BLOCK;
    private boolean interactionEnabled = false;
    private double interactionWidth = 1.0D;
    private double interactionHeight = 1.0D;
    private int interactionCooldownSeconds = 1;
    private boolean autoRotationEnabled = false;
    private double autoYawPerSecond = 0.0D;
    private double autoPitchPerSecond = 0.0D;
    private final List<String> interactionLeftActions = new ArrayList<>();
    private final List<String> interactionRightActions = new ArrayList<>();

    private final List<String> lines = new ArrayList<>();

    public DisplayData(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public DisplayType getType() {
        return type;
    }

    public void setType(DisplayType type) {
        this.type = type == null ? DisplayType.TEXT : type;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setLocation(Location loc) {
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    public void setRawLocation(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** Resolves the base location, or {@code null} if the world is not loaded. */
    public Location toLocation() {
        World w = org.bukkit.Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, x, y, z, yaw, pitch);
    }

    public TextRenderMode getTextRenderMode() {
        return textRenderMode;
    }

    public void setTextRenderMode(TextRenderMode textRenderMode) {
        this.textRenderMode = textRenderMode == null ? TextRenderMode.LINE_ENTITIES : textRenderMode;
    }

    public Display.Billboard getBillboard() {
        return billboard;
    }

    public void setBillboard(Display.Billboard billboard) {
        this.billboard = billboard;
    }

    public TextDisplay.TextAlignment getAlignment() {
        return alignment;
    }

    public void setAlignment(TextDisplay.TextAlignment alignment) {
        this.alignment = alignment;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public boolean isSeeThrough() {
        return seeThrough;
    }

    public void setSeeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
    }

    public boolean isBackground() {
        return background;
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor == null ? "#000000" : backgroundColor;
    }

    public int getBackgroundOpacity() {
        return backgroundOpacity;
    }

    public void setBackgroundOpacity(int backgroundOpacity) {
        this.backgroundOpacity = Math.max(0, Math.min(100, backgroundOpacity));
    }

    public double getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(double lineSpacing) {
        this.lineSpacing = lineSpacing;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getViewRange() {
        return viewRange;
    }

    public void setViewRange(double viewRange) {
        this.viewRange = viewRange;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public int getRefreshIntervalMinutes() {
        return refreshIntervalMinutes;
    }

    public void setRefreshIntervalMinutes(int refreshIntervalMinutes) {
        this.refreshIntervalMinutes = refreshIntervalMinutes;
    }

    public boolean isRefreshOnlyWhenViewed() {
        return refreshOnlyWhenViewed;
    }

    public void setRefreshOnlyWhenViewed(boolean refreshOnlyWhenViewed) {
        this.refreshOnlyWhenViewed = refreshOnlyWhenViewed;
    }

    public double getRefreshViewerRange() {
        return refreshViewerRange;
    }

    public void setRefreshViewerRange(double refreshViewerRange) {
        this.refreshViewerRange = refreshViewerRange;
    }

    public boolean hasDeprecatedRefreshIntervalKey() {
        return deprecatedRefreshIntervalKey;
    }

    public void setDeprecatedRefreshIntervalKey(boolean deprecatedRefreshIntervalKey) {
        this.deprecatedRefreshIntervalKey = deprecatedRefreshIntervalKey;
    }

    public Material getItemMaterial() {
        return itemMaterial;
    }

    public void setItemMaterial(Material itemMaterial) {
        this.itemMaterial = itemMaterial == null ? Material.DIAMOND : itemMaterial;
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public void setBlockMaterial(Material blockMaterial) {
        this.blockMaterial = blockMaterial == null ? Material.DIAMOND_BLOCK : blockMaterial;
    }

    public boolean isInteractionEnabled() {
        return interactionEnabled;
    }

    public void setInteractionEnabled(boolean interactionEnabled) {
        this.interactionEnabled = interactionEnabled;
    }

    public double getInteractionWidth() {
        return interactionWidth;
    }

    public void setInteractionWidth(double interactionWidth) {
        this.interactionWidth = interactionWidth;
    }

    public double getInteractionHeight() {
        return interactionHeight;
    }

    public void setInteractionHeight(double interactionHeight) {
        this.interactionHeight = interactionHeight;
    }

    public int getInteractionCooldownSeconds() {
        return interactionCooldownSeconds;
    }

    public void setInteractionCooldownSeconds(int interactionCooldownSeconds) {
        this.interactionCooldownSeconds = interactionCooldownSeconds;
    }

    public boolean isAutoRotationEnabled() {
        return autoRotationEnabled;
    }

    public void setAutoRotationEnabled(boolean autoRotationEnabled) {
        this.autoRotationEnabled = autoRotationEnabled;
    }

    public double getAutoYawPerSecond() {
        return autoYawPerSecond;
    }

    public void setAutoYawPerSecond(double autoYawPerSecond) {
        this.autoYawPerSecond = autoYawPerSecond;
    }

    public double getAutoPitchPerSecond() {
        return autoPitchPerSecond;
    }

    public void setAutoPitchPerSecond(double autoPitchPerSecond) {
        this.autoPitchPerSecond = autoPitchPerSecond;
    }

    public List<String> getInteractionLeftActions() {
        return interactionLeftActions;
    }

    public List<String> getInteractionRightActions() {
        return interactionRightActions;
    }

    public void setInteractionLeftActions(List<String> actions) {
        interactionLeftActions.clear();
        if (actions != null) {
            interactionLeftActions.addAll(actions);
        }
    }

    public void setInteractionRightActions(List<String> actions) {
        interactionRightActions.clear();
        if (actions != null) {
            interactionRightActions.addAll(actions);
        }
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
    }

    public void addLine(String line) {
        lines.add(line);
    }

    public DisplayData copyAs(String newId) {
        DisplayData copy = new DisplayData(newId);
        copy.setType(type);
        copy.setRawLocation(world, x, y, z, yaw, pitch);
        copy.setTextRenderMode(textRenderMode);
        copy.setBillboard(billboard);
        copy.setAlignment(alignment);
        copy.setShadow(shadow);
        copy.setSeeThrough(seeThrough);
        copy.setBackground(background);
        copy.setBackgroundColor(backgroundColor);
        copy.setBackgroundOpacity(backgroundOpacity);
        copy.setLineSpacing(lineSpacing);
        copy.setScale(scale);
        copy.setViewRange(viewRange);
        copy.setEnabled(enabled);
        copy.setRefreshEnabled(refreshEnabled);
        copy.setRefreshIntervalMinutes(refreshIntervalMinutes);
        copy.setRefreshOnlyWhenViewed(refreshOnlyWhenViewed);
        copy.setRefreshViewerRange(refreshViewerRange);
        copy.setDeprecatedRefreshIntervalKey(deprecatedRefreshIntervalKey);
        copy.setItemMaterial(itemMaterial);
        copy.setBlockMaterial(blockMaterial);
        copy.setInteractionEnabled(interactionEnabled);
        copy.setInteractionWidth(interactionWidth);
        copy.setInteractionHeight(interactionHeight);
        copy.setInteractionCooldownSeconds(interactionCooldownSeconds);
        copy.setAutoRotationEnabled(autoRotationEnabled);
        copy.setAutoYawPerSecond(autoYawPerSecond);
        copy.setAutoPitchPerSecond(autoPitchPerSecond);
        copy.setInteractionLeftActions(interactionLeftActions);
        copy.setInteractionRightActions(interactionRightActions);
        copy.setLines(lines);
        return copy;
    }
}
