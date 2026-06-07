package gg.cozycrafters.cozydisplays.command;

import gg.cozycrafters.cozydisplays.CozyDisplaysPlugin;
import gg.cozycrafters.cozydisplays.display.DisplayData;
import gg.cozycrafters.cozydisplays.display.DisplayManager;
import gg.cozycrafters.cozydisplays.display.DisplayManager.WorldEntityAudit;
import gg.cozycrafters.cozydisplays.display.DisplayType;
import gg.cozycrafters.cozydisplays.gui.DisplayEditor;
import gg.cozycrafters.cozydisplays.storage.TemplateStorage;
import gg.cozycrafters.cozydisplays.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses {@code /display} subcommands, validates input/permission, and
 * delegates the actual spawn/storage work to {@link DisplayManager}.
 */
public final class DisplayCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "cozydisplays.admin";
    private static final List<String> SUBS = List.of(
            "create", "addline", "setline", "removeline",
            "movehere", "nearby", "clone", "audit", "edit",
            "rotate", "rotateby", "face", "spin",
            "snapwall", "nudge",
            "up", "down", "left", "right", "forward", "back",
            "scale", "viewrange", "viewrangeall", "enabled",
            "hide", "show", "info", "stats",
            "setitem", "setblock", "interaction",
            "refresh", "template", "delete", "list", "reload");

    private static final List<String> SCALE_SUGGESTIONS = List.of(
            "0.5", "0.75", "1.0", "1.25", "1.5", "2.0");

    private static final List<String> VIEW_RANGE_SUGGESTIONS = List.of(
            "4.0", "8.0", "12.0", "16.0", "32.0", "64.0");

    private static final List<String> BOOL_SUGGESTIONS = List.of("true", "false");
    private static final List<String> REFRESH_ACTIONS = List.of(
            "enable", "disable", "interval", "status");
    private static final List<String> REFRESH_INTERVAL_SUGGESTIONS = List.of("1", "5", "10", "15", "30");

    private static final double MIN_SCALE = 0.1D;
    private static final double MAX_SCALE = 10.0D;
    private static final double MIN_VIEW_RANGE = 1.0D;
    private static final double MAX_VIEW_RANGE = 64.0D;

    private static final List<String> DIRECTIONS = List.of(
            "up", "down", "left", "right", "forward", "back");

    private static final List<String> AMOUNT_SUGGESTIONS = List.of(
            "0.01", "0.03", "0.05", "0.1", "0.25");

    private static final double MAX_NUDGE = 5.0D;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final CozyDisplaysPlugin plugin;
    private final DisplayManager manager;
    private final DisplayEditor editor;
    private final TemplateStorage templates;

    public DisplayCommand(CozyDisplaysPlugin plugin, DisplayManager manager,
                          DisplayEditor editor, TemplateStorage templates) {
        this.plugin = plugin;
        this.manager = manager;
        this.editor = editor;
        this.templates = templates;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(TextUtil.error("You do not have permission to use CozyDisplays."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "addline" -> handleAddLine(sender, args);
            case "setline" -> handleSetLine(sender, args);
            case "removeline" -> handleRemoveLine(sender, args);
            case "movehere" -> handleMoveHere(sender, args);
            case "nearby" -> handleNearby(sender, args);
            case "clone" -> handleClone(sender, args);
            case "audit" -> handleAudit(sender);
            case "edit" -> handleEdit(sender, args);
            case "rotate" -> handleRotate(sender, args);
            case "rotateby" -> handleRotateBy(sender, args);
            case "face" -> handleFace(sender, args);
            case "spin" -> handleSpin(sender, args);
            case "snapwall" -> handleSnapWall(sender, args);
            case "nudge" -> handleNudge(sender, args);
            case "up", "down", "left", "right", "forward", "back" ->
                    handleShortcutNudge(sender, sub, args);
            case "scale" -> handleScale(sender, args);
            case "viewrange" -> handleViewRange(sender, args);
            case "enabled" -> handleEnabled(sender, args);
            case "hide" -> handleHideShow(sender, args, false);
            case "show" -> handleHideShow(sender, args, true);
            case "viewrangeall" -> handleViewRangeAll(sender, args);
            case "info" -> handleInfo(sender, args);
            case "stats" -> handleStats(sender);
            case "setitem" -> handleSetItem(sender, args);
            case "setblock" -> handleSetBlock(sender, args);
            case "interaction" -> handleInteraction(sender, args);
            case "refresh" -> handleRefresh(sender, args);
            case "template" -> handleTemplate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage(TextUtil.error("Unknown subcommand '" + args[0] + "'."));
                sendUsage(sender);
            }
        }
        return true;
    }

    /* ----------------------------- create ------------------------------- */

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.error("Only a player can use /display create (location required)."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display create <id> <text...>"));
            sender.sendMessage(TextUtil.error("Usage: /display create item <id> <material>"));
            sender.sendMessage(TextUtil.error("Usage: /display create block <id> <material>"));
            return;
        }

        String maybeType = args[1].toLowerCase(Locale.ROOT);
        if (maybeType.equals("text")) {
            if (args.length < 4) {
                sender.sendMessage(TextUtil.error("Usage: /display create text <id> <text...>"));
                return;
            }
            createText(player, args[2], join(args, 3));
            return;
        }
        if (maybeType.equals("item")) {
            if (args.length < 4) {
                sender.sendMessage(TextUtil.error("Usage: /display create item <id> <material>"));
                return;
            }
            createItem(player, args[2], args[3]);
            return;
        }
        if (maybeType.equals("block")) {
            if (args.length < 4) {
                sender.sendMessage(TextUtil.error("Usage: /display create block <id> <material>"));
                return;
            }
            createBlock(player, args[2], args[3]);
            return;
        }

        createText(player, args[1], join(args, 2));
    }

    private void createText(Player player, String id, String text) {
        if (!isValidNewId(id)) {
            player.sendMessage(TextUtil.error("Display id must be 1-64 characters: letters, numbers, _ or -."));
            return;
        }
        if (manager.exists(id)) {
            player.sendMessage(TextUtil.error("A display with id '" + id + "' already exists."));
            return;
        }
        if (text.isBlank()) {
            player.sendMessage(TextUtil.error("Text cannot be empty."));
            return;
        }

        DisplayData data = new DisplayData(id);
        data.setType(DisplayType.TEXT);
        data.setLocation(player.getLocation());
        data.addLine(text);
        manager.put(data);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Created display '" + id + "' at your location."));
    }

    private void createItem(Player player, String id, String rawMaterial) {
        if (!isValidNewId(id)) {
            player.sendMessage(TextUtil.error("Display id must be 1-64 characters: letters, numbers, _ or -."));
            return;
        }
        if (manager.exists(id)) {
            player.sendMessage(TextUtil.error("A display with id '" + id + "' already exists."));
            return;
        }
        Material material = parseMaterial(player, rawMaterial);
        if (material == null) {
            return;
        }
        DisplayData data = new DisplayData(id);
        data.setType(DisplayType.ITEM);
        data.setLocation(player.getLocation());
        data.setItemMaterial(material);
        manager.put(data);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Created item display '" + id
                + "' using " + material.name() + "."));
    }

    private void createBlock(Player player, String id, String rawMaterial) {
        if (!isValidNewId(id)) {
            player.sendMessage(TextUtil.error("Display id must be 1-64 characters: letters, numbers, _ or -."));
            return;
        }
        if (manager.exists(id)) {
            player.sendMessage(TextUtil.error("A display with id '" + id + "' already exists."));
            return;
        }
        Material material = parseBlockMaterial(player, rawMaterial);
        if (material == null) {
            return;
        }
        DisplayData data = new DisplayData(id);
        data.setType(DisplayType.BLOCK);
        data.setLocation(player.getLocation());
        data.setBlockMaterial(material);
        manager.put(data);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Created block display '" + id
                + "' using " + material.name() + "."));
    }

    /* ---------------------------- addline ------------------------------- */

    private void handleAddLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display addline <id> <text...>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        if (!requireType(sender, data, DisplayType.TEXT)) {
            return;
        }
        String text = join(args, 2);
        if (text.isBlank()) {
            sender.sendMessage(TextUtil.error("Text cannot be empty."));
            return;
        }
        data.addLine(text);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Added line to '" + data.getId()
                + "' (now " + data.getLines().size() + " lines)."));
    }

    /* ---------------------------- setline ------------------------------- */

    private void handleSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextUtil.error("Usage: /display setline <id> <lineNumber> <text...>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        if (!requireType(sender, data, DisplayType.TEXT)) {
            return;
        }
        Integer line = parseLine(sender, args[2], data.getLines().size());
        if (line == null) {
            return;
        }
        String text = join(args, 3);
        if (text.isBlank()) {
            sender.sendMessage(TextUtil.error("Text cannot be empty."));
            return;
        }
        data.getLines().set(line - 1, text);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Updated line " + line + " of '" + data.getId() + "'."));
    }

    /* --------------------------- removeline ----------------------------- */

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display removeline <id> <lineNumber>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        if (!requireType(sender, data, DisplayType.TEXT)) {
            return;
        }
        Integer line = parseLine(sender, args[2], data.getLines().size());
        if (line == null) {
            return;
        }
        if (data.getLines().size() <= 1) {
            sender.sendMessage(TextUtil.error("Cannot remove the final line. Use /display delete "
                    + data.getId() + " instead."));
            return;
        }
        data.getLines().remove(line - 1);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Removed line " + line + " from '" + data.getId() + "'."));
    }

    /* ---------------------------- movehere ------------------------------ */

    private void handleMoveHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.error("Only a player can use /display movehere (location required)."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display movehere <id>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        data.setLocation(player.getLocation());
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Moved display '" + data.getId() + "' to your location."));
    }

    /* ----------------------------- nearby ------------------------------ */

    private void handleNearby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.error("Only players can use this command."));
            return;
        }

        double radius = plugin.getNearbyDefaultRadius();
        if (args.length >= 2) {
            try {
                radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(TextUtil.error("Radius must be a positive number."));
                return;
            }
            if (Double.isNaN(radius) || Double.isInfinite(radius) || radius <= 0.0D) {
                sender.sendMessage(TextUtil.error("Radius must be a positive number."));
                return;
            }
        }
        if (radius > plugin.getNearbyMaxRadius()) {
            radius = plugin.getNearbyMaxRadius();
            sender.sendMessage(TextUtil.info("Radius was capped at " + round(radius) + " blocks."));
        }

        Location origin = player.getLocation();
        double radiusSquared = radius * radius;
        List<NearbyDisplay> nearby = new ArrayList<>();
        for (DisplayData data : manager.getDisplays().values()) {
            Location loc = data.toLocation();
            if (loc == null || !loc.getWorld().equals(origin.getWorld())) {
                continue;
            }
            double distanceSquared = loc.distanceSquared(origin);
            if (distanceSquared <= radiusSquared) {
                nearby.add(new NearbyDisplay(data, loc, Math.sqrt(distanceSquared)));
            }
        }
        nearby.sort(java.util.Comparator.comparingDouble(NearbyDisplay::distance));

        if (nearby.isEmpty()) {
            sender.sendMessage(TextUtil.error("No CozyDisplays found nearby."));
            return;
        }

        sender.sendMessage(TextUtil.info("Nearby displays within " + round(radius) + " blocks:"));
        for (NearbyDisplay entry : nearby) {
            DisplayData data = entry.data();
            Location loc = entry.location();
            String line = "- " + data.getId() + " (" + round(entry.distance()) + "m) ["
                    + data.getType() + "] at "
                    + round(loc.getX()) + ", " + round(loc.getY()) + ", " + round(loc.getZ());
            String preview = preview(data);
            Component hover = Component.text("World: " + data.getWorld()
                    + "\nX/Y/Z: " + round(loc.getX()) + ", " + round(loc.getY()) + ", " + round(loc.getZ())
                    + "\nType: " + data.getType()
                    + "\nPreview: " + preview);
            player.sendMessage(Component.text(line)
                    .clickEvent(ClickEvent.suggestCommand("/display edit " + data.getId()))
                    .hoverEvent(HoverEvent.showText(hover)));
        }
    }

    /* ------------------------------ clone ------------------------------ */

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.error("Only players can use this command."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display clone <sourceId> <newId>"));
            return;
        }
        DisplayData source = require(sender, args[1]);
        if (source == null) {
            return;
        }
        String targetId = args[2];
        if (!isValidNewId(targetId)) {
            sender.sendMessage(TextUtil.error("Display id must be 1-64 characters: letters, numbers, _ or -."));
            return;
        }
        if (manager.exists(targetId)) {
            sender.sendMessage(TextUtil.error("Display '" + targetId + "' already exists."));
            return;
        }

        DisplayData clone = source.copyAs(targetId);
        clone.setLocation(player.getLocation());
        manager.put(clone);
        manager.saveAll();
        manager.respawn(clone);
        sender.sendMessage(TextUtil.success("Cloned display '" + source.getId()
                + "' to '" + targetId + "'."));
    }

    /* ------------------------------ audit ------------------------------ */

    private void handleAudit(CommandSender sender) {
        int total = manager.getDisplays().size();
        int loaded = manager.getFullySpawnedDisplayCount();
        int missingWorlds = 0;
        int missingEntities = 0;
        int missingInteractions = 0;
        int invalidMaterials = 0;
        int invalidRotation = 0;
        int autoRotating = 0;
        int refreshEnabled = 0;
        int refreshAggressive = 0;
        int refreshDeprecated = 0;
        int refreshAlways = 0;
        int invalid = 0;
        int text = 0;
        int item = 0;
        int block = 0;
        WorldEntityAudit worldAudit = manager.auditWorldEntities();

        for (DisplayData data : manager.getDisplays().values()) {
            switch (data.getType()) {
                case TEXT -> text++;
                case ITEM -> item++;
                case BLOCK -> block++;
            }
            World world = org.bukkit.Bukkit.getWorld(data.getWorld());
            if (world == null) {
                missingWorlds++;
            }
            if (data.getType() == DisplayType.ITEM && data.getItemMaterial().isAir()) {
                invalidMaterials++;
            }
            if (data.getType() == DisplayType.BLOCK && !data.getBlockMaterial().isBlock()) {
                invalidMaterials++;
            }
            if (!isFinite(data.getYaw()) || !isFinite(data.getPitch())
                    || !isFinite(data.getAutoYawPerSecond())
                    || !isFinite(data.getAutoPitchPerSecond())) {
                invalidRotation++;
            }
            if (data.isAutoRotationEnabled()) {
                autoRotating++;
            }
            if (data.isRefreshEnabled()) {
                refreshEnabled++;
                if (!data.isRefreshOnlyWhenViewed()) {
                    refreshAlways++;
                }
            }
            if (data.getRefreshIntervalMinutes() < plugin.getRefreshMinimumIntervalMinutes()) {
                refreshAggressive++;
            }
            if (data.hasDeprecatedRefreshIntervalKey()) {
                refreshDeprecated++;
            }
            if (!isFinite(data.getX()) || !isFinite(data.getY()) || !isFinite(data.getZ())
                    || !isFinite(data.getLineSpacing()) || !isFinite(data.getScale())
                    || !isFinite(data.getViewRange()) || !isFinite(data.getRefreshViewerRange())
                    || (data.getType() == DisplayType.TEXT && data.getLines().isEmpty())
                    || data.getRefreshIntervalMinutes() < 1) {
                invalid++;
            }
            if (data.isEnabled() && world != null && !manager.isFullySpawned(data)) {
                missingEntities++;
            }
            if (data.isEnabled() && data.isInteractionEnabled()
                    && world != null && !manager.isInteractionSpawned(data)) {
                missingInteractions++;
            }
        }

        sender.sendMessage(TextUtil.info("CozyDisplays Audit"));
        sender.sendMessage(TextUtil.info("Total displays: " + total));
        sender.sendMessage(TextUtil.info("Types: " + text + " text, " + item
                + " item, " + block + " block"));
        sender.sendMessage(TextUtil.info("Loaded/spawned: " + loaded));
        sender.sendMessage(TextUtil.info("Missing worlds: " + missingWorlds));
        sender.sendMessage(TextUtil.info("Missing entities: " + missingEntities));
        sender.sendMessage(TextUtil.info("Missing interactions: " + missingInteractions));
        sender.sendMessage(TextUtil.info("World visual entities: " + worldAudit.visualEntities()));
        sender.sendMessage(TextUtil.info("World interaction entities: " + worldAudit.interactionEntities()));
        sender.sendMessage(TextUtil.info("Duplicate visual entities: "
                + worldAudit.duplicateVisualEntities()));
        sender.sendMessage(TextUtil.info("Duplicate interaction entities: "
                + worldAudit.duplicateInteractionEntities()));
        sender.sendMessage(TextUtil.info("Orphan display entities: " + worldAudit.orphanEntities()));
        sender.sendMessage(TextUtil.info("Invalid materials: " + invalidMaterials));
        sender.sendMessage(TextUtil.info("Auto-rotating displays: " + autoRotating));
        sender.sendMessage(TextUtil.info("Invalid rotation values: " + invalidRotation));
        sender.sendMessage(TextUtil.info("Automatic refresh enabled: " + refreshEnabled));
        sender.sendMessage(TextUtil.info("Aggressive refresh intervals: " + refreshAggressive));
        sender.sendMessage(TextUtil.info("Deprecated refresh keys: " + refreshDeprecated));
        sender.sendMessage(TextUtil.info("Refresh without viewer checks: " + refreshAlways));
        sender.sendMessage(TextUtil.info("Invalid entries: " + invalid));
        if (missingWorlds == 0 && missingEntities == 0 && missingInteractions == 0
                && invalidMaterials == 0 && invalidRotation == 0
                && invalid == 0 && worldAudit.duplicateVisualEntities() == 0
                && worldAudit.duplicateInteractionEntities() == 0
                && worldAudit.orphanEntities() == 0) {
            sender.sendMessage(TextUtil.success("No issues found."));
        } else {
            sender.sendMessage(TextUtil.info("Use /display reload to respawn missing valid displays."));
        }
    }

    /* ------------------------------- edit ------------------------------ */

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.error("Only players can use this command."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display edit <id>"));
            return;
        }
        if (require(sender, args[1]) == null) {
            return;
        }
        editor.open(player, args[1]);
    }

    /* ---------------------------- rotation ----------------------------- */

    private void handleRotate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display rotate <id> <yaw> [pitch]"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        Double yaw = parseDouble(sender, args[2], "Yaw");
        Double pitch = args.length >= 4 ? parseDouble(sender, args[3], "Pitch") : (double) data.getPitch();
        if (yaw == null || pitch == null) {
            sender.sendMessage(TextUtil.error("Angle values must be valid numbers."));
            return;
        }
        if (args.length >= 5) {
            sender.sendMessage(TextUtil.info("Roll is not supported in CozyDisplays 1.8.3."));
        }
        setRotation(data, yaw, pitch);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Rotated display '" + data.getId()
                + "' to yaw=" + round(data.getYaw()) + ", pitch=" + round(data.getPitch()) + "."));
    }

    private void handleRotateBy(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display rotateby <id> <yawDelta> [pitchDelta]"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        Double yaw = parseDouble(sender, args[2], "Yaw delta");
        Double pitch = args.length >= 4 ? parseDouble(sender, args[3], "Pitch delta") : 0.0D;
        if (yaw == null || pitch == null) {
            sender.sendMessage(TextUtil.error("Angle values must be valid numbers."));
            return;
        }
        if (args.length >= 5) {
            sender.sendMessage(TextUtil.info("Roll is not supported in CozyDisplays 1.8.3."));
        }
        setRotation(data, data.getYaw() + yaw, data.getPitch() + pitch);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Rotated display '" + data.getId()
                + "' by yaw=" + round(yaw) + ", pitch=" + round(pitch) + "."));
    }

    private void handleFace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.error("Only players can use this command."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display face <id>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        setRotation(data, player.getLocation().getYaw(), player.getLocation().getPitch());
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Display '" + data.getId()
                + "' now matches your facing direction."));
    }

    private void handleSpin(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            if (args.length < 3) {
                sender.sendMessage(TextUtil.error("Usage: /display spin stop <id>"));
                return;
            }
            DisplayData data = require(sender, args[2]);
            if (data == null) {
                return;
            }
            data.setAutoRotationEnabled(false);
            data.setAutoYawPerSecond(0.0D);
            data.setAutoPitchPerSecond(0.0D);
            manager.saveAll();
            sender.sendMessage(TextUtil.info("Stopped auto-rotation for display '" + data.getId() + "'."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display spin <id> <yawPerSecond> [pitchPerSecond]"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        Double yaw = parseDouble(sender, args[2], "Yaw speed");
        Double pitch = args.length >= 4 ? parseDouble(sender, args[3], "Pitch speed") : 0.0D;
        if (yaw == null || pitch == null) {
            sender.sendMessage(TextUtil.error("Angle values must be valid numbers."));
            return;
        }
        if (args.length >= 5) {
            sender.sendMessage(TextUtil.info("Roll is not supported in CozyDisplays 1.8.3."));
        }
        double max = plugin.getRotationMaxDegreesPerSecond();
        double clampedYaw = clamp(yaw, -max, max);
        double clampedPitch = clamp(pitch, -max, max);
        if (clampedYaw != yaw || clampedPitch != pitch) {
            sender.sendMessage(TextUtil.info("Rotation speed was capped at "
                    + round(max) + " degrees/sec."));
        }
        data.setAutoRotationEnabled(true);
        data.setAutoYawPerSecond(clampedYaw);
        data.setAutoPitchPerSecond(clampedPitch);
        manager.saveAll();
        sender.sendMessage(TextUtil.success("Enabled auto-rotation for '" + data.getId()
                + "' at yaw=" + round(clampedYaw)
                + ", pitch=" + round(clampedPitch) + " degrees/sec."));
    }

    /* ---------------------------- snapwall ------------------------------ */

    private void handleSnapWall(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.error("Only a player can use /display snapwall (location required)."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display snapwall <id>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }

        RayTraceResult hit = player.rayTraceBlocks(6.0D);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            sender.sendMessage(TextUtil.error("No block face found within 6 blocks. Look directly at a wall."));
            return;
        }

        BlockFace face = hit.getHitBlockFace();
        float yaw;
        switch (face) {
            case NORTH -> yaw = 180.0F;
            case SOUTH -> yaw = 0.0F;
            case WEST -> yaw = 90.0F;
            case EAST -> yaw = -90.0F;
            default -> {
                sender.sendMessage(TextUtil.error("Wall snap only supports vertical block faces."));
                return;
            }
        }

        Block block = hit.getHitBlock();
        double offset = plugin.getWallOffset();
        double x = block.getX() + 0.5D + face.getModX() * (0.5D + offset);
        double y = block.getY() + 0.5D;
        double z = block.getZ() + 0.5D + face.getModZ() * (0.5D + offset);

        Location target = new Location(block.getWorld(), x, y, z, yaw, 0.0F);
        data.setLocation(target);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Moved display '" + data.getId()
                + "' to the targeted wall face."));
    }

    /* ------------------------------ nudge ------------------------------- */

    private void handleNudge(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error(
                    "Usage: /display nudge <id> <up|down|left|right|forward|back> [amount]"));
            return;
        }
        String direction = args[2].toLowerCase(Locale.ROOT);
        if (!DIRECTIONS.contains(direction)) {
            sender.sendMessage(TextUtil.error(
                    "Invalid direction. Use: up, down, left, right, forward, back."));
            return;
        }
        applyNudge(sender, args[1], direction, args.length >= 4 ? args[3] : null);
    }

    private void handleShortcutNudge(CommandSender sender, String direction, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display " + direction + " <id> [amount]"));
            return;
        }
        applyNudge(sender, args[1], direction, args.length >= 3 ? args[2] : null);
    }

    private void applyNudge(CommandSender sender, String id, String direction, String amountArg) {
        DisplayData data = require(sender, id);
        if (data == null) {
            return;
        }

        double amount = plugin.getDefaultNudgeAmount();
        if (amountArg != null) {
            double parsed;
            try {
                parsed = Double.parseDouble(amountArg);
            } catch (NumberFormatException ex) {
                sender.sendMessage(TextUtil.error("Amount must be a positive number."));
                return;
            }
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed <= 0.0D) {
                sender.sendMessage(TextUtil.error("Amount must be a positive number."));
                return;
            }
            if (parsed > MAX_NUDGE) {
                sender.sendMessage(TextUtil.error(
                        "Amount is too large. Maximum is " + MAX_NUDGE + " blocks."));
                return;
            }
            amount = parsed;
        }

        // Horizontal facing unit vector from yaw (Minecraft: yaw 0 = +Z).
        double yawRad = Math.toRadians(data.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        // Right vector (perpendicular, clockwise from above).
        double rx = -fz;
        double rz = fx;

        double dx = 0.0D;
        double dy = 0.0D;
        double dz = 0.0D;
        switch (direction) {
            case "up" -> dy = amount;
            case "down" -> dy = -amount;
            case "forward" -> {
                dx = fx * amount;
                dz = fz * amount;
            }
            case "back" -> {
                dx = -fx * amount;
                dz = -fz * amount;
            }
            case "left" -> {
                dx = rx * amount;
                dz = rz * amount;
            }
            case "right" -> {
                dx = -rx * amount;
                dz = -rz * amount;
            }
            default -> {
                return;
            }
        }

        data.setRawLocation(data.getWorld(),
                data.getX() + dx, data.getY() + dy, data.getZ() + dz,
                data.getYaw(), data.getPitch());
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Nudged display '" + data.getId()
                + "' " + direction + " by " + amount + " blocks."));
    }

    /* ------------------------------ scale ------------------------------- */

    private void handleScale(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display scale <id> <scale>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        double scale;
        try {
            scale = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.error("Scale must be a valid number."));
            return;
        }
        if (Double.isNaN(scale) || Double.isInfinite(scale)) {
            sender.sendMessage(TextUtil.error("Scale must be a valid number."));
            return;
        }
        if (scale < MIN_SCALE) {
            sender.sendMessage(TextUtil.error("Scale must be at least " + MIN_SCALE + "."));
            return;
        }
        if (scale > MAX_SCALE) {
            sender.sendMessage(TextUtil.error("Scale cannot be greater than " + MAX_SCALE + "."));
            return;
        }

        data.setScale(scale);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Set display '" + data.getId()
                + "' scale to " + scale + "."));
    }

    /* ---------------------------- viewrange ----------------------------- */

    private void handleViewRange(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display viewrange <id> <range>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        double range;
        try {
            range = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.error("View range must be a valid number."));
            return;
        }
        if (Double.isNaN(range) || Double.isInfinite(range)) {
            sender.sendMessage(TextUtil.error("View range must be a valid number."));
            return;
        }
        if (range < MIN_VIEW_RANGE) {
            sender.sendMessage(TextUtil.error("View range must be at least " + MIN_VIEW_RANGE + "."));
            return;
        }
        if (range > MAX_VIEW_RANGE) {
            sender.sendMessage(TextUtil.error(
                    "View range cannot be greater than " + MAX_VIEW_RANGE + "."));
            return;
        }

        data.setViewRange(range);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Set display '" + data.getId()
                + "' view range to " + range + " blocks."));
    }

    /* ----------------------------- enabled ------------------------------ */

    private void handleEnabled(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display enabled <id> <true|false>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        String v = args[2].toLowerCase(Locale.ROOT);
        if (!v.equals("true") && !v.equals("false")) {
            sender.sendMessage(TextUtil.error("Value must be true or false."));
            return;
        }
        data.setEnabled(v.equals("true"));
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Display '" + data.getId() + "' is now "
                + (data.isEnabled() ? "enabled (shown)." : "disabled (hidden).")));
    }

    /* -------------------------- hide / show ----------------------------- */

    private void handleHideShow(CommandSender sender, String[] args, boolean show) {
        String verb = show ? "show" : "hide";
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display " + verb + " <id>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        data.setEnabled(show);
        manager.saveAll();
        manager.respawn(data);
        if (show) {
            sender.sendMessage(TextUtil.success("Shown display '" + data.getId() + "'."));
        } else {
            sender.sendMessage(TextUtil.success("Hidden display '" + data.getId() + "'."));
        }
    }

    /* -------------------------- viewrangeall ---------------------------- */

    private void handleViewRangeAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display viewrangeall <range>"));
            return;
        }
        double range;
        try {
            range = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.error("View range must be a valid number."));
            return;
        }
        if (Double.isNaN(range) || Double.isInfinite(range)) {
            sender.sendMessage(TextUtil.error("View range must be a valid number."));
            return;
        }
        if (range < MIN_VIEW_RANGE) {
            sender.sendMessage(TextUtil.error("View range must be at least " + MIN_VIEW_RANGE + "."));
            return;
        }
        if (range > MAX_VIEW_RANGE) {
            sender.sendMessage(TextUtil.error(
                    "View range cannot be greater than " + MAX_VIEW_RANGE + "."));
            return;
        }

        int count = manager.getDisplays().size();
        for (DisplayData data : manager.getDisplays().values()) {
            data.setViewRange(range);
        }
        manager.saveAll();
        for (DisplayData data : manager.getDisplays().values()) {
            manager.respawn(data);
        }
        sender.sendMessage(TextUtil.success("Set view range to " + range
                + " blocks for " + count + " display(s)."));
    }

    /* ------------------------------ info -------------------------------- */

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display info <id>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        sender.sendMessage(TextUtil.info("Display '" + data.getId() + "':"));
        sender.sendMessage(TextUtil.info("Type: " + data.getType()));
        sender.sendMessage(TextUtil.info("Enabled: " + data.isEnabled()));
        sender.sendMessage(TextUtil.info("Lines: " + data.getLines().size()));
        if (data.getType() == DisplayType.ITEM) {
            sender.sendMessage(TextUtil.info("Item: " + data.getItemMaterial().name()));
        }
        if (data.getType() == DisplayType.BLOCK) {
            sender.sendMessage(TextUtil.info("Block: " + data.getBlockMaterial().name()));
        }
        sender.sendMessage(TextUtil.info("Scale: " + data.getScale()));
        sender.sendMessage(TextUtil.info("View range: " + data.getViewRange() + " blocks"));
        sender.sendMessage(TextUtil.info("Location: " + data.getWorld() + " "
                + data.getX() + " " + data.getY() + " " + data.getZ()));
        sender.sendMessage(TextUtil.info("Yaw/Pitch: " + data.getYaw() + " / " + data.getPitch()));
        sender.sendMessage(TextUtil.info("Auto-rotation: "
                + (data.isAutoRotationEnabled() ? "enabled" : "disabled")
                + ", yaw " + data.getAutoYawPerSecond() + " deg/s"
                + ", pitch " + data.getAutoPitchPerSecond() + " deg/s"));
        sender.sendMessage(TextUtil.info("Refresh: " + (data.isRefreshEnabled() ? "enabled" : "disabled")
                + ", interval " + data.getRefreshIntervalMinutes() + "m"
                + ", only when viewed: " + data.isRefreshOnlyWhenViewed()
                + ", viewer range: " + data.getRefreshViewerRange() + " blocks"));
        sender.sendMessage(TextUtil.info("Interaction: " + (data.isInteractionEnabled() ? "enabled" : "disabled")
                + ", size " + data.getInteractionWidth() + " x " + data.getInteractionHeight()
                + ", cooldown " + data.getInteractionCooldownSeconds() + "s"
                + ", left actions " + data.getInteractionLeftActions().size()
                + ", right actions " + data.getInteractionRightActions().size()));
        sender.sendMessage(TextUtil.info("Placeholders: "
                + (plugin.isPlaceholdersEnabled() ? "enabled" : "not installed")));
    }

    /* ------------------------------ stats ------------------------------- */

    private void handleStats(CommandSender sender) {
        sender.sendMessage(TextUtil.info("Displays: " + manager.getDisplays().size()
                + " saved, " + manager.getEnabledCount() + " enabled, "
                + manager.getHiddenCount() + " hidden."));
        sender.sendMessage(TextUtil.info("Display/interaction entities spawned: "
                + manager.getSpawnedEntityCount() + "."));
        sender.sendMessage(TextUtil.info("Saved lines: " + manager.getTotalLineCount() + "."));
        sender.sendMessage(TextUtil.info("Default view range: "
                + plugin.getDefaultViewRange() + " blocks."));
        sender.sendMessage(TextUtil.info(plugin.refreshStatus()));
    }

    /* -------------------------- item / block --------------------------- */

    private void handleSetItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display setitem <id> <material>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        if (data.getType() != DisplayType.ITEM) {
            sender.sendMessage(TextUtil.error("Display '" + data.getId() + "' is type "
                    + data.getType() + ", not ITEM."));
            return;
        }
        Material material = parseMaterial(sender, args[2]);
        if (material == null) {
            return;
        }
        data.setItemMaterial(material);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Updated item display '" + data.getId()
                + "' to " + material.name() + "."));
    }

    private void handleSetBlock(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display setblock <id> <material>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        if (data.getType() != DisplayType.BLOCK) {
            sender.sendMessage(TextUtil.error("Display '" + data.getId() + "' is type "
                    + data.getType() + ", not BLOCK."));
            return;
        }
        Material material = parseBlockMaterial(sender, args[2]);
        if (material == null) {
            return;
        }
        data.setBlockMaterial(material);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Updated block display '" + data.getId()
                + "' to " + material.name() + "."));
    }

    /* --------------------------- interaction --------------------------- */

    private void handleInteraction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.error("Usage: /display interaction <enable|disable|size|cooldown|add|clear> ..."));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "enable" -> {
                DisplayData data = require(sender, args[2]);
                if (data == null) {
                    return;
                }
                data.setInteractionEnabled(true);
                if (data.getInteractionWidth() <= 0.0D) {
                    data.setInteractionWidth(plugin.getInteractionDefaultWidth());
                }
                if (data.getInteractionHeight() <= 0.0D) {
                    data.setInteractionHeight(plugin.getInteractionDefaultHeight());
                }
                manager.saveAll();
                manager.respawn(data);
                sender.sendMessage(TextUtil.success("Enabled interaction for display '" + data.getId() + "'."));
            }
            case "disable" -> {
                DisplayData data = require(sender, args[2]);
                if (data == null) {
                    return;
                }
                data.setInteractionEnabled(false);
                manager.saveAll();
                manager.respawn(data);
                sender.sendMessage(TextUtil.info("Disabled interaction for display '" + data.getId() + "'."));
            }
            case "size" -> handleInteractionSize(sender, args);
            case "cooldown" -> handleInteractionCooldown(sender, args);
            case "add" -> handleInteractionAdd(sender, args);
            case "clear" -> handleInteractionClear(sender, args);
            default -> sender.sendMessage(TextUtil.error(
                    "Usage: /display interaction <enable|disable|size|cooldown|add|clear> ..."));
        }
    }

    private void handleInteractionSize(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(TextUtil.error("Usage: /display interaction size <id> <width> <height>"));
            return;
        }
        DisplayData data = require(sender, args[2]);
        if (data == null) {
            return;
        }
        Double width = parseDouble(sender, args[3], "Width");
        Double height = parseDouble(sender, args[4], "Height");
        if (width == null || height == null) {
            return;
        }
        data.setInteractionWidth(clamp(width, 0.1D, plugin.getInteractionMaxWidth()));
        data.setInteractionHeight(clamp(height, 0.1D, plugin.getInteractionMaxHeight()));
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Set interaction size for '" + data.getId()
                + "' to " + round(data.getInteractionWidth()) + " x "
                + round(data.getInteractionHeight()) + "."));
    }

    private void handleInteractionCooldown(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextUtil.error("Usage: /display interaction cooldown <id> <seconds>"));
            return;
        }
        DisplayData data = require(sender, args[2]);
        if (data == null) {
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.error("Cooldown must be a whole number."));
            return;
        }
        data.setInteractionCooldownSeconds((int) clamp(seconds, 0, plugin.getInteractionMaxCooldownSeconds()));
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Set interaction cooldown for '" + data.getId()
                + "' to " + data.getInteractionCooldownSeconds() + "s."));
    }

    private void handleInteractionAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(TextUtil.error("Usage: /display interaction add <id> <left|right> <action>"));
            return;
        }
        DisplayData data = require(sender, args[2]);
        if (data == null) {
            return;
        }
        String click = args[3].toLowerCase(Locale.ROOT);
        if (!click.equals("left") && !click.equals("right")) {
            sender.sendMessage(TextUtil.error("Click type must be left or right."));
            return;
        }
        String action = join(args, 4);
        if (!isValidInteractionAction(action)) {
            sender.sendMessage(TextUtil.error("Action must start with player:, console:, or message:."));
            return;
        }
        if (click.equals("left")) {
            data.getInteractionLeftActions().add(action);
        } else {
            data.getInteractionRightActions().add(action);
        }
        manager.saveAll();
        sender.sendMessage(TextUtil.success("Added " + click + " action to display '"
                + data.getId() + "'."));
    }

    private void handleInteractionClear(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextUtil.error("Usage: /display interaction clear <id> <left|right|all>"));
            return;
        }
        DisplayData data = require(sender, args[2]);
        if (data == null) {
            return;
        }
        String click = args[3].toLowerCase(Locale.ROOT);
        switch (click) {
            case "left" -> data.getInteractionLeftActions().clear();
            case "right" -> data.getInteractionRightActions().clear();
            case "all" -> {
                data.getInteractionLeftActions().clear();
                data.getInteractionRightActions().clear();
            }
            default -> {
                sender.sendMessage(TextUtil.error("Click type must be left, right, or all."));
                return;
            }
        }
        manager.saveAll();
        sender.sendMessage(TextUtil.info("Cleared " + click + " actions for display '"
                + data.getId() + "'."));
    }

    /* ----------------------------- refresh ----------------------------- */

    private void handleRefresh(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display refresh <id>"));
            sender.sendMessage(TextUtil.error("Usage: /display refresh <enable|disable|interval|status> ..."));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "enable" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.error("Usage: /display refresh enable <id>"));
                    return;
                }
                DisplayData data = require(sender, args[2]);
                if (data == null) {
                    return;
                }
                data.setRefreshEnabled(true);
                manager.saveAll();
                sender.sendMessage(TextUtil.success("Automatic refresh enabled for display '"
                        + data.getId() + "'."));
            }
            case "disable" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.error("Usage: /display refresh disable <id>"));
                    return;
                }
                DisplayData data = require(sender, args[2]);
                if (data == null) {
                    return;
                }
                data.setRefreshEnabled(false);
                manager.saveAll();
                sender.sendMessage(TextUtil.info("Automatic refresh disabled for display '"
                        + data.getId() + "'."));
            }
            case "interval" -> {
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.error("Usage: /display refresh interval <id> <minutes>"));
                    return;
                }
                DisplayData data = require(sender, args[2]);
                if (data == null) {
                    return;
                }
                Integer minutes = parsePositiveInt(sender, args[3],
                        "Refresh interval must be a positive number of minutes.");
                if (minutes == null) {
                    return;
                }
                int minimum = plugin.getRefreshMinimumIntervalMinutes();
                if (minutes < minimum) {
                    minutes = minimum;
                    sender.sendMessage(TextUtil.info("Refresh interval was raised to the minimum of "
                            + minimum + " minutes."));
                }
                data.setRefreshIntervalMinutes(minutes);
                manager.saveAll();
                sender.sendMessage(TextUtil.success("Refresh interval for '" + data.getId()
                        + "' set to " + minutes + " minutes."));
            }
            case "status" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.error("Usage: /display refresh status <id>"));
                    return;
                }
                DisplayData data = require(sender, args[2]);
                if (data == null) {
                    return;
                }
                sender.sendMessage(TextUtil.info("Refresh for " + data.getId()
                        + ": enabled=" + data.isRefreshEnabled()
                        + ", interval=" + data.getRefreshIntervalMinutes() + "m"
                        + ", only-when-viewed=" + data.isRefreshOnlyWhenViewed()
                        + ", viewer-range=" + data.getRefreshViewerRange() + "."));
            }
            default -> {
                DisplayData data = require(sender, args[1]);
                if (data == null) {
                    return;
                }
                manager.forceRefresh(data);
                sender.sendMessage(TextUtil.success("Refreshed display '" + data.getId() + "'."));
                if (!data.isRefreshEnabled()) {
                    sender.sendMessage(TextUtil.info("Automatic refresh is disabled for display '"
                            + data.getId() + "'."));
                }
            }
        }
    }

    /* ----------------------------- template ---------------------------- */

    private void handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display template <list|save|apply> ..."));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                List<String> ids = templates.listTemplateIds();
                if (ids.isEmpty()) {
                    sender.sendMessage(TextUtil.error("No display templates saved."));
                    return;
                }
                sender.sendMessage(TextUtil.info("Display templates:"));
                for (String id : ids) {
                    sender.sendMessage(TextUtil.info(" - " + id));
                }
            }
            case "save" -> {
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.error("Usage: /display template save <templateId> <displayId>"));
                    return;
                }
                String templateId = args[2];
                if (!isValidNewId(templateId)) {
                    sender.sendMessage(TextUtil.error("Template id must be 1-64 characters: letters, numbers, _ or -."));
                    return;
                }
                if (templates.exists(templateId)) {
                    sender.sendMessage(TextUtil.error("Template '" + templateId + "' already exists."));
                    return;
                }
                DisplayData source = require(sender, args[3]);
                if (source == null) {
                    return;
                }
                templates.saveTemplate(templateId, source);
                sender.sendMessage(TextUtil.success("Saved template '" + templateId
                        + "' from display '" + source.getId() + "'."));
            }
            case "apply" -> {
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.error("Usage: /display template apply <templateId> <displayId>"));
                    return;
                }
                String templateId = args[2];
                if (!templates.exists(templateId)) {
                    sender.sendMessage(TextUtil.error("Template '" + templateId + "' does not exist."));
                    return;
                }
                DisplayData target = require(sender, args[3]);
                if (target == null) {
                    return;
                }
                if (!templates.applyTemplate(templateId, target)) {
                    sender.sendMessage(TextUtil.error("Template '" + templateId + "' does not exist."));
                    return;
                }
                manager.saveAll();
                manager.respawn(target);
                sender.sendMessage(TextUtil.success("Applied template '" + templateId
                        + "' to display '" + target.getId() + "'."));
            }
            default -> sender.sendMessage(TextUtil.error("Usage: /display template <list|save|apply> ..."));
        }
    }

    /* ----------------------------- delete ------------------------------- */

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.error("Usage: /display delete <id>"));
            return;
        }
        DisplayData data = require(sender, args[1]);
        if (data == null) {
            return;
        }
        manager.despawn(data.getId());
        manager.remove(data.getId());
        manager.saveAll();
        sender.sendMessage(TextUtil.success("Deleted display '" + data.getId() + "'."));
    }

    /* ------------------------------ list -------------------------------- */

    private void handleList(CommandSender sender) {
        if (manager.getDisplays().isEmpty()) {
            sender.sendMessage(TextUtil.info("No displays exist yet. Create one with /display create <id> <text...>"));
            return;
        }
        sender.sendMessage(TextUtil.info("Displays (" + manager.getDisplays().size() + "):"));
        for (DisplayData data : manager.getDisplays().values()) {
            sender.sendMessage(TextUtil.info(" - " + data.getId()
                    + " (" + data.getType() + ", world: " + data.getWorld() + ")"));
        }
    }

    /* ----------------------------- reload ------------------------------- */

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadAll();
            sender.sendMessage(TextUtil.success("Reloaded "
                    + manager.getDisplays().size() + " display(s). "
                    + plugin.refreshStatus()));
            int spawned = manager.getSpawnedEntityCount();
            if (spawned > plugin.getEntityWarningThreshold()) {
                sender.sendMessage(TextUtil.error("Warning: " + spawned
                        + " display/interaction entities are spawned. Dense displays may cause"
                        + " client FPS lag near spawn."));
            }
        } catch (RuntimeException ex) {
            sender.sendMessage(TextUtil.error("Reload failed: " + ex.getMessage()));
        }
    }

    /* ----------------------------- helpers ------------------------------ */

    private DisplayData require(CommandSender sender, String id) {
        DisplayData data = manager.get(id);
        if (data == null) {
            sender.sendMessage(TextUtil.error("No display found with id '" + id + "'."));
        }
        return data;
    }

    private boolean isValidNewId(String id) {
        return ID_PATTERN.matcher(id).matches();
    }

    private Integer parseLine(CommandSender sender, String raw, int size) {
        int line;
        try {
            line = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.error("Line number must be a whole number."));
            return null;
        }
        if (line < 1 || line > size) {
            sender.sendMessage(TextUtil.error("Line number must be between 1 and " + size + "."));
            return null;
        }
        return line;
    }

    private Integer parsePositiveInt(CommandSender sender, String raw, String errorMessage) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) {
                sender.sendMessage(TextUtil.error(errorMessage));
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.error(errorMessage));
            return null;
        }
    }

    private String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.info("CozyDisplays commands:"));
        sender.sendMessage(TextUtil.info(" /display create <id> <text...>"));
        sender.sendMessage(TextUtil.info(" /display create text <id> <text...>"));
        sender.sendMessage(TextUtil.info(" /display create item <id> <material>"));
        sender.sendMessage(TextUtil.info(" /display create block <id> <material>"));
        sender.sendMessage(TextUtil.info(" /display addline <id> <text...>"));
        sender.sendMessage(TextUtil.info(" /display setline <id> <lineNumber> <text...>"));
        sender.sendMessage(TextUtil.info(" /display removeline <id> <lineNumber>"));
        sender.sendMessage(TextUtil.info(" /display movehere <id>"));
        sender.sendMessage(TextUtil.info(" /display nearby [radius] - List nearby displays in your world."));
        sender.sendMessage(TextUtil.info(" /display clone <sourceId> <newId>"));
        sender.sendMessage(TextUtil.info(" /display audit - Report display storage/spawn health."));
        sender.sendMessage(TextUtil.info(" /display edit <id> - Open the admin editor GUI."));
        sender.sendMessage(TextUtil.info(" /display rotate <id> <yaw> [pitch]"));
        sender.sendMessage(TextUtil.info(" /display rotateby <id> <yawDelta> [pitchDelta]"));
        sender.sendMessage(TextUtil.info(" /display face <id>"));
        sender.sendMessage(TextUtil.info(" /display spin <id> <yawPerSecond> [pitchPerSecond]"));
        sender.sendMessage(TextUtil.info(" /display spin stop <id>"));
        sender.sendMessage(TextUtil.info(" /display snapwall <id> - Center a display on the targeted wall face."));
        sender.sendMessage(TextUtil.info(" /display nudge <id> <up|down|left|right|forward|back> [amount]"));
        sender.sendMessage(TextUtil.info(" /display up|down|left|right|forward|back <id> [amount]"));
        sender.sendMessage(TextUtil.info(" /display scale <id> <scale> - Set a display's text scale."));
        sender.sendMessage(TextUtil.info(" /display viewrange <id> <range> - Set how far the display renders."));
        sender.sendMessage(TextUtil.info(" /display enabled <id> <true|false> - Show or hide a display."));
        sender.sendMessage(TextUtil.info(" /display hide <id> - Despawn a display but keep it saved."));
        sender.sendMessage(TextUtil.info(" /display show <id> - Respawn a hidden display."));
        sender.sendMessage(TextUtil.info(" /display viewrangeall <range> - Set view range for all displays."));
        sender.sendMessage(TextUtil.info(" /display info <id> - Show a display's saved settings."));
        sender.sendMessage(TextUtil.info(" /display stats - Show display and render counts."));
        sender.sendMessage(TextUtil.info(" /display setitem <id> <material>"));
        sender.sendMessage(TextUtil.info(" /display setblock <id> <material>"));
        sender.sendMessage(TextUtil.info(" /display interaction <enable|disable|size|cooldown|add|clear> ..."));
        sender.sendMessage(TextUtil.info(" /display refresh <id> - Force a one-time refresh."));
        sender.sendMessage(TextUtil.info(" /display refresh <enable|disable|interval|status> ..."));
        sender.sendMessage(TextUtil.info(" /display template <list|save|apply> ..."));
        sender.sendMessage(TextUtil.info(" /display delete <id>"));
        sender.sendMessage(TextUtil.info(" /display list"));
        sender.sendMessage(TextUtil.info(" /display reload"));
    }

    /* -------------------------- tab complete ---------------------------- */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            if (args.length == 2) {
                return filter(List.of("text", "item", "block"), args[1]);
            }
            if (args.length == 4 && (args[1].equalsIgnoreCase("item")
                    || args[1].equalsIgnoreCase("block"))) {
                return filter(materialSuggestions(args[1].equalsIgnoreCase("block")), args[3]);
            }
        }

        if (args.length == 2 && needsId(sub)) {
            return filter(new ArrayList<>(manager.getDisplays().keySet()), args[1]);
        }

        if (sub.equals("template")) {
            if (args.length == 2) {
                return filter(List.of("list", "save", "apply"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("apply")) {
                return filter(templates.listTemplateIds(), args[2]);
            }
            if (args.length == 4 && (args[1].equalsIgnoreCase("save")
                    || args[1].equalsIgnoreCase("apply"))) {
                return filter(new ArrayList<>(manager.getDisplays().keySet()), args[3]);
            }
        }

        if (sub.equals("interaction")) {
            if (args.length == 2) {
                return filter(List.of("enable", "disable", "size", "cooldown", "add", "clear"), args[1]);
            }
            if (args.length == 3) {
                return filter(new ArrayList<>(manager.getDisplays().keySet()), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
                return filter(List.of("left", "right"), args[3]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("clear")) {
                return filter(List.of("left", "right", "all"), args[3]);
            }
            if (args.length == 5 && args[1].equalsIgnoreCase("add")) {
                return filter(List.of("player:", "console:", "message:"), args[4]);
            }
        }

        if (sub.equals("refresh")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>(manager.getDisplays().keySet());
                options.addAll(REFRESH_ACTIONS);
                return filter(options, args[1]);
            }
            if (args.length == 3 && REFRESH_ACTIONS.contains(args[1].toLowerCase(Locale.ROOT))) {
                return filter(new ArrayList<>(manager.getDisplays().keySet()), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("interval")) {
                return filter(REFRESH_INTERVAL_SUGGESTIONS, args[3]);
            }
        }

        if (sub.equals("spin")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>(manager.getDisplays().keySet());
                options.add("stop");
                return filter(options, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("stop")) {
                return filter(new ArrayList<>(manager.getDisplays().keySet()), args[2]);
            }
        }

        if (args.length == 3 && (sub.equals("setline") || sub.equals("removeline"))) {
            DisplayData data = manager.get(args[1]);
            if (data != null) {
                List<String> numbers = new ArrayList<>();
                for (int i = 1; i <= data.getLines().size(); i++) {
                    numbers.add(String.valueOf(i));
                }
                return filter(numbers, args[2]);
            }
        }

        if (sub.equals("nudge")) {
            if (args.length == 3) {
                return filter(DIRECTIONS, args[2]);
            }
            if (args.length == 4) {
                return filter(AMOUNT_SUGGESTIONS, args[3]);
            }
        }

        if (DIRECTIONS.contains(sub) && args.length == 3) {
            return filter(AMOUNT_SUGGESTIONS, args[2]);
        }

        if (sub.equals("scale") && args.length == 3) {
            return filter(SCALE_SUGGESTIONS, args[2]);
        }

        if (sub.equals("viewrange") && args.length == 3) {
            return filter(VIEW_RANGE_SUGGESTIONS, args[2]);
        }

        if (sub.equals("enabled") && args.length == 3) {
            return filter(BOOL_SUGGESTIONS, args[2]);
        }

        if (sub.equals("setitem") && args.length == 3) {
            return filter(materialSuggestions(false), args[2]);
        }

        if (sub.equals("setblock") && args.length == 3) {
            return filter(materialSuggestions(true), args[2]);
        }

        if (sub.equals("viewrangeall") && args.length == 2) {
            return filter(VIEW_RANGE_SUGGESTIONS, args[1]);
        }

        return List.of();
    }

    private boolean needsId(String sub) {
        return switch (sub) {
            case "addline", "setline", "removeline", "movehere", "snapwall",
                 "nudge", "up", "down", "left", "right", "forward", "back",
                 "scale", "viewrange", "enabled", "hide", "show", "info",
                 "edit", "clone", "rotate", "rotateby", "face",
                 "setitem", "setblock", "delete" -> true;
            default -> false;
        };
    }

    private boolean requireType(CommandSender sender, DisplayData data, DisplayType expected) {
        if (data.getType() == expected) {
            return true;
        }
        sender.sendMessage(TextUtil.error("Display '" + data.getId() + "' is type "
                + data.getType() + ", not " + expected + "."));
        return false;
    }

    private Material parseMaterial(CommandSender sender, String raw) {
        Material material = Material.matchMaterial(raw);
        if (material == null || material.isAir()) {
            sender.sendMessage(TextUtil.error("Invalid material '" + raw + "'."));
            return null;
        }
        return material;
    }

    private Material parseBlockMaterial(CommandSender sender, String raw) {
        Material material = parseMaterial(sender, raw);
        if (material == null) {
            return null;
        }
        if (!material.isBlock()) {
            sender.sendMessage(TextUtil.error("Material '" + raw + "' is not a block."));
            return null;
        }
        return material;
    }

    private Double parseDouble(CommandSender sender, String raw, String label) {
        try {
            double value = Double.parseDouble(raw);
            if (!isFinite(value)) {
                sender.sendMessage(TextUtil.error(label + " must be a valid number."));
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.error(label + " must be a valid number."));
            return null;
        }
    }

    private boolean isValidInteractionAction(String action) {
        String lower = action.toLowerCase(Locale.ROOT);
        return lower.startsWith("player:")
                || lower.startsWith("console:")
                || lower.startsWith("message:");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void setRotation(DisplayData data, double yaw, double pitch) {
        data.setRawLocation(data.getWorld(), data.getX(), data.getY(), data.getZ(),
                (float) yaw, (float) pitch);
    }

    private String preview(DisplayData data) {
        return switch (data.getType()) {
            case TEXT -> data.getLines().isEmpty() ? "" : data.getLines().getFirst();
            case ITEM -> data.getItemMaterial().name();
            case BLOCK -> data.getBlockMaterial().name();
        };
    }

    private List<String> materialSuggestions(boolean blocksOnly) {
        List<String> options = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isAir()) {
                continue;
            }
            if (blocksOnly && !material.isBlock()) {
                continue;
            }
            options.add(material.name());
            if (options.size() >= 200) {
                break;
            }
        }
        return options;
    }

    private boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record NearbyDisplay(DisplayData data, Location location, double distance) {
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(opt);
            }
        }
        return out;
    }
}
