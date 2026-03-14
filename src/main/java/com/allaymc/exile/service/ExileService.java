package com.allaymc.exile.service;

import com.allaymc.exile.AllayMcPlugin;
import com.allaymc.exile.data.ExileData;
import com.allaymc.exile.data.PlayerDataManager;
import com.allaymc.exile.util.InventoryUtil;
import com.allaymc.exile.util.MessageUtil;
import com.allaymc.exile.util.TimeUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ExileService {

    private final AllayMcPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final MessageUtil messageUtil;

    public ExileService(AllayMcPlugin plugin, PlayerDataManager playerDataManager, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.messageUtil = messageUtil;
    }

    public void startTimerTask() {
        long periodTicks = Math.max(20L, plugin.getConfig().getLong("settings.timer-check-seconds", 1) * 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ExileData data = playerDataManager.getData(player.getUniqueId());
                if (!data.isExiled()) continue;

                long remaining = data.getExileEndTime() - System.currentTimeMillis();
                if (remaining <= 0) {
                    freePlayer(player, true);
                }
            }
        }, 20L, periodTicks);
    }

    public boolean isExiled(UUID uuid) {
        return playerDataManager.getData(uuid).isExiled();
    }

    public long getRemaining(UUID uuid) {
        ExileData data = playerDataManager.getData(uuid);
        return Math.max(0L, data.getExileEndTime() - System.currentTimeMillis());
    }

    public ExileData getData(UUID uuid) {
        return playerDataManager.getData(uuid);
    }

    public void exilePlayer(Player player, long durationMillis, String reason) {
        ExileData data = playerDataManager.getData(player.getUniqueId());

        saveNormalState(player, data);

        clearPlayer(player);

        if (!data.getExileInventory().isEmpty()) {
            loadExileInventory(player, data);
        }

        data.setExiled(true);
        data.setExileEndTime(System.currentTimeMillis() + durationMillis);
        data.setExileCount(data.getExileCount() + 1);
        data.setReason(reason == null || reason.isBlank() ? "No reason provided" : reason);

        teleportToExile(player);
        playerDataManager.save(player.getUniqueId());

        String time = TimeUtil.formatDuration(durationMillis);
        player.sendMessage(messageUtil.replace(messageUtil.get("player-exiled-chat"), "%time%", time));
        player.sendMessage(messageUtil.replace(messageUtil.get("player-exiled-reason"), "%reason%", data.getReason()));

        String title = messageUtil.replace(messageUtil.raw("player-exiled-title"), "%time%", time);
        String subtitle = messageUtil.replace(messageUtil.raw("player-exiled-subtitle"), "%time%", time);
        player.sendTitle(title, subtitle, 10, 60, 20);

        plugin.getLogger().info(ChatColor.stripColor(
                messageUtil.replace(
                        messageUtil.replace(
                                messageUtil.replace(messageUtil.raw("console-log-exile"), "%player%", player.getName()),
                                "%time%", time
                        ),
                        "%reason%", data.getReason()
                )
        ));
    }

    public void extendPlayer(Player player, long extraMillis) {
        ExileData data = playerDataManager.getData(player.getUniqueId());
        data.setExileEndTime(data.getExileEndTime() + extraMillis);
        playerDataManager.save(player.getUniqueId());

        String time = TimeUtil.formatDuration(extraMillis);
        player.sendMessage(messageUtil.replace(messageUtil.get("player-extended-chat"), "%time%", time));
        plugin.getLogger().info(ChatColor.stripColor(
                messageUtil.replace(
                        messageUtil.replace(messageUtil.raw("console-log-extend"), "%player%", player.getName()),
                        "%time%", time
                )
        ));
    }

    public void freePlayer(Player player, boolean timeEnded) {
        ExileData data = playerDataManager.getData(player.getUniqueId());

        saveExileState(player, data);

        clearPlayer(player);
        loadNormalState(player, data);

        data.setExiled(false);
        data.setExileEndTime(0L);
        data.setReason("No reason provided");

        teleportToReturn(player, data);
        playerDataManager.save(player.getUniqueId());

        if (timeEnded) {
            messageUtil.send(player, "player-time-ended");
        } else {
            messageUtil.send(player, "player-freed-chat");
        }

        plugin.getLogger().info(ChatColor.stripColor(
                messageUtil.replace(messageUtil.raw("console-log-free"), "%player%", player.getName())
        ));
    }

    public void removePlayerPermanently(Player player) {
        String mode = plugin.getConfig().getString("punishments.exileremove.mode", "kick").toLowerCase();

        switch (mode) {
            case "ban" -> Bukkit.getBanList(BanList.Type.NAME).addBan(
                    player.getName(),
                    plugin.getConfig().getString("punishments.exileremove.ban-reason", "Removed from server."),
                    null,
                    "AllayMc"
            );
            case "whitelist-remove" -> {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(player.getUniqueId());
                offline.setWhitelisted(false);
            }
            default -> {
            }
        }

        player.kickPlayer(messageUtil.color(
                plugin.getConfig().getString("punishments.exileremove.kick-message",
                        "&cYou have been permanently removed from the server.")
        ));

        plugin.getLogger().info(ChatColor.stripColor(
                messageUtil.replace(messageUtil.raw("console-log-remove"), "%player%", player.getName())
        ));
    }

    public void applyExileStateIfNeeded(Player player) {
        ExileData data = playerDataManager.getData(player.getUniqueId());
        if (!data.isExiled()) return;

        long remaining = getRemaining(player.getUniqueId());
        if (remaining <= 0) {
            freePlayer(player, true);
            return;
        }

        saveExileState(player, data);
        clearPlayer(player);
        loadExileInventory(player, data);
        teleportToExile(player);
    }

    public void savePlayerStateOnQuit(Player player) {
        ExileData data = playerDataManager.getData(player.getUniqueId());
        if (data.isExiled()) {
            saveExileState(player, data);
        } else {
            saveNormalState(player, data);
        }
        playerDataManager.save(player.getUniqueId());
    }

    public void saveAllOnlineExileStates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerStateOnQuit(player);
        }
    }

    private void saveNormalState(Player player, ExileData data) {
        data.setNormalInventory(InventoryUtil.itemStackArrayToBase64(player.getInventory().getStorageContents()));
        data.setNormalArmor(InventoryUtil.itemStackArrayToBase64(player.getInventory().getArmorContents()));
        data.setNormalOffhand(InventoryUtil.itemStackArrayToBase64(new ItemStack[]{player.getInventory().getItemInOffHand()}));
        data.setNormalLocation(serializeLocation(player.getLocation()));
    }

    private void loadNormalState(Player player, ExileData data) {
        ItemStack[] storage = InventoryUtil.itemStackArrayFromBase64(data.getNormalInventory());
        ItemStack[] armor = InventoryUtil.itemStackArrayFromBase64(data.getNormalArmor());
        ItemStack[] offhand = InventoryUtil.itemStackArrayFromBase64(data.getNormalOffhand());

        player.getInventory().setStorageContents(fixSize(storage, 36));
        player.getInventory().setArmorContents(fixSize(armor, 4));
        player.getInventory().setItemInOffHand(offhand.length > 0 ? offhand[0] : null);
        player.updateInventory();
    }

    private void saveExileState(Player player, ExileData data) {
        data.setExileInventory(InventoryUtil.itemStackArrayToBase64(player.getInventory().getStorageContents()));
        data.setExileArmor(InventoryUtil.itemStackArrayToBase64(player.getInventory().getArmorContents()));
        data.setExileOffhand(InventoryUtil.itemStackArrayToBase64(new ItemStack[]{player.getInventory().getItemInOffHand()}));
    }

    private void loadExileInventory(Player player, ExileData data) {
        ItemStack[] storage = InventoryUtil.itemStackArrayFromBase64(data.getExileInventory());
        ItemStack[] armor = InventoryUtil.itemStackArrayFromBase64(data.getExileArmor());
        ItemStack[] offhand = InventoryUtil.itemStackArrayFromBase64(data.getExileOffhand());

        player.getInventory().setStorageContents(fixSize(storage, 36));
        player.getInventory().setArmorContents(fixSize(armor, 4));
        player.getInventory().setItemInOffHand(offhand.length > 0 ? offhand[0] : null);
        player.updateInventory();
    }

    private ItemStack[] fixSize(ItemStack[] items, int size) {
        ItemStack[] fixed = new ItemStack[size];
        for (int i = 0; i < Math.min(items.length, size); i++) {
            fixed[i] = items[i];
        }
        return fixed;
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void teleportToExile(Player player) {
        Location exile = getConfiguredLocation("exile");
        if (exile != null) {
            player.teleport(exile);
        }
    }

    private void teleportToReturn(Player player, ExileData data) {
        boolean useNormal = plugin.getConfig().getBoolean("settings.restore-normal-location-on-free", true);
        Location target = null;

        if (useNormal && data.getNormalLocation() != null && !data.getNormalLocation().isEmpty()) {
            target = deserializeLocation(data.getNormalLocation());
        }

        if (target == null && plugin.getConfig().getBoolean("return-location.use-config-return-if-normal-location-missing", true)) {
            target = getConfiguredLocation("return-location");
        }

        if (target != null) {
            player.teleport(target);
        }
    }

    private Location getConfiguredLocation(String path) {
        String worldName = plugin.getConfig().getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = plugin.getConfig().getDouble(path + ".x");
        double y = plugin.getConfig().getDouble(path + ".y");
        double z = plugin.getConfig().getDouble(path + ".z");
        float yaw = (float) plugin.getConfig().getDouble(path + ".yaw");
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    private Location deserializeLocation(String str) {
        try {
            String[] parts = str.split(";");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            return new Location(
                    world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5])
            );
        } catch (Exception e) {
            return null;
        }
    }
}
