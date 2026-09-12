package net.weavemc.mods.servercontext;

/** Immutable token: queued effects belong to exactly one connection/world/permission state. */
final class ConnectionSession {
    final Object connection;
    final Object world;
    final boolean allowed;

    ConnectionSession(Object connection, Object world, boolean allowed) {
        this.connection = connection;
        this.world = world;
        this.allowed = allowed && connection != null && world != null;
    }

    ConnectionSession update(Object nextConnection, Object nextWorld, boolean nextAllowed) {
        boolean effective = nextAllowed && nextConnection != null && nextWorld != null;
        return connection == nextConnection && world == nextWorld && allowed == effective
                ? this : new ConnectionSession(nextConnection, nextWorld, effective);
    }

    boolean permits(Object liveConnection, Object liveWorld) {
        return allowed && connection == liveConnection && world == liveWorld;
    }
}
