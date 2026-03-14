package com.allaymc.exile.command;

import com.allaymc.exile.AllayMcPlugin;
import com.allaymc.exile.data.ExileData;
import com.allaymc.exile.data.PlayerDataManager;
import com.allaymc.exile.service.ExileService;
import com.allaymc.exile.util.MessageUtil;
import com.allaymc.exile.util.TimeUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ExileCommand implements CommandExecutor, TabCompleter {

    private final AllayMcPlugin plugin;
    private final ExileService exileService;
    private final PlayerDataManager playerDataManager;
    private final MessageUtil messageUtil;

    public ExileCommand(AllayMcPlugin plugin, ExileService exileService, PlayerDataManager playerDataManager, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.exileService = exileService;
        this.playerDataManager = playerDataManager;
        this.messageUtil = messageUtil;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "exile" -> handleExile(sender, args);
            case "exileadd" -> handleExileAdd(sender, args);
            case "exilefree" -> handleExileFree(sender, args);
            case "exileextend" -> handleExileExtend(sender, args);
            case "exileremove" -> handleExileRemove(sender, args);
            case "exilecount" -> handleExileCount(sender, args);
            case "serverborder" -> handleServerBorder(sender, args);
            default -> false;
        };
    }

    private boolean handleExile(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messageUtil.send(sender, "player-only");
                return true;
            }

            ExileData data = playerDataManager.getData(player.getUniqueId());
            if (!data.isExiled()) {
                messageUtil.send(player, "self-status-inactive");
                messageUtil.sendRaw(player, messageUtil.replace(messageUtil.get("self-exile-count"), "%count%", String.valueOf(data.getExileCount())));
                return true;
            }

            long remaining = exileService.getRemaining(player.getUniqueId());
            messageUtil.send(player, "self-status-active");
            messageUtil.sendRaw(player, messageUtil.replace(messageUtil.get("self-time-left"), "%time%", TimeUtil.formatDuration(remaining)));
            messageUtil.sendRaw(player, messageUtil.replace(messageUtil.get("self-exile-count"), "%count%", String.valueOf(data.getExileCount())));
            messageUtil.sendRaw(player, messageUtil.replace(messageUtil.get("self-reason"), "%reason%", data.getReason()));

            Location loc = exileService.getSavedExileLocation(player.getUniqueId());
            if (loc != null) {
                String msg = messageUtil.get("self-center")
                        .replace("%x%", String.valueOf(loc.getBlockX()))
                        .replace("%z%", String.valueOf(loc.getBlockZ()));
                messageUtil.sendRaw(player, msg);
            }
            return true;
        }

        if (!sender.hasPermission("allaymc.exile.others")) {
            messageUtil.send(sender, "no-permission");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        ExileData data = playerDataManager.getData(target.getUniqueId());
        String targetName = target.getName() == null ? args[0] : target.getName();

        if (!data.isExiled()) {
            messageUtil.sendRaw(sender, messageUtil.replace(messageUtil.get("others-status-inactive"), "%player%", targetName));
            messageUtil.sendRaw(sender, messageUtil.replace(
                    messageUtil.replace(messageUtil.get("others-exile-count"), "%player%", targetName),
                    "%count%", String.valueOf(data.getExileCount())
            ));
            return true;
        }

        long remaining = exileService.getRemaining(target.getUniqueId());

        messageUtil.sendRaw(sender, messageUtil.replace(messageUtil.get("others-status-active"), "%player%", targetName));
        messageUtil.sendRaw(sender, messageUtil.replace(
                messageUtil.replace(messageUtil.get("others-time-left"), "%player%", targetName),
                "%time%", TimeUtil.formatDuration(remaining)
        ));
        messageUtil.sendRaw(sender, messageUtil.replace(
                messageUtil.replace(messageUtil.get("others-exile-count"), "%player%", targetName),
                "%count%", String.valueOf(data.getExileCount())
        ));
        messageUtil.sendRaw(sender, messageUtil.replace(
                messageUtil.replace(messageUtil.get("others-reason"), "%player%", targetName),
                "%reason%", data.getReason()
        ));

        return true;
    }

    private boolean handleExileAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("allaymc.exile.add")) {
            messageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            messageUtil.send(sender, "usage-exileadd");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messageUtil.send(sender, "player-not-found");
            return true;
        }

        if (exileService.isExiled(target.getUniqueId())) {
            messageUtil.send(sender, "already-exiled");
            return true;
        }

        long duration = TimeUtil.parseTimeToMillis(args[1]);
        if (duration <= 0) {
            messageUtil.send(sender, "invalid-time");
            return true;
        }

        String reason = args.length >= 3
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "No reason provided";

        exileService.exilePlayer(target, duration, reason);

        String time = TimeUtil.formatDuration(duration);
        messageUtil.sendRaw(sender, messageUtil.replace(
                messageUtil.replace(messageUtil.get("staff-exiled-player"), "%player%", target.getName()),
                "%time%", time
        ));

        return true;
    }

    private boolean handleExileFree(CommandSender sender, String[] args) {
        if (!sender.hasPermission("allaymc.exile.free")) {
            messageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length != 1) {
            messageUtil.send(sender, "usage-exilefree");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messageUtil.send(sender, "player-not-found");
            return true;
        }

        if (!exileService.isExiled(target.getUniqueId())) {
            messageUtil.send(sender, "not-exiled");
            return true;
        }

        exileService.freePlayer(target, false);
        messageUtil.sendRaw(sender, messageUtil.replace(messageUtil.get("staff-freed-player"), "%player%", target.getName()));
        return true;
    }

    private boolean handleExileExtend(CommandSender sender, String[] args) {
        if (!sender.hasPermission("allaymc.exile.extend")) {
            messageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length != 2) {
            messageUtil.send(sender, "usage-exileextend");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messageUtil.send(sender, "player-not-found");
            return true;
        }

        if (!exileService.isExiled(target.getUniqueId())) {
            messageUtil.send(sender, "not-exiled");
            return true;
        }

        long extra = TimeUtil.parseTimeToMillis(args[1]);
        if (extra <= 0) {
            messageUtil.send(sender, "invalid-time");
            return true;
        }

        exileService.extendPlayer(target, extra);
        messageUtil.sendRaw(sender, messageUtil.replace(
                messageUtil.replace(messageUtil.get("staff-exile-extended"), "%player%", target.getName()),
                "%time%", TimeUtil.formatDuration(extra)
        ));
        return true;
    }

    private boolean handleExileRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("allaymc.exile.remove")) {
            messageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length != 1) {
            messageUtil.send(sender, "usage-exileremove");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null) {
            exileService.removePlayerPermanently(target);
            messageUtil.sendRaw(sender, messageUtil.replace(messageUtil.get("staff-removed-player"), "%player%", target.getName()));
            return true;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        String mode = plugin.getConfig().getString("punishments.exileremove.mode", "kick").toLowerCase();

        switch (mode) {
            case "ban" -> Bukkit.getBanList(BanList.Type.NAME).addBan(
                    offline.getName(),
                    plugin.getConfig().getString("punishments.exileremove.ban-reason", "Removed from server."),
                    null,
                    "AllayMc"
            );
            case "whitelist-remove" -> offline.setWhitelisted(false);
            default -> {
                messageUtil.send(sender, "player-not-found");
                return true;
            }
        }

        messageUtil.sendRaw(sender, messageUtil.replace(messageUtil.get("staff-removed-player"), "%player%", offline.getName() == null ? args[0] : offline.getName()));
        return true;
    }

    private boolean handleExileCount(CommandSender sender, String[] args) {
        if (!sender.hasPermission("allaymc.exile.count")) {
            messageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length != 1) {
            messageUtil.send(sender, "usage-exilecount");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        ExileData data = playerDataManager.getData(target.getUniqueId());

        messageUtil.sendRaw(sender, messageUtil.replace(
                messageUtil.replace(messageUtil.get("staff-count-message"), "%player%", target.getName() == null ? args[0] : target.getName()),
                "%count%", String.valueOf(data.getExileCount())
        ));
        return true;
    }

    private boolean handleServerBorder(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission("allaymc.serverborder.view")) {
                messageUtil.send(sender, "no-permission");
                return true;
            }

            double blocks = exileService.getGlobalServerBorderSize();
            messageUtil.sendRaw(sender, messageUtil.get("server-border-info")
                    .replace("%blocks%", stripDecimal(blocks))
                    .replace("%chunks%", stripDecimal(blocks / 16.0)));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "set" -> {
                if (!sender.hasPermission("allaymc.serverborder.set")) {
                    messageUtil.send(sender, "no-permission");
                    return true;
                }
                if (args.length != 2) {
                    messageUtil.send(sender, "usage-serverborder");
                    return true;
                }

                double value = parsePositiveDouble(args[1]);
                if (value <= 0) {
                    messageUtil.send(sender, "invalid-number");
                    return true;
                }

                exileService.setGlobalServerBorderSize(value);
                messageUtil.sendRaw(sender, messageUtil.get("server-border-set")
                        .replace("%blocks%", stripDecimal(value)));
                return true;
            }

            case "add" -> {
                if (!sender.hasPermission("allaymc.serverborder.add")) {
                    messageUtil.send(sender, "no-permission");
                    return true;
                }
                if (args.length != 2) {
                    messageUtil.send(sender, "usage-serverborder");
                    return true;
                }

                double value = parsePositiveDouble(args[1]);
                if (value <= 0) {
                    messageUtil.send(sender, "invalid-number");
                    return true;
                }

                exileService.addGlobalServerBorderSize(value);
                double after = exileService.getGlobalServerBorderSize();

                messageUtil.sendRaw(sender, messageUtil.get("server-border-added")
                        .replace("%blocks%", stripDecimal(value))
                        .replace("%new_size%", stripDecimal(after)));
                return true;
            }

            case "chunks" -> {
                if (!sender.hasPermission("allaymc.serverborder.view")) {
                    messageUtil.send(sender, "no-permission");
                    return true;
                }

                double blocks = exileService.getGlobalServerBorderSize();
                messageUtil.sendRaw(sender, "&eServer border: &6" + stripDecimal(blocks / 16.0) + " chunks");
                return true;
            }

            default -> {
                messageUtil.send(sender, "usage-serverborder");
                return true;
            }
        }
    }

    private double parsePositiveDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String stripDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("serverborder")) {
            if (args.length == 1) {
                return List.of("set", "add", "chunks").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (args.length == 2 && List.of("set", "add").contains(args[0].toLowerCase(Locale.ROOT))) {
                return List.of("6400", "8000", "16000", "32000");
            }
        }

        if (args.length == 1 && List.of("exile", "exileadd", "exilefree", "exileextend", "exileremove", "exilecount").contains(cmd)) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if ((cmd.equals("exileadd") || cmd.equals("exileextend")) && args.length == 2) {
            return new ArrayList<>(List.of("30m", "1h", "12h", "1d", "7d"));
        }

        return List.of();
    }
}
