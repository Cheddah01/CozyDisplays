package gg.cozycrafters.cozydisplays.display;

import org.bukkit.Location;
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

    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    private Display.Billboard billboard = Display.Billboard.FIXED;
    private TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;
    private boolean shadow = true;
    private boolean seeThrough = false;
    private boolean background = false;
    private double lineSpacing = 0.28D;
    private double scale = 1.0D;
    private double viewRange = 12.0D;
    private boolean enabled = true;

    private final List<String> lines = new ArrayList<>();

    public DisplayData(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
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
}
