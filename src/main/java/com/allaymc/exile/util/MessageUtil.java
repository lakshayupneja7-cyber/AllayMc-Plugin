package com.allaymc.exile.util;

import com.allaymc.exile.AllayMcPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageUtil {

    private final AllayMcPlugin plugin;
    private final YamlConfiguration messages;

    public MessageUtil(AllayMcPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String prefix = messages.getString("prefix", "&8[&bAllayMc&8] ");
        String value = messages.getString(path, "&cMissing message: " + path);
        return color(prefix + value);
    }

    public String raw(String path) {
        return color(messages.getString(path, "&cMissing message: " + path));
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(color(text));
    }

    public String replace(String message, String key, String value) {
        return message.replace(key, value);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
