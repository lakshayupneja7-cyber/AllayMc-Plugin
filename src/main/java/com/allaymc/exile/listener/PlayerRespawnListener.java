package com.allaymc.exile.listener;

import com.allaymc.exile.AllayMcPlugin;
import com.allaymc.exile.service.ExileService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerRespawnListener implements Listener {

    private final AllayMcPlugin plugin;
    private final ExileService exileService;

    public PlayerRespawnListener(AllayMcPlugin plugin, ExileService exileService, Object ignored) {
        this.plugin = plugin;
        this.exileService = exileService;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            exileService.applyExileStateIfNeeded(event.getPlayer());
        }, 1L);
    }
}
