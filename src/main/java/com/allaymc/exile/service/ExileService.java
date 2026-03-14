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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ExileService {

    private final AllayMcPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final MessageUtil messageUtil;
    private final Map<UUID, Long> lastDangerWarn = new HashMap<>();
    private final Map<UUID, Long> lastBarrierWarn = new HashMap<>();

    public ExileService(AllayMcPlugin plugin, PlayerDataManager playerDataManager, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.messageUtil = messageUtil;
    }

    public void disableVanillaWorldBorder() {
        String worldName = plugin.getConfig().getString("server-border.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Could not disable vanilla world border: world not found: " + worldName);
            return;
        }

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(5.9999968E7);
        border.setDamageAmount(0.0);
        border.setDamageBuffer(5.9999968E7);
        border.setWarningDistance(0);
        border.setWarningTime(0);

        plugin.getLogger().info("Vanilla world border disabled for custom border system.");
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

    public void startDangerZoneTask() {
        if (!plugin.getConfig().getBoolean("danger-zone.enabled", true)) {
            return;
        }

        long periodTicks = Math.max(20L, plugin.getConfig().getLong("danger-zone.apply-every-seconds", 1) * 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            String worldName = plugin.getConfig().getString("server-border.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;

            for (Player player : world.getPlayers()) {
                if (isInDangerZone(player.getLocation())) {
                    double damage = plugin.getConfig().getDouble("danger-zone.damage-per-tick", 8.0);
                    player.damage(damage);

                    long now = System.currentTimeMillis();
                    long warnEvery = plugin.getConfig().getLong("danger-zone.warn-every-seconds", 3) * 1000L;
                    long last = lastDangerWarn.getOrDefault(player.getUniqueId(), 0L);

                    if (now - last >= warnEvery) {
                        player.sendMessage(messageUtil.color(plugin.getConfig().getString(
                                "danger-zone.warning-message",
                                "&cYou are in the forbidden zone between the server border and exile border!"
                        )));
                        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.8f, 0.7f);
                        lastDangerWarn.put(player.getUniqueId(), now);
                    }
                }
            }
        }, 20L, periodTicks);
    }

    public boolean isInsideMainBorder(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String worldName = plugin.getConfig().getString("server-border.world", "world");
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) return false;

        double centerX = plugin.getConfig().getDouble("server-border.center-x", 0.0);
        double centerZ = plugin.getConfig().getDouble("server-border.center-z", 0.0);
        double halfSize = getGlobalServerBorderSize() / 2.0;

        return Math.abs(location.getX() - centerX) <= halfSize &&
                Math.abs(location.getZ() - centerZ) <= halfSize;
    }

    public boolean isOutsideExileBorder(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String worldName = plugin.getConfig().getString("exile-border.world", "world");
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) return false;

        double exileRadius = plugin.getConfig().getDouble("exile-border.radius-blocks", 2000000.0);

        return Math.abs(location.getX()) >= exileRadius &&
                Math.abs(location.getZ()) >= exileRadius;
    }

    public boolean isInDangerZone(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String worldName = plugin.getConfig().getString("server-border.world", "world");
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) return false;

        return !isInsideMainBorder(location) && !isOutsideExileBorder(location);
    }

    public void showMainBorderHit(Player player, Location attempted) {
        showBarrierFeedback(player, attempted, true, false);
    }

    public void showExileBorderHit(Player player, Location attempted, boolean exiledPlayer) {
        showBarrierFeedback(player, attempted, false, exiledPlayer);
    }

    private void showBarrierFeedback(Player player, Location attempted, boolean mainBorder, boolean exiledPlayer) {
        if (!plugin.getConfig().getBoolean("barrier.enabled", true)) {
            return;
        }

        long now = System.currentTimeMillis();
        long warnEvery = plugin.getConfig().getLong("barrier.warn-every-ms", 1200L);
        long last = lastBarrierWarn.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < warnEvery) {
            return;
        }

        String message;
        if (mainBorder) {
            message = plugin.getConfig().getString("barrier.main-message", "&cA mysterious force blocks you at the server border.");
        } else {
            message = exiledPlayer
                    ? plugin.getConfig().getString("barrier.exile-message-exiled", "&cThe exile border rejects your return.")
                    : plugin.getConfig().getString("barrier.exile-message-normal", "&cThe exile lands reject you.");
        }

        player.sendMessage(messageUtil.color(message));

        if (plugin.getConfig().getBoolean("barrier.sounds", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.5f);
        }

        if (plugin.getConfig().getBoolean("barrier.particles", true)) {
            spawnBarrierEffect(player, attempted, mainBorder);
        }

        lastBarrierWarn.put(player.getUniqueId(), now);
    }

    private void spawnBarrierEffect(Player player, Location attempted, boolean mainBorder) {
        World world = player.getWorld();
        Particle particle = mainBorder ? Particle.END_ROD : Particle.ELECTRIC_SPARK;

        double yBase = player.getLocation().getY();
        for (double y = yBase - 0.5; y <= yBase + 2.5; y += 0.35) {
            world.spawnParticle(particle, attempted.getX(), y, attempted.getZ(), 8, 0.15, 0.05, 0.15, 0.0);
        }
    }

    public double getGlobalServerBorderSize() {
        return plugin.getConfig().getDouble("server-border.start-size-blocks", 6400.0);
    }

    public boolean setGlobalServerBorderSize(double newSize) {
        if (newSize <= 1.0) {
            return false;
        }

        plugin.getConfig().set("server-border.start-size-blocks", newSize);
        plugin.saveConfig();
        return true;
    }

    public boolean addGlobalServerBorderSize(double amount) {
        if (amount <= 0.0) {
            return false;
        }
        return setGlobalServerBorderSize(getGlobalServerBorderSize() + amount);
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

        Location exileLocation = generateRandomExileLocation();
        if (exileLocation == null) {
            player.sendMessage(ChatColor.RED + "Could not find a safe exile location.");
            return;
        }

        data.setExiled(true);
        data.setExileEndTime(System.currentTimeMillis() + durationMillis);
        data.setExileCount(data.getExileCount() + 1);
        data.setReason(reason == null || reason.isBlank() ? "No reason provided" : reason);
        data.setExileLocation(serializeLocation(exileLocation));

        player.teleport(exileLocation);
        playerDataManager.save(player.getUniqueId());

        String time = TimeUtil.formatDuration(durationMillis);
        player.sendMessage(messageUtil.replace(messageUtil.get("player-exiled-chat"), "%time%", time));
        player.sendMessage(messageUtil.replace(messageUtil.get("player-exiled-reason"), "%reason%", data.getReason()));
        player.sendMessage(messageUtil.get("player-exiled-location")
                .replace("%x%", String.valueOf(exileLocation.getBlockX()))
                .replace("%z%", String.valueOf(exileLocation.getBlockZ())));

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

        Location exileLoc = deserializeLocation(data.getExileLocation());
        if (exileLoc == null) {
            exileLoc = generateRandomExileLocation();
            if (exileLoc != null) {
                data.setExileLocation(serializeLocation(exileLoc));
            }
        }

        if (exileLoc != null) {
            player.teleport(exileLoc);
        }

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

    public Location getSavedExileLocation(UUID uuid) {
        return deserializeLocation(playerDataManager.getData(uuid).getExileLocation());
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

    private Location generateRandomExileLocation() {
        String worldName = plugin.getConfig().getString("exile-border.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        int minCoord = (int) Math.round(plugin.getConfig().getDouble("exile-border.exile-spawn-min-distance", 2000000.0));
        int maxCoord = (int) Math.round(plugin.getConfig().getDouble("exile-border.exile-spawn-max-distance", 2300000.0));
        int tries = plugin.getConfig().getInt("exile-border.safe-teleport-tries", 100);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < tries; i++) {
            int x = random.nextInt(minCoord, maxCoord + 1);
            int z = random.nextInt(minCoord, maxCoord + 1);

            if (random.nextBoolean()) x = -x;
            if (random.nextBoolean()) z = -z;

            if (Math.abs(x) < 2000000 || Math.abs(z) < 2000000) {
                continue;
            }

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

        if (plugin.getConfig().getBoolean("exile-border.avoid-water", true) &&
                (material == Material.WATER || material == Material.KELP || material == Material.SEAGRASS)) {
            return false;
        }

        if (plugin.getConfig().getBoolean("exile-border.avoid-lava", true) &&
                (material == Material.LAVA || material == Material.MAGMA_BLOCK)) {
            return false;
        }

        if (plugin.getConfig().getBoolean("exile-border.avoid-leaves", true) &&
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
}
