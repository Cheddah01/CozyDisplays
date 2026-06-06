package gg.cozycrafters.cozydisplays.interaction;

import gg.cozycrafters.cozydisplays.display.DisplayData;
import gg.cozycrafters.cozydisplays.display.DisplayManager;
import gg.cozycrafters.cozydisplays.placeholder.PlaceholderService;
import gg.cozycrafters.cozydisplays.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DisplayInteractionListener implements Listener {

    private final DisplayManager manager;
    private final PlaceholderService placeholders;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public DisplayInteractionListener(DisplayManager manager, PlaceholderService placeholders) {
        this.manager = manager;
        this.placeholders = placeholders;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String id = manager.getOwnedInteractionDisplayId(event.getRightClicked());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        runActions(event.getPlayer(), id, "right");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        String id = manager.getOwnedInteractionDisplayId(event.getEntity());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        runActions(player, id, "left");
    }

    private void runActions(Player player, String id, String click) {
        DisplayData data = manager.get(id);
        if (data == null || !data.isInteractionEnabled()) {
            return;
        }
        List<String> actions = click.equals("left")
                ? data.getInteractionLeftActions()
                : data.getInteractionRightActions();
        if (actions.isEmpty()) {
            return;
        }
        if (isOnCooldown(player.getUniqueId(), data, click)) {
            player.sendMessage(TextUtil.error("Please wait before clicking this again."));
            return;
        }
        markCooldown(player.getUniqueId(), data, click);
        for (String action : actions) {
            runAction(player, data, action);
        }
    }

    private boolean isOnCooldown(UUID playerId, DisplayData data, String click) {
        int seconds = data.getInteractionCooldownSeconds();
        if (seconds <= 0) {
            return false;
        }
        long last = cooldowns.getOrDefault(cooldownKey(playerId, data.getId(), click), 0L);
        return System.currentTimeMillis() - last < seconds * 1000L;
    }

    private void markCooldown(UUID playerId, DisplayData data, String click) {
        cooldowns.put(cooldownKey(playerId, data.getId(), click), System.currentTimeMillis());
    }

    private String cooldownKey(UUID playerId, String id, String click) {
        return playerId + ":" + id + ":" + click;
    }

    private void runAction(Player player, DisplayData data, String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return;
        }
        String rendered = placeholders.applyPlaceholders(player, replaceTokens(player, data, rawAction));
        String lower = rendered.toLowerCase(Locale.ROOT);
        if (lower.startsWith("player:")) {
            String command = stripLeadingSlash(rendered.substring("player:".length()).trim());
            if (!command.isBlank()) {
                player.performCommand(command);
            }
        } else if (lower.startsWith("console:")) {
            String command = stripLeadingSlash(rendered.substring("console:".length()).trim());
            if (!command.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } else if (lower.startsWith("message:")) {
            player.sendMessage(TextUtil.legacy(rendered.substring("message:".length()).trim()));
        }
    }

    private String replaceTokens(Player player, DisplayData data, String input) {
        Location loc = data.toLocation();
        String world = loc == null || loc.getWorld() == null ? data.getWorld() : loc.getWorld().getName();
        return input
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%id%", data.getId())
                .replace("%world%", String.valueOf(world))
                .replace("%x%", round(data.getX()))
                .replace("%y%", round(data.getY()))
                .replace("%z%", round(data.getZ()));
    }

    private String stripLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
