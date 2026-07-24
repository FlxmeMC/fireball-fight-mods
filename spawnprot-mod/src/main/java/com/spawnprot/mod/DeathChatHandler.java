package com.spawnprot.mod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeathChatHandler {
    private static final String PLAYER_NAME = "[A-Za-z0-9_]{3,16}";
    private static final Pattern DEATH_PATTERN = Pattern.compile(
            "^(" + PLAYER_NAME + ") (?:fell into the void"
                    + "|was (?:hit|knocked) into the void by " + PLAYER_NAME
                    + "|was killed by " + PLAYER_NAME
                    + "|died)[!.]?(?: FINAL KILL)?$",
            Pattern.CASE_INSENSITIVE);

    static final class DeathInfo {
        final String playerName;
        final String colorCode;

        DeathInfo(String playerName, String colorCode) {
            this.playerName = playerName;
            this.colorCode = colorCode;
        }
    }

    interface PlayerColorResolver {
        String colorFor(String playerName);
    }

    public void onChatMessage(String formattedText, String localPlayerName,
                              String localTeamColor, long receivedAtMs) {
        onChatMessage(formattedText, localPlayerName, localTeamColor, receivedAtMs, null);
    }

    void onChatMessage(String formattedText, String localPlayerName,
                       String localTeamColor, long receivedAtMs,
                       PlayerColorResolver colorResolver) {
        if (formattedText == null) {
            return;
        }
        if (SpawnProtState.isMatchEndMessage(formattedText)) {
            SpawnProtState.clearAll();
            return;
        }

        DeathInfo death = serverDeathInfo(formattedText, localPlayerName);
        if (death != null) {
            String resolvedTeamColor = localTeamColor;
            if (resolvedTeamColor == null && localPlayerName != null) {
                resolvedTeamColor = colorBeforePlayer(formattedText, localPlayerName);
            }
            if (resolvedTeamColor == null && colorResolver != null) {
                resolvedTeamColor = colorResolver.colorFor(localPlayerName);
            }
            String victimTeamColor = death.colorCode;
            if (colorResolver != null) {
                String entityColor = colorResolver.colorFor(death.playerName);
                if (entityColor != null) {
                    victimTeamColor = entityColor;
                }
            }
            boolean teammate = sameTeamColor(resolvedTeamColor, victimTeamColor);
            SpawnProtState.onPlayerDeath(death.playerName, teammate, receivedAtMs);
        }
    }

    static boolean sameTeamColor(String first, String second) {
        String firstTeam = canonicalTeamColor(first);
        return firstTeam != null && firstTeam.equals(canonicalTeamColor(second));
    }

    private static String canonicalTeamColor(String color) {
        if (color == null) {
            return null;
        }
        if ("4".equalsIgnoreCase(color) || "c".equalsIgnoreCase(color)) {
            return "red";
        }
        if ("1".equalsIgnoreCase(color) || "9".equalsIgnoreCase(color)) {
            return "blue";
        }
        return color.toLowerCase(java.util.Locale.ROOT);
    }

    static String serverDeathPlayer(String formattedText, String localPlayerName) {
        DeathInfo info = serverDeathInfo(formattedText, localPlayerName);
        return info == null ? null : info.playerName;
    }

    static DeathInfo serverDeathInfo(String formattedText, String localPlayerName) {
        if (formattedText == null) {
            return null;
        }
        String stripped = SpawnProtState.stripColorCodes(formattedText).trim();
        if (SpawnProtState.shouldIgnoreChat(stripped)) {
            return null;
        }
        Matcher matcher = DEATH_PATTERN.matcher(stripped);
        if (!matcher.matches()) {
            return null;
        }
        String playerName = matcher.group(1);
        if (localPlayerName != null && localPlayerName.equalsIgnoreCase(playerName)) {
            return null;
        }
        return new DeathInfo(playerName, colorBeforePlayer(formattedText, playerName));
    }

    static String colorBeforePlayer(String formattedText, String playerName) {
        Pattern playerPattern = Pattern.compile(
                "(?i)(?:^|\\s)((?:\\u00A7[0-9A-FK-OR])*)" + Pattern.quote(playerName)
                        + "(?=\\s|$|[!.,])");
        Matcher playerMatcher = playerPattern.matcher(formattedText);
        if (!playerMatcher.find()) {
            return null;
        }
        return lastColorCode(playerMatcher.group(1));
    }

    static String lastColorCode(String codes) {
        if (codes == null) {
            return null;
        }
        String color = null;
        for (int index = 0; index + 1 < codes.length(); index++) {
            if (codes.charAt(index) == '\u00A7') {
                char code = Character.toLowerCase(codes.charAt(index + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    color = String.valueOf(code);
                } else if (code == 'r') {
                    color = null;
                }
                index++;
            }
        }
        return color;
    }
}
