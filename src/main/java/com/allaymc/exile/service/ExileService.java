package com.allaymc.exile.service;

import com.allaymc.exile.AllayMcPlugin;
import com.allaymc.exile.data.ExileData;
import com.allaymc.exile.data.PlayerDataManager;
import com.allaymc.exile.util.InventoryUtil;
import com.allaymc.exile.util.MessageUtil;
import com.allaymc.exile.util.TimeUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
                } else {
                    applyPersonalBorder(player, data);
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

        Location exileLocation = getOrCreateExileLocation(data);
        if (exileLocation == null) {
            player.sendMessage(ChatColor.RED + "Could not find a safe exile location.");
            return;
        }

        data.setExiled(true);
        data.setExileEndTime(System.currentTimeMillis() + durationMillis);
        data.setExileCount(data.getExileCount() + 1);
        data.setReason(reason == null || reason.isBlank() ? "No reason provided" : reason);
        data.setExileLocation(serializeLocation(exileLocation));
        data.setExileBorderSize(plugin.getConfig().getDouble("border.initial-size", 64.0));

        player.teleport(exileLocation);
        applyPersonalBorder(player, data);
        playerDataManager.save(player.getUniqueId());

        String time = TimeUtil.formatDuration(durationMillis);
        player.sendMessage(messageUtil.replace(messageUtil.get("player-exiled-chat"), "%time%", time));
        player.sendMessage(messageUtil.replace(messageUtil.get("player-exiled-reason"), "%reason%", data.getReason()));
        player.sendMessage(messageUtil.replace(
                messageUtil.replace(messageUtil.get("player-exiled-location"), "%x%", String.valueOf(exileLocation.getBlockX())),
                "%z%", String.valueOf(exileLocation.getBlockZ())
        ));
        player.sendMessage(messageUtil.replace(
                messageUtil.get("player-exiled-border"),
                "%size%", formatBorderSize(data.getExileBorderSize())
        ));

        String title = messageUtil.raw("player-exiled-title");
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
        data.setExileLocation("");
        data.setExileBorderSize(plugin.getConfig().getDouble("border.initial-size", 64.0));

        clearPersonalBorder(player);
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
        clearPersonalBorder(player);

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

        Location exileLoc = deserializeLocation(data.getExileLocation());
        if (exileLoc == null) {
            exileLoc = getOrCreateExileLocation(data);
            if (exileLoc != null) {
                data.setExileLocation(serializeLocation(exileLoc));
            }
        }

        if (exileLoc != null) {
            player.teleport(exileLoc);
        }

        applyPersonalBorder(player, data);
        playerDataManager.save(player.getUniqueId());
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

    public double getBorderSize(UUID uuid) {
        return playerDataManager.getData(uuid).getExileBorderSize();
    }

    public boolean setBorderSize(Player player, double newSize) {
        ExileData data = playerDataManager.getData(player.getUniqueId());
        if (!data.isExiled()) return false;

        double maxSize = plugin.getConfig().getDouble("border.max-size", 2048.0);
        newSize = Math.max(1.0, Math.min(newSize, maxSize));

        data.setExileBorderSize(newSize);
        applyPersonalBorder(player, data);
        playerDataManager.save(player.getUniqueId());
        return true;
    }

    public boolean expandBorder(Player player, double amount) {
        return setBorderSize(player, getBorderSize(player.getUniqueId()) + amount);
    }

    public boolean shrinkBorder(Player player, double amount) {
        return setBorderSize(player, getBorderSize(player.getUniqueId()) - amount);
    }

    public boolean resetBorder(Player player) {
        return setBorderSize(player, plugin.getConfig().getDouble("border.initial-size", 64.0));
    }

    public Location getSavedExileLocation(UUID uuid) {
        return deserializeLocation(playerDataManager.getData(uuid).getExileLocation());
    }

    public void applyPersonalBorder(Player player, ExileData data) {
        if (!plugin.getConfig().getBoolean("border.enabled", true)) {
            return;
        }

        Location center = deserializeLocation(data.getExileLocation());
        if (center == null || center.getWorld() == null) {
            return;
        }

        WorldBorder border = Bukkit.getServer().createWorldBorder();
        border.setCenter(center.getX(), center.getZ());
        border.setSize(data.getExileBorderSize());
        border.setDamageBuffer(plugin.getConfig().getDouble("border.damage-buffer", 5.0));
        border.setDamageAmount(plugin.getConfig().getDouble("border.damage-amount", 0.2));
        border.setWarningDistance(plugin.getConfig().getInt("border.warning-distance", 8));
        border.setWarningTime(plugin.getConfig().getInt("border.warning-time", 10));

        try {
            player.setWorldBorder(border);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    public void clearPersonalBorder(Player player) {
        try {
            player.setWorldBorder(null);
        } catch (UnsupportedOperationException ignored) {
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

    private void teleportToReturn(Player player, ExileData data) {
        boolean useNormal = plugin.getConfig().getBoolean("settings.restore-normal-location-on-free", true);
        Location target = null;

        if (useNormal && data.getNormalLocation() != null && !data.getNormalLocation().isEmpty()) {
            target = deserializeLocation(data.getNormalLocation());
        }

        if (target == null && plugin.getConfig().getBoolean("return-location.use-config-return-if-normal-location-missing", true)) {
            target = getConfiguredReturnLocation();
        }

        if (target != null) {
            player.teleport(target);
        }
    }

    private Location getConfiguredReturnLocation() {
        String worldName = plugin.getConfig().getString("return-location.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = plugin.getConfig().getDouble("return-location.x");
        double y = plugin.getConfig().getDouble("return-location.y");
        double z = plugin.getConfig().getDouble("return-location.z");
        float yaw = (float) plugin.getConfig().getDouble("return-location.yaw");
        float pitch = (float) plugin.getConfig().getDouble("return-location.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location getOrCreateExileLocation(ExileData data) {
        Location saved = deserializeLocation(data.getExileLocation());
        if (saved != null) {
            return saved;
        }
        return generateRandomExileLocation();
    }

    private Location generateRandomExileLocation() {
        String worldName = plugin.getConfig().getString("exile.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        int minRadius = plugin.getConfig().getInt("exile.min-radius", 2000000);
        int maxRadius = plugin.getConfig().getInt("exile.max-radius", 2200000);
        int tries = plugin.getConfig().getInt("exile.safe-teleport-tries", 100);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            double distance = random.nextDouble(minRadius, maxRadius + 1.0);

            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);

            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight()) {
                continue;
            }

            Block ground = world.getBlockAt(x, y - 1, z);
            Material groundType = ground.getType();

            if (!isSafeGround(groundType)) {
                continue;
            }

            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }

            return new Location(world, x + 0.5, y, z + 0.5);
        }

        return null;
    }

    private boolean isSafeGround(Material material) {
        if (material.isAir()) return false;

        if (plugin.getConfig().getBoolean("exile.avoid-water", true) &&
                (material == Material.WATER || material == Material.KELP || material == Material.SEAGRASS)) {
            return false;
        }

        if (plugin.getConfig().getBoolean("exile.avoid-lava", true) &&
                (material == Material.LAVA || material == Material.MAGMA_BLOCK)) {
            return false;
        }

        if (plugin.getConfig().getBoolean("exile.avoid-leaves", true) &&
                material.name().endsWith("_LEAVES")) {
            return false;
        }

        return true;
    }

    public String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    public Location deserializeLocation(String str) {
        try {
            if (str == null || str.isEmpty()) return null;

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

    private String formatBorderSize(double size) {
        if (size == Math.floor(size)) {
            return String.valueOf((int) size);
        }
        return String.format("%.2f", size);
    }
}
