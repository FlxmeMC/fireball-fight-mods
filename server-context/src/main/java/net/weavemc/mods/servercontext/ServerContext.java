package net.weavemc.mods.servercontext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.ChatComponentText;
import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ExecutionException;
import net.weavemc.api.event.ShutdownEvent;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;

/** Shared, fail-closed session gate. No DNS/network operations on render threads. */
public final class ServerContext implements ModInitializer {
    public static final String DISABLED_MESSAGE = "Fireball Fight mods are disabled outside Minemen Club.";
    private static volatile ConnectionSession current = new ConnectionSession(null, null, false);
    private NetHandlerPlayClient warnedConnection;
    private NetHandlerPlayClient lookupConnection;
    private final MinemenDestination destinations = new MinemenDestination();
    private final ExecutorService resolver = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Minemen destination lookup");
            thread.setDaemon(true);
            return thread;
        }
    });
    private Future<Boolean> lookup;
    private boolean destinationAllowed;
    private boolean lookupFinished;
    private long joinedAt;
    private long nextLookupAt;

    @Override public void init() { EventBus.subscribe(this); }

    public static boolean isAllowed() {
        ConnectionSession session = current;
        if (!session.allowed) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return false;
        NetHandlerPlayClient connection = mc.getNetHandler();
        return session.permits(connection, mc.theWorld) && connection != null
                && connection.getNetworkManager().isChannelOpen() && !mc.isSingleplayer();
    }

    public static Object connectionId() { return current; }

    /** Called at execution time too, so packets queued in a previous world cannot take effect. */
    public static void runOnClientThread(final Runnable action) {
        final Object session = connectionId();
        if (!isAllowed() || session != connectionId()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        Runnable guarded = new Runnable() {
            @Override public void run() {
                if (session == connectionId() && isAllowed()) action.run();
            }
        };
        if (mc.isCallingFromMinecraftThread()) guarded.run();
        else mc.addScheduledTask(guarded);
    }

    public static void explainDisabled() {
        if (current.connection == null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        mc.addScheduledTask(new Runnable() {
            @Override public void run() {
                if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(DISABLED_MESSAGE));
            }
        });
    }

    @SubscribeEvent public void onTick(TickEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        final NetHandlerPlayClient connection = mc == null ? null : mc.getNetHandler();
        Object world = mc == null ? null : mc.theWorld;
        ServerData data = mc == null ? null : mc.getCurrentServerData();
        long now = System.nanoTime();
        if (lookupConnection != connection) {
            if (lookup != null) lookup.cancel(true);
            lookupConnection = connection;
            lookup = null;
            destinationAllowed = false;
            lookupFinished = false;
            warnedConnection = null;
            joinedAt = now;
            nextLookupAt = now;
        }
        boolean multiplayer = connection != null && world != null && mc.thePlayer != null
                && !mc.isSingleplayer() && connection.getNetworkManager().isChannelOpen();
        boolean officialHost = data != null && ServerAddress.isMinemen(data.serverIP);
        if (multiplayer && !officialHost && !destinationAllowed) {
            if (lookup != null && lookup.isDone()) {
                try { destinationAllowed = lookup.get().booleanValue(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                catch (ExecutionException exception) {
                    System.err.println("[Server Context] Destination lookup unavailable: " + exception.getCause().getClass().getSimpleName());
                }
                lookup = null;
                lookupFinished = true;
                nextLookupAt = now + 30_000_000_000L;
            }
            if (!destinationAllowed && lookup == null && now >= nextLookupAt) {
                final String enteredAddress = data == null ? null : data.serverIP;
                final SocketAddress remoteAddress = connection.getNetworkManager().getRemoteAddress();
                lookup = resolver.submit(new Callable<Boolean>() {
                    @Override public Boolean call() {
                        return Boolean.valueOf(destinations.matches(enteredAddress, remoteAddress));
                    }
                });
            }
        }
        boolean allowed = multiplayer && (officialHost || destinationAllowed);
        current = current.update(connection, world, allowed);
        // Give DNS time to resolve; do not warn during login.
        if (multiplayer && !allowed && lookupFinished && now - joinedAt >= 5_000_000_000L
                && warnedConnection != connection) {
            warnedConnection = connection;
            mc.thePlayer.addChatMessage(new ChatComponentText(DISABLED_MESSAGE));
        }
    }

    @SubscribeEvent public void onShutdown(ShutdownEvent event) {
        resolver.shutdownNow();
    }
}
