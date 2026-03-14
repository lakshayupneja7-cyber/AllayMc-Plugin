package com.allaymc.exile.data;

import com.allaymc.exile.AllayMcPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final AllayMcPlugin plugin;
    private final File file;
    private final YamlConfiguration config;
    private final Map<UUID, ExileData> cache = new HashMap<>();

    public PlayerDataManager(AllayMcPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create playerdata.yml");
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    public ExileData getData(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new ExileData());
    }

    public void loadAll() {
        cache.clear();
        for (String key : config.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            ExileData data = new ExileData();
            data.setExiled(config.getBoolean(key + ".exiled", false));
            data.setExileEndTime(config.getLong(key + ".exileEndTime", 0L));
            data.setExileCount(config.getInt(key + ".exileCount", 0));
            data.setReason(config.getString(key + ".reason", "No reason provided"));

            data.setNormalInventory(config.getString(key + ".normalInventory", ""));
            data.setNormalArmor(config.getString(key + ".normalArmor", ""));
            data.setNormalOffhand(config.getString(key + ".normalOffhand", ""));

            data.setExileInventory(config.getString(key + ".exileInventory", ""));
            data.setExileArmor(config.getString(key + ".exileArmor", ""));
            data.setExileOffhand(config.getString(key + ".exileOffhand", ""));

            data.setNormalLocation(config.getString(key + ".normalLocation", ""));

            cache.put(uuid, data);
        }
    }

    public void save(UUID uuid) {
        ExileData data = cache.get(uuid);
        if (data == null) return;

        String base = uuid.toString();
        config.set(base + ".exiled", data.isExiled());
        config.set(base + ".exileEndTime", data.getExileEndTime());
        config.set(base + ".exileCount", data.getExileCount());
        config.set(base + ".reason", data.getReason());

        config.set(base + ".normalInventory", data.getNormalInventory());
        config.set(base + ".normalArmor", data.getNormalArmor());
        config.set(base + ".normalOffhand", data.getNormalOffhand());

        config.set(base + ".exileInventory", data.getExileInventory());
        config.set(base + ".exileArmor", data.getExileArmor());
        config.set(base + ".exileOffhand", data.getExileOffhand());

        config.set(base + ".normalLocation", data.getNormalLocation());

        saveFile();
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            save(uuid);
        }
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save playerdata.yml");
            e.printStackTrace();
        }
    }
}
