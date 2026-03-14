package com.allaymc.exile.listener;

import com.allaymc.exile.AllayMcPlugin;
import com.allaymc.exile.data.PlayerDataManager;
import com.allaymc.exile.service.ExileService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final AllayMcPlugin plugin;
    private final ExileService exileService;
    private final PlayerDataManager playerDataManager;

    public PlayerJoinQuitListener(AllayMcPlugin plugin, ExileService exileService, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.exileService = exileService;
        this.playerDataManager = playerDataManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            exileService.applyExileStateIfNeeded(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("settings.save-on-quit", true)) {
            exileService.savePlayerStateOnQuit(event.getPlayer());
            playerDataManager.save(event.getPlayer().getUniqueId());
        }
    }
}
