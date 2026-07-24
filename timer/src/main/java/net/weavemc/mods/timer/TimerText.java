package net.weavemc.mods.timer;

import java.util.Locale;

/** Formats the large clock and smaller millisecond suffix independently. */
final class TimerText {
    private final String clock;
    private final String decimals;

    private TimerText(String clock, String decimals) {
        this.clock = clock;
        this.decimals = decimals;
    }

    static TimerText fromMillis(long elapsedMillis) {
        long safeMillis = Math.max(0L, elapsedMillis);
        long totalSeconds = safeMillis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        String clock = hours > 0L
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        return new TimerText(clock, String.format(Locale.ROOT, ".%03d", safeMillis % 1000L));
    }

    String getClock() {
        return clock;
    }

    String getDecimals() {
        return decimals;
    }

    String plainText() {
        return clock + decimals;
    }
}
