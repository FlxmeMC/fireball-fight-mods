package net.weavemc.mods.timer;

import java.util.regex.Pattern;

/** Recognizes only complete server-authored match lifecycle lines. */
final class MatchLifecycleParser {
    enum Signal {
        NONE,
        START,
        END
    }

    private static final Pattern COLOR_CODE = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private MatchLifecycleParser() {
    }

    static Signal parse(String formattedText) {
        if (formattedText == null) {
            return Signal.NONE;
        }
        String message = COLOR_CODE.matcher(formattedText).replaceAll("").trim();
        if (message.isEmpty() || message.startsWith("(From") || message.startsWith("(To")
                || message.contains(":") || message.startsWith("[")) {
            return Signal.NONE;
        }
        if ("Match started!".equalsIgnoreCase(message)) {
            return Signal.START;
        }
        if ("Match Results (Click to view)".equalsIgnoreCase(message)) {
            return Signal.END;
        }
        int separator = message.indexOf(' ');
        if (separator <= 0 || !PLAYER_NAME.matcher(message.substring(0, separator)).matches()) {
            return Signal.NONE;
        }
        String suffix = message.substring(separator + 1);
        return "forfeited.".equalsIgnoreCase(suffix) || "disconnected.".equalsIgnoreCase(suffix)
                ? Signal.END
                : Signal.NONE;
    }
}
