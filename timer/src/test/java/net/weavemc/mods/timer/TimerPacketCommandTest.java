package net.weavemc.mods.timer;

import net.minecraft.network.play.client.C01PacketChatMessage;
import net.weavemc.api.event.PacketEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TimerPacketCommandTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String originalUserHome;

    @Before
    public void useTemporaryUserHome() throws Exception {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryFolder.newFolder("home").getAbsolutePath());
    }

    @After
    public void restoreUserHome() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    public void consumesTimerAtOutgoingPacketButLeavesServerCommandsAlone() {
        TimerMod mod = new TimerMod();
        mod.init();

        PacketEvent.Send timer = new PacketEvent.Send(new C01PacketChatMessage("/timer"));
        mod.onPacketSend(timer);
        assertTrue(timer.isCancelled());

        PacketEvent.Send server = new PacketEvent.Send(new C01PacketChatMessage("/help"));
        mod.onPacketSend(server);
        assertFalse(server.isCancelled());
    }
}
