package com.allaymc.exile.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");

    public static long parseTimeToMillis(String input) {
        if (input == null || input.isBlank()) return -1L;

        Matcher matcher = TIME_PATTERN.matcher(input.toLowerCase());
        long total = 0L;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "s" -> total += value * 1000L;
                case "m" -> total += value * 60_000L;
                case "h" -> total += value * 3_600_000L;
                case "d" -> total += value * 86_400_000L;
                default -> {
                    return -1L;
                }
            }
        }

        return found ? total : -1L;
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";

        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
