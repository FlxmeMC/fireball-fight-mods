package net.weavemc.mods.servercontext;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/** Optional manual live-DNS check; never part of the deterministic test suite. */
public final class DestinationProbe {
    private DestinationProbe() { }
    public static void main(String[] args) throws Exception {
        MinemenDestination resolver = new MinemenDestination();
        for (String argument : args) {
            boolean expected = !argument.startsWith("!");
            String host = expected ? argument : argument.substring(1);
            boolean accepted = resolver.matches(host, new InetSocketAddress(InetAddress.getByName(host), 25565));
            System.out.println(host + " recognized=" + accepted);
            if (accepted != expected) throw new AssertionError("Unexpected destination result: " + host);
        }
    }
}
