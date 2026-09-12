package net.weavemc.mods.servercontext;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ConnectionSessionTest {
    @Test public void reconnectAndWorldChangesInvalidateQueuedEffects() {
        Object connection = new Object(), world = new Object();
        ConnectionSession session = new ConnectionSession(connection, world, true);
        assertTrue(session.permits(connection, world));
        assertSame(session, session.update(connection, world, true));
        Object nextWorld = new Object();
        assertFalse(session.permits(connection, nextWorld));
        assertNotSame(session, session.update(connection, nextWorld, true));
        Object nextConnection = new Object();
        assertFalse(session.permits(nextConnection, world));
        assertNotSame(session, session.update(nextConnection, world, true));
    }
    @Test public void disconnectAndRevocationFailClosedWithoutReusingOldTokens() {
        Object connection = new Object(), world = new Object();
        ConnectionSession allowed = new ConnectionSession(connection, world, true);
        ConnectionSession disabled = allowed.update(connection, world, false);
        assertFalse(disabled.permits(connection, world));
        assertNotSame(allowed, disabled);
        assertNotSame(allowed, disabled.update(connection, world, true));
        assertFalse(allowed.update(null, world, true).allowed);
        assertFalse(allowed.update(connection, null, true).allowed);
        assertFalse(new ConnectionSession(null, null, false).permits(null, null));
    }
}
