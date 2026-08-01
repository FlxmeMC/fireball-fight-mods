package com.spawnprot.mod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DeathChatHandlerTest {
    @Test
    public void acceptsStandaloneServerDeathMessages() {
        assertEquals("xFlxme", DeathChatHandler.serverDeathPlayer(
                "xFlxme was hit into the void by Skablonk!", "Skablonk"));
        assertEquals("Alex", DeathChatHandler.serverDeathPlayer(
                "\u00A7cAlex was knocked into the void by Steve!", "Steve"));
        assertEquals("Skablonk", DeathChatHandler.serverDeathPlayer(
                "Skablonk was killed by xFlxme! FINAL KILL", "xFlxme"));
    }

    @Test
    public void rejectsPlayerAuthoredMessagesAndLocalDeaths() {
        assertNull(DeathChatHandler.serverDeathPlayer("[xFlxme] Alex died!", null));
        assertNull(DeathChatHandler.serverDeathPlayer(
                "[Famous] xFlxme AURA: Alex died!", null));
        assertNull(DeathChatHandler.serverDeathPlayer("Alex: Steve died!", null));
        assertNull(DeathChatHandler.serverDeathPlayer("Alex died! nice try", null));
        assertNull(DeathChatHandler.serverDeathPlayer("Steve died!", "steve"));
    }

    @Test
    public void preservesVictimTeamColorFromServerFormatting() {
        DeathChatHandler.DeathInfo red = DeathChatHandler.serverDeathInfo(
                "\u00A7cAlex was killed by \u00A79Steve!", "Steve");
        DeathChatHandler.DeathInfo blue = DeathChatHandler.serverDeathInfo(
                "\u00A79Jamie fell into the void!", "Steve");

        assertEquals("Alex", red.playerName);
        assertEquals("c", red.colorCode);
        assertEquals("Jamie", blue.playerName);
        assertEquals("9", blue.colorCode);
    }

    @Test
    public void treatsBrightAndDarkVariantsAsTheSameTeamColor() {
        assertTrue(DeathChatHandler.sameTeamColor("c", "4"));
        assertTrue(DeathChatHandler.sameTeamColor("9", "1"));
        assertFalse(DeathChatHandler.sameTeamColor("c", "9"));
        assertFalse(DeathChatHandler.sameTeamColor(null, "9"));
    }
}
