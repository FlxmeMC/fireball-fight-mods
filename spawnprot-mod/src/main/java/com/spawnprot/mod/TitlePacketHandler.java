package com.spawnprot.mod;

import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.IChatComponent;

public final class TitlePacketHandler {
    public void onPacketReceive(S45PacketTitle packet, long receivedAtMs) {
        if (packet == null || packet.getType() == null) {
            return;
        }
        IChatComponent message = packet.getMessage();
        if (message == null) {
            return;
        }

        String text = message.getFormattedText();
        String typeName = packet.getType().name();
        if ("TITLE".equals(typeName) && text.contains("YOU DIED!")) {
            SpawnProtState.onSelfDeathCanRespawn(receivedAtMs);
        } else if ("SUBTITLE".equals(typeName)) {
            handleTitleSubtitle(text, receivedAtMs);
        }
    }

    static void handleTitleSubtitle(String subtitleText) {
        handleTitleSubtitle(subtitleText, System.currentTimeMillis());
    }

    static void handleTitleSubtitle(String subtitleText, long receivedAtMs) {
        if (subtitleText == null) {
            return;
        }
        String stripped = SpawnProtState.stripColorCodes(subtitleText);
        if (stripped.contains("Now spectating")) {
            SpawnProtState.onSelfDeathFinal();
        } else if (stripped.contains("Respawning in")) {
            SpawnProtState.onSelfDeathCanRespawn(receivedAtMs);
        }
    }
}
