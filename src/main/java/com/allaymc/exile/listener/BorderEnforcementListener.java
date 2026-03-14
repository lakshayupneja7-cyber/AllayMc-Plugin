package com.allaymc.exile.listener;

import com.allaymc.exile.service.ExileService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class BorderEnforcementListener implements Listener {

    private final ExileService exileService;

    public BorderEnforcementListener(ExileService exileService) {
        this.exileService = exileService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getWorld() == null || to.getWorld() == null) return;
        if (!from.getWorld().equals(to.getWorld())) return;

        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        boolean exiled = exileService.isExiled(player.getUniqueId());

        boolean fromInsideMain = exileService.isInsideMainBorder(from);
        boolean toInsideMain = exileService.isInsideMainBorder(to);

        boolean fromOutsideExile = exileService.isOutsideExileBorder(from);
        boolean toOutsideExile = exileService.isOutsideExileBorder(to);

        // Normal players cannot leave the main border
        if (!exiled && fromInsideMain && !toInsideMain) {
            event.setCancelled(true);
            exileService.showMainBorderHit(player, to);
            return;
        }

        // Normal players also cannot enter exile lands from the danger zone
        if (!exiled && !fromOutsideExile && toOutsideExile) {
            event.setCancelled(true);
            exileService.showExileBorderHit(player, to, false);
            return;
        }

        // Exiled players cannot cross back inward through the exile border
        if (exiled && fromOutsideExile && !toOutsideExile) {
            event.setCancelled(true);
            exileService.showExileBorderHit(player, to, true);
            return;
        }

        // If an exiled player somehow gets into the danger zone, do not let them enter the normal safe zone
        if (exiled && !fromInsideMain && toInsideMain) {
            event.setCancelled(true);
            exileService.showMainBorderHit(player, to);
        }
    }
}
