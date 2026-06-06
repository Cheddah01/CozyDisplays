package gg.cozycrafters.cozydisplays.command;

import gg.cozycrafters.cozydisplays.CozyDisplaysPlugin;
import gg.cozycrafters.cozydisplays.display.DisplayData;
import gg.cozycrafters.cozydisplays.display.DisplayManager;
import gg.cozycrafters.cozydisplays.gui.DisplayEditor;
import gg.cozycrafters.cozydisplays.storage.TemplateStorage;
import gg.cozycrafters.cozydisplays.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
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
            "snapwall", "nudge",
            "up", "down", "left", "right", "forward", "back",
            "scale", "viewrange", "viewrangeall", "enabled",
            "hide", "show", "info", "stats",
            "template", "delete", "list", "reload");

    private static final List<String> SCALE_SUGGESTIONS = List.of(
            "0.5", "0.75", "1.0", "1.25", "1.5", "2.0");

    private static final List<String> VIEW_RANGE_SUGGESTIONS = List.of(
            "4.0", "8.0", "12.0", "16.0", "32.0", "64.0");

    private static final List<String> BOOL_SUGGESTIONS = List.of("true", "false");

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
            return;
        }
        String id = args[1];
        if (!isValidNewId(id)) {
            sender.sendMessage(TextUtil.error("Display id must be 1-64 characters: letters, numbers, _ or -."));
            return;
        }
        if (manager.exists(id)) {
            sender.sendMessage(TextUtil.error("A display with id '" + id + "' already exists."));
            return;
        }
        String text = join(args, 2);
        if (text.isBlank()) {
            sender.sendMessage(TextUtil.error("Text cannot be empty."));
            return;
        }

        DisplayData data = new DisplayData(id);
        data.setLocation(player.getLocation());
        data.addLine(text);
        manager.put(data);
        manager.saveAll();
        manager.respawn(data);
        sender.sendMessage(TextUtil.success("Created display '" + id + "' at your location."));
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
            String line = "- " + data.getId() + " (" + round(entry.distance()) + "m) at "
                    + round(loc.getX()) + ", " + round(loc.getY()) + ", " + round(loc.getZ());
            String preview = data.getLines().isEmpty() ? "" : data.getLines().getFirst();
            Component hover = Component.text("World: " + data.getWorld()
                    + "\nX/Y/Z: " + round(loc.getX()) + ", " + round(loc.getY()) + ", " + round(loc.getZ())
                    + "\nText: " + preview);
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
        int invalid = 0;

        for (DisplayData data : manager.getDisplays().values()) {
            World world = org.bukkit.Bukkit.getWorld(data.getWorld());
            if (world == null) {
                missingWorlds++;
            }
            if (!isFinite(data.getX()) || !isFinite(data.getY()) || !isFinite(data.getZ())
                    || !isFinite(data.getLineSpacing()) || !isFinite(data.getScale())
                    || !isFinite(data.getViewRange()) || !isFinite(data.getRefreshViewerRange())
                    || data.getLines().isEmpty()
                    || data.getRefreshIntervalSeconds() < 1) {
                invalid++;
            }
            if (data.isEnabled() && world != null && !manager.isFullySpawned(data)) {
                missingEntities++;
            }
        }

        sender.sendMessage(TextUtil.info("CozyDisplays Audit"));
        sender.sendMessage(TextUtil.info("Total displays: " + total));
        sender.sendMessage(TextUtil.info("Loaded/spawned: " + loaded));
        sender.sendMessage(TextUtil.info("Missing worlds: " + missingWorlds));
        sender.sendMessage(TextUtil.info("Missing entities: " + missingEntities));
        sender.sendMessage(TextUtil.info("Invalid entries: " + invalid));
        if (missingWorlds == 0 && missingEntities == 0 && invalid == 0) {
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
        sender.sendMessage(TextUtil.info("Enabled: " + data.isEnabled()));
        sender.sendMessage(TextUtil.info("Lines: " + data.getLines().size()));
        sender.sendMessage(TextUtil.info("Scale: " + data.getScale()));
        sender.sendMessage(TextUtil.info("View range: " + data.getViewRange() + " blocks"));
        sender.sendMessage(TextUtil.info("Location: " + data.getWorld() + " "
                + data.getX() + " " + data.getY() + " " + data.getZ()));
        sender.sendMessage(TextUtil.info("Yaw/Pitch: " + data.getYaw() + " / " + data.getPitch()));
        sender.sendMessage(TextUtil.info("Refresh: " + (data.isRefreshEnabled() ? "enabled" : "disabled")
                + ", interval " + data.getRefreshIntervalSeconds() + "s"
                + ", only when viewed: " + data.isRefreshOnlyWhenViewed()
                + ", viewer range: " + data.getRefreshViewerRange() + " blocks"));
        sender.sendMessage(TextUtil.info("Placeholders: "
                + (plugin.isPlaceholdersEnabled() ? "enabled" : "not installed")));
    }

    /* ------------------------------ stats ------------------------------- */

    private void handleStats(CommandSender sender) {
        sender.sendMessage(TextUtil.info("Displays: " + manager.getDisplays().size()
                + " saved, " + manager.getEnabledCount() + " enabled, "
                + manager.getHiddenCount() + " hidden."));
        sender.sendMessage(TextUtil.info("TextDisplay entities spawned: "
                + manager.getSpawnedEntityCount() + "."));
        sender.sendMessage(TextUtil.info("Saved lines: " + manager.getTotalLineCount() + "."));
        sender.sendMessage(TextUtil.info("Default view range: "
                + plugin.getDefaultViewRange() + " blocks."));
        sender.sendMessage(TextUtil.info(plugin.refreshStatus()));
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
                    + " (" + data.getLines().size() + " lines, world: " + data.getWorld() + ")"));
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
                        + " TextDisplay entities are spawned. Dense displays may cause"
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

    private String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.info("CozyDisplays commands:"));
        sender.sendMessage(TextUtil.info(" /display create <id> <text...>"));
        sender.sendMessage(TextUtil.info(" /display addline <id> <text...>"));
        sender.sendMessage(TextUtil.info(" /display setline <id> <lineNumber> <text...>"));
        sender.sendMessage(TextUtil.info(" /display removeline <id> <lineNumber>"));
        sender.sendMessage(TextUtil.info(" /display movehere <id>"));
        sender.sendMessage(TextUtil.info(" /display nearby [radius] - List nearby displays in your world."));
        sender.sendMessage(TextUtil.info(" /display clone <sourceId> <newId>"));
        sender.sendMessage(TextUtil.info(" /display audit - Report display storage/spawn health."));
        sender.sendMessage(TextUtil.info(" /display edit <id> - Open the admin editor GUI."));
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
                 "edit", "clone", "delete" -> true;
            default -> false;
        };
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
