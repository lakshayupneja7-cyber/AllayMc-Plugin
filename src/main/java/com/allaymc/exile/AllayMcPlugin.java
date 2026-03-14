package com.allaymc.exile;

import com.allaymc.exile.command.ExileCommand;
import com.allaymc.exile.data.PlayerDataManager;
import com.allaymc.exile.listener.BorderEnforcementListener;
import com.allaymc.exile.listener.PlayerJoinQuitListener;
import com.allaymc.exile.listener.PlayerRespawnListener;
import com.allaymc.exile.service.ExileService;
import com.allaymc.exile.util.MessageUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class AllayMcPlugin extends JavaPlugin {

    private static AllayMcPlugin instance;

    private PlayerDataManager playerDataManager;
    private ExileService exileService;
    private MessageUtil messageUtil;

    public static AllayMcPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResourceIfNotExists("messages.yml");

        this.messageUtil = new MessageUtil(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.exileService = new ExileService(this, playerDataManager, messageUtil);

        registerCommands();
        registerListeners();

        exileService.disableVanillaWorldBorder();
        exileService.startTimerTask();
        exileService.startDangerZoneTask();

        getLogger().info("AllayMc enabled.");
    }

    @Override
    public void onDisable() {
        if (exileService != null) {
            exileService.saveAllOnlineExileStates();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        getLogger().info("AllayMc disabled.");
    }

    private void registerCommands() {
        ExileCommand exileCommand = new ExileCommand(this, exileService, playerDataManager, messageUtil);

        bind("exile", exileCommand);
        bind("exileadd", exileCommand);
        bind("exilefree", exileCommand);
        bind("exileextend", exileCommand);
        bind("exileremove", exileCommand);
        bind("exilecount", exileCommand);
        bind("serverborder", exileCommand);
    }

    private void bind(String name, ExileCommand executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, exileService, playerDataManager), this
        );
        getServer().getPluginManager().registerEvents(
                new PlayerRespawnListener(this, exileService, playerDataManager), this
        );
        getServer().getPluginManager().registerEvents(
                new BorderEnforcementListener(exileService), this
        );
    }

    private void saveResourceIfNotExists(String path) {
        if (!new File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ExileService getExileService() {
        return exileService;
    }

    public MessageUtil getMessageUtil() {
        return messageUtil;
    }
}
