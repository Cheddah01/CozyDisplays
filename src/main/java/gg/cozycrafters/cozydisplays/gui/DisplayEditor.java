package gg.cozycrafters.cozydisplays.gui;

import gg.cozycrafters.cozydisplays.CozyDisplaysPlugin;
import gg.cozycrafters.cozydisplays.display.DisplayData;
import gg.cozycrafters.cozydisplays.display.DisplayManager;
import gg.cozycrafters.cozydisplays.display.DisplayType;
import gg.cozycrafters.cozydisplays.display.TextRenderMode;
import gg.cozycrafters.cozydisplays.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DisplayEditor implements Listener {

    private static final int SIZE = 36;
    private static final double MIN_SCALE = 0.1D;
    private static final double MAX_SCALE = 10.0D;
    private static final double MIN_VIEW_RANGE = 1.0D;
    private static final double MAX_VIEW_RANGE = 64.0D;

    private final CozyDisplaysPlugin plugin;
    private final DisplayManager manager;

    public DisplayEditor(CozyDisplaysPlugin plugin, DisplayManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player player, String id) {
        DisplayData data = manager.get(id);
        if (data == null) {
            player.sendMessage(TextUtil.error("Display '" + id + "' no longer exists."));
            return;
        }

        Inventory inventory = Bukkit.createInventory(new EditorHolder(id), SIZE,
                TextUtil.legacy(format(plugin.getConfig().getString(
                        "editor.title", "&8CozyDisplays &7- &f%id%"), data)));
        populate(inventory, data);
        player.openInventory(inventory);
        player.sendMessage(TextUtil.success("Opened editor for '" + id + "'."));
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof EditorHolder) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }

        DisplayData data = manager.get(holder.id());
        if (data == null) {
            player.closeInventory();
            player.sendMessage(TextUtil.error("Display '" + holder.id() + "' no longer exists."));
            return;
        }

        switch (event.getRawSlot()) {
            case 0 -> rotate(player, data, -plugin.getEditorRotationStepYaw(), 0.0D);
            case 1 -> rotate(player, data, plugin.getEditorRotationStepYaw(), 0.0D);
            case 2 -> rotate(player, data, 0.0D, -plugin.getEditorRotationStepPitch());
            case 3 -> rotate(player, data, 0.0D, plugin.getEditorRotationStepPitch());
            case 5 -> resetRotation(player, data);
            case 7 -> suggestMaterialEdit(player, data);
            case 8 -> cycleRenderMode(player, data);
            case 9 -> toggleBackground(player, data);
            case 10 -> teleport(player, data);
            case 11 -> changeOpacity(player, data, -plugin.getEditorOpacityStep());
            case 12 -> moveHere(player, data);
            case 13 -> changeOpacity(player, data, plugin.getEditorOpacityStep());
            case 14 -> forceRefresh(player, data);
            case 15 -> cycleAlignment(player, data);
            case 16 -> suggestClone(player, data);
            case 17 -> suggestBackgroundColor(player, data);
            case 19 -> nudge(player, data, -plugin.getEditorNudgeStep(), 0.0D, 0.0D);
            case 20 -> nudge(player, data, plugin.getEditorNudgeStep(), 0.0D, 0.0D);
            case 21 -> nudge(player, data, 0.0D, -plugin.getEditorNudgeStep(), 0.0D);
            case 22 -> nudge(player, data, 0.0D, plugin.getEditorNudgeStep(), 0.0D);
            case 23 -> nudge(player, data, 0.0D, 0.0D, -plugin.getEditorNudgeStep());
            case 24 -> nudge(player, data, 0.0D, 0.0D, plugin.getEditorNudgeStep());
            case 25 -> changeLineSpacing(player, data, -plugin.getEditorLineSpacingStep());
            case 26 -> changeLineSpacing(player, data, plugin.getEditorLineSpacingStep());
            case 28 -> setScale(player, data, data.getScale() - plugin.getEditorScaleStep());
            case 29 -> setScale(player, data, 1.0D);
            case 30 -> setScale(player, data, data.getScale() + plugin.getEditorScaleStep());
            case 32 -> setViewRange(player, data,
                    data.getViewRange() - plugin.getEditorViewRangeStep());
            case 33 -> setViewRange(player, data,
                    data.getViewRange() + plugin.getEditorViewRangeStep());
            case 34 -> faceMe(player, data);
            case 35 -> player.closeInventory();
            default -> {
                return;
            }
        }

        DisplayData updated = manager.get(holder.id());
        if (updated != null && player.getOpenInventory().getTopInventory().getHolder() instanceof EditorHolder) {
            populate(top, updated);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EditorHolder) {
            event.setCancelled(true);
        }
    }

    private void populate(Inventory inventory, DisplayData data) {
        inventory.clear();
        inventory.setItem(0, item(Material.ARROW,
                plugin.getConfig().getString("editor.items.rotate-left.name", "&eRotate Left"),
                rotationLore(), data));
        inventory.setItem(1, item(Material.ARROW,
                plugin.getConfig().getString("editor.items.rotate-right.name", "&eRotate Right"),
                rotationLore(), data));
        inventory.setItem(2, item(Material.ARROW,
                plugin.getConfig().getString("editor.items.rotate-up.name", "&eRotate Up"),
                rotationLore(), data));
        inventory.setItem(3, item(Material.ARROW,
                plugin.getConfig().getString("editor.items.rotate-down.name", "&eRotate Down"),
                rotationLore(), data));
        inventory.setItem(4, item(Material.NAME_TAG,
                plugin.getConfig().getString("editor.items.info.name", "&b%id%"),
                plugin.getConfig().getStringList("editor.items.info.lore"), data));
        inventory.setItem(5, item(Material.REDSTONE,
                plugin.getConfig().getString("editor.items.rotation-reset.name", "&cReset Rotation"),
                rotationLore(), data));
        inventory.setItem(6, item(Material.OAK_SIGN,
                plugin.getConfig().getString("editor.items.type.name", "&bType: &f%type%"),
                plugin.getConfig().getStringList("editor.items.type.lore"), data));
        if (data.getType() == DisplayType.ITEM) {
            inventory.setItem(7, item(data.getItemMaterial(),
                    plugin.getConfig().getString("editor.items.item-material.name", "&eItem: &f%material%"),
                    plugin.getConfig().getStringList("editor.items.item-material.lore"), data));
        } else if (data.getType() == DisplayType.BLOCK) {
            inventory.setItem(7, item(data.getBlockMaterial(),
                    plugin.getConfig().getString("editor.items.block-material.name", "&eBlock: &f%material%"),
                    plugin.getConfig().getStringList("editor.items.block-material.lore"), data));
        } else {
            inventory.setItem(7, item(Material.PAINTING,
                    plugin.getConfig().getString("editor.items.panel-info.name", "&bText Panel Info"),
                    plugin.getConfig().getStringList("editor.items.panel-info.lore"), data));
        }
        if (data.getType() == DisplayType.TEXT) {
            inventory.setItem(8, item(Material.ITEM_FRAME,
                    plugin.getConfig().getString("editor.items.render-mode-cycle.name", "&bCycle Render Mode"),
                    configLore("editor.items.render-mode-cycle.lore",
                            List.of("&7Change how text lines are rendered.", "",
                                    "&8- &7Current: &f%render_mode%", "",
                                    "&eClick to cycle mode.")), data));
            inventory.setItem(9, item(Material.BLACK_STAINED_GLASS_PANE,
                    plugin.getConfig().getString("editor.items.background-toggle.name", "&eToggle Background"),
                    configLore("editor.items.background-toggle.lore",
                            List.of("&7Show or hide the text background.", "",
                                    "&8- &7Current: &f%background%", "",
                                    "&eClick to toggle.")), data));
            inventory.setItem(11, item(Material.GRAY_DYE,
                    plugin.getConfig().getString("editor.items.opacity-down.name", "&cOpacity -"),
                    configLore("editor.items.opacity-down.lore",
                            List.of("&7Reduce the background opacity.", "",
                                    "&8- &7Current: &f%opacity%%", "",
                                    "&eClick to decrease.")), data));
            inventory.setItem(13, item(Material.WHITE_DYE,
                    plugin.getConfig().getString("editor.items.opacity-up.name", "&aOpacity +"),
                    configLore("editor.items.opacity-up.lore",
                            List.of("&7Increase the background opacity.", "",
                                    "&8- &7Current: &f%opacity%%", "",
                                    "&eClick to increase.")), data));
            inventory.setItem(15, item(Material.OAK_SIGN,
                    plugin.getConfig().getString("editor.items.align-cycle.name", "&bCycle Alignment"),
                    configLore("editor.items.align-cycle.lore",
                            List.of("&7Adjust text alignment.", "",
                                    "&8- &7Current: &f%alignment%", "",
                                    "&eClick to cycle.")), data));
            inventory.setItem(17, item(Material.MAGENTA_DYE,
                    plugin.getConfig().getString("editor.items.bgcolor-suggest.name", "&dSet Background Color"),
                    configLore("editor.items.bgcolor-suggest.lore",
                            List.of("&7Prepare the color command.", "",
                                    "&8- &7Current: &f%color%", "",
                                    "&eClick to suggest command.")), data));
            inventory.setItem(25, item(Material.STRING,
                    plugin.getConfig().getString("editor.items.line-spacing-down.name", "&cLine Spacing -"),
                    configLore("editor.items.line-spacing-down.lore",
                            List.of("&7Tighten line spacing.", "",
                                    "&8- &7Current: &f%spacing%", "",
                                    "&eClick to decrease.")), data));
            inventory.setItem(26, item(Material.IRON_BARS,
                    plugin.getConfig().getString("editor.items.line-spacing-up.name", "&aLine Spacing +"),
                    configLore("editor.items.line-spacing-up.lore",
                            List.of("&7Add more space between lines.", "",
                                    "&8- &7Current: &f%spacing%", "",
                                    "&eClick to increase.")), data));
        }
        inventory.setItem(10, item(Material.ENDER_PEARL,
                plugin.getConfig().getString("editor.items.teleport.name", "&aTeleport"),
                plugin.getConfig().getStringList("editor.items.teleport.lore"), data));
        inventory.setItem(12, item(Material.COMPASS,
                plugin.getConfig().getString("editor.items.move-here.name", "&eMove Here"),
                plugin.getConfig().getStringList("editor.items.move-here.lore"), data));
        inventory.setItem(14, item(Material.CLOCK,
                plugin.getConfig().getString("editor.items.refresh.name", "&bRefresh"),
                plugin.getConfig().getStringList("editor.items.refresh.lore"), data));
        inventory.setItem(16, item(Material.WRITABLE_BOOK,
                plugin.getConfig().getString("editor.items.clone.name", "&dClone"),
                plugin.getConfig().getStringList("editor.items.clone.lore"), data));

        List<String> nudgeLore = configLore("editor.items.nudge.lore",
                List.of("&7Move this display by one editor step.", "",
                        "&8- &7Step: &f%nudge_step% blocks", "",
                        "&eClick to nudge."));
        inventory.setItem(19, item(Material.RED_CONCRETE,
                plugin.getConfig().getString("editor.items.nudge-negative-x.name", "&c-X"), nudgeLore, data));
        inventory.setItem(20, item(Material.LIME_CONCRETE,
                plugin.getConfig().getString("editor.items.nudge-positive-x.name", "&a+X"), nudgeLore, data));
        inventory.setItem(21, item(Material.RED_CONCRETE,
                plugin.getConfig().getString("editor.items.nudge-negative-y.name", "&c-Y"), nudgeLore, data));
        inventory.setItem(22, item(Material.LIME_CONCRETE,
                plugin.getConfig().getString("editor.items.nudge-positive-y.name", "&a+Y"), nudgeLore, data));
        inventory.setItem(23, item(Material.RED_CONCRETE,
                plugin.getConfig().getString("editor.items.nudge-negative-z.name", "&c-Z"), nudgeLore, data));
        inventory.setItem(24, item(Material.LIME_CONCRETE,
                plugin.getConfig().getString("editor.items.nudge-positive-z.name", "&a+Z"), nudgeLore, data));

        inventory.setItem(28, item(Material.SMALL_AMETHYST_BUD,
                plugin.getConfig().getString("editor.items.scale-down.name", "&eScale -"),
                configLore("editor.items.scale-down.lore",
                        List.of("&7Make the display smaller.", "",
                                "&8- &7Current: &f%scale%", "",
                                "&eClick to decrease.")), data));
        inventory.setItem(29, item(Material.AMETHYST_CLUSTER,
                plugin.getConfig().getString("editor.items.scale-reset.name", "&eReset Scale"),
                configLore("editor.items.scale-reset.lore",
                        List.of("&7Return the display to normal size.", "",
                                "&8- &7Current: &f%scale%", "",
                                "&eClick to reset to 1.0.")), data));
        inventory.setItem(30, item(Material.LARGE_AMETHYST_BUD,
                plugin.getConfig().getString("editor.items.scale-up.name", "&eScale +"),
                configLore("editor.items.scale-up.lore",
                        List.of("&7Make the display larger.", "",
                                "&8- &7Current: &f%scale%", "",
                                "&eClick to increase.")), data));

        inventory.setItem(32, item(Material.SPYGLASS,
                plugin.getConfig().getString("editor.items.view-range-down.name", "&bView Range -"),
                configLore("editor.items.view-range-down.lore",
                        List.of("&7Lower the render distance.", "",
                                "&8- &7Current: &f%view_range% blocks", "",
                                "&eClick to decrease.")), data));
        inventory.setItem(33, item(Material.SPYGLASS,
                plugin.getConfig().getString("editor.items.view-range-up.name", "&bView Range +"),
                configLore("editor.items.view-range-up.lore",
                        List.of("&7Raise the render distance.", "",
                                "&8- &7Current: &f%view_range% blocks", "",
                                "&eClick to increase.")), data));
        inventory.setItem(34, item(Material.PLAYER_HEAD,
                plugin.getConfig().getString("editor.items.face-me.name", "&bFace Me"),
                configLore("editor.items.face-me.lore",
                        List.of("&7Match your current facing direction.", "",
                                "&8- &7Yaw: &f%yaw%",
                                "&8- &7Pitch: &f%pitch%", "",
                                "&eClick to face you.")), data));
        inventory.setItem(35, item(Material.BARRIER,
                plugin.getConfig().getString("editor.items.close.name", "&cClose"),
                configLore("editor.items.close.lore",
                        List.of("&7Close the editor.", "",
                                "&eClick to close.")), data));
    }

    private ItemStack item(Material material, String name, List<String> lore, DisplayData data) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.legacy(format(name, data)));
        List<Component> renderedLore = new ArrayList<>();
        List<String> sourceLore = lore == null || lore.isEmpty() ? defaultLore(name) : lore;
        for (String line : sourceLore) {
            renderedLore.add(TextUtil.legacy(format(line, data)));
        }
        meta.lore(renderedLore);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> defaultLore(String name) {
        if (name != null && name.contains("%id%")) {
            return List.of("&7Manage this display.", "",
                    "&8- &7World: &f%world%",
                    "&8- &7Location: &f%x%, %y%, %z%", "",
                    "&eUse the buttons below to edit.");
        }
        return List.of();
    }

    private List<String> configLore(String path, List<String> fallback) {
        List<String> configured = plugin.getConfig().getStringList(path);
        return configured.isEmpty() ? fallback : configured;
    }

    private void teleport(Player player, DisplayData data) {
        Location loc = data.toLocation();
        if (loc == null) {
            player.sendMessage(TextUtil.error("World '" + data.getWorld() + "' is not loaded."));
            return;
        }
        player.teleport(loc);
        player.sendMessage(TextUtil.success("Teleported to display '" + data.getId() + "'."));
    }

    private void moveHere(Player player, DisplayData data) {
        data.setLocation(player.getLocation());
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Moved display '" + data.getId() + "' to your location."));
    }

    private void forceRefresh(Player player, DisplayData data) {
        manager.forceRefresh(data);
        player.sendMessage(TextUtil.success("Refreshed display '" + data.getId() + "'."));
        if (!data.isRefreshEnabled()) {
            player.sendMessage(TextUtil.info("Automatic refresh is disabled for display '"
                    + data.getId() + "'."));
        }
    }

    private void suggestClone(Player player, DisplayData data) {
        player.closeInventory();
        String command = "/display clone " + data.getId() + " new_id";
        player.sendMessage(Component.text("Click to prepare clone command: ")
                .append(Component.text(command)
                        .clickEvent(ClickEvent.suggestCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text("Suggest command")))));
    }

    private void suggestMaterialEdit(Player player, DisplayData data) {
        player.closeInventory();
        String command;
        switch (data.getType()) {
            case ITEM -> command = "/display setitem " + data.getId() + " " + data.getItemMaterial().name();
            case BLOCK -> command = "/display setblock " + data.getId() + " " + data.getBlockMaterial().name();
            default -> {
                player.sendMessage(TextUtil.info("Text displays do not have item or block material."));
                return;
            }
        }
        player.sendMessage(Component.text("Click to prepare material command: ")
                .append(Component.text(command)
                        .clickEvent(ClickEvent.suggestCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text("Suggest command")))));
    }

    private void cycleRenderMode(Player player, DisplayData data) {
        if (!requireText(player, data)) {
            return;
        }
        data.setTextRenderMode(data.getTextRenderMode() == TextRenderMode.LINE_ENTITIES
                ? TextRenderMode.SINGLE_ENTITY : TextRenderMode.LINE_ENTITIES);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Updated render mode for '" + data.getId() + "'."));
    }

    private void toggleBackground(Player player, DisplayData data) {
        if (!requireText(player, data)) {
            return;
        }
        data.setBackground(!data.isBackground());
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Updated background for '" + data.getId() + "'."));
    }

    private void changeOpacity(Player player, DisplayData data, int delta) {
        if (!requireText(player, data)) {
            return;
        }
        data.setBackgroundOpacity(data.getBackgroundOpacity() + delta);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Set background opacity to "
                + data.getBackgroundOpacity() + "%."));
    }

    private void cycleAlignment(Player player, DisplayData data) {
        if (!requireText(player, data)) {
            return;
        }
        TextDisplay.TextAlignment next = switch (data.getAlignment()) {
            case LEFT -> TextDisplay.TextAlignment.CENTER;
            case CENTER -> TextDisplay.TextAlignment.RIGHT;
            case RIGHT -> TextDisplay.TextAlignment.LEFT;
        };
        data.setAlignment(next);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Updated alignment for '" + data.getId() + "'."));
    }

    private void suggestBackgroundColor(Player player, DisplayData data) {
        if (!requireText(player, data)) {
            return;
        }
        player.closeInventory();
        String command = "/display bgcolor " + data.getId() + " " + data.getBackgroundColor();
        player.sendMessage(Component.text("Click to prepare background color command: ")
                .append(Component.text(command)
                        .clickEvent(ClickEvent.suggestCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text("Suggest command")))));
    }

    private void changeLineSpacing(Player player, DisplayData data, double delta) {
        if (!requireText(player, data)) {
            return;
        }
        double clamped = Math.max(0.05D, Math.min(2.0D, data.getLineSpacing() + delta));
        data.setLineSpacing(clamped);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Set line spacing to " + round(clamped) + "."));
    }

    private boolean requireText(Player player, DisplayData data) {
        if (data.getType() == DisplayType.TEXT) {
            return true;
        }
        player.sendMessage(TextUtil.info("This control is only available for text displays."));
        return false;
    }

    private void nudge(Player player, DisplayData data, double dx, double dy, double dz) {
        data.setRawLocation(data.getWorld(), data.getX() + dx, data.getY() + dy, data.getZ() + dz,
                data.getYaw(), data.getPitch());
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Nudged display '" + data.getId() + "'."));
    }

    private void setScale(Player player, DisplayData data, double scale) {
        double clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        data.setScale(clamped);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Set display '" + data.getId()
                + "' scale to " + round(clamped) + "."));
    }

    private void setViewRange(Player player, DisplayData data, double range) {
        double clamped = Math.max(MIN_VIEW_RANGE, Math.min(MAX_VIEW_RANGE, range));
        data.setViewRange(clamped);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Set display '" + data.getId()
                + "' view range to " + round(clamped) + " blocks."));
    }

    private void rotate(Player player, DisplayData data, double yawDelta, double pitchDelta) {
        data.setRawLocation(data.getWorld(), data.getX(), data.getY(), data.getZ(),
                (float) (data.getYaw() + yawDelta), (float) (data.getPitch() + pitchDelta));
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Rotated display '" + data.getId() + "'."));
    }

    private void resetRotation(Player player, DisplayData data) {
        data.setRawLocation(data.getWorld(), data.getX(), data.getY(), data.getZ(), 0.0F, 0.0F);
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Reset rotation for display '" + data.getId() + "'."));
    }

    private void faceMe(Player player, DisplayData data) {
        data.setRawLocation(data.getWorld(), data.getX(), data.getY(), data.getZ(),
                player.getLocation().getYaw(), player.getLocation().getPitch());
        manager.saveAll();
        manager.respawn(data);
        player.sendMessage(TextUtil.success("Display '" + data.getId()
                + "' now matches your facing direction."));
    }

    private List<String> rotationLore() {
        return List.of(
                "&7Adjust this display's rotation.",
                "",
                "&8- &7Yaw: &f%yaw%",
                "&8- &7Pitch: &f%pitch%",
                "&8- &7Yaw step: &f%rotation_step_yaw%",
                "&8- &7Pitch step: &f%rotation_step_pitch%",
                "",
                "&eClick to rotate.");
    }

    private String format(String input, DisplayData data) {
        if (input == null) {
            return "";
        }
        return input
                .replace("%id%", data.getId())
                .replace("%world%", String.valueOf(data.getWorld()))
                .replace("%type%", data.getType().name())
                .replace("%material%", materialName(data))
                .replace("%render_mode%", data.getTextRenderMode().name())
                .replace("%alignment%", data.getAlignment().name())
                .replace("%background%", data.isBackground() ? "enabled" : "disabled")
                .replace("%color%", data.getBackgroundColor())
                .replace("%opacity%", String.valueOf(data.getBackgroundOpacity()))
                .replace("%spacing%", round(data.getLineSpacing()))
                .replace("%yaw%", round(data.getYaw()))
                .replace("%pitch%", round(data.getPitch()))
                .replace("%rotation_step_yaw%", round(plugin.getEditorRotationStepYaw()))
                .replace("%rotation_step_pitch%", round(plugin.getEditorRotationStepPitch()))
                .replace("%nudge_step%", round(plugin.getEditorNudgeStep()))
                .replace("%x%", round(data.getX()))
                .replace("%y%", round(data.getY()))
                .replace("%z%", round(data.getZ()))
                .replace("%scale%", round(data.getScale()))
                .replace("%view_range%", round(data.getViewRange()));
    }

    private String materialName(DisplayData data) {
        return switch (data.getType()) {
            case ITEM -> data.getItemMaterial().name();
            case BLOCK -> data.getBlockMaterial().name();
            case TEXT -> "";
        };
    }

    private String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private record EditorHolder(String id) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
