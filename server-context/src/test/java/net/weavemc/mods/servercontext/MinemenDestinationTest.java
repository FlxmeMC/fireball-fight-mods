package net.weavemc.mods.servercontext;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.naming.NamingException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MinemenDestinationTest {
    private static final class FakeDns implements MinemenDestination.Dns {
        final Map<String, Set<String>> records = new HashMap<String, Set<String>>();
        void put(String host, String type, String... values) {
            records.put(host + " " + type, new HashSet<String>(Arrays.asList(values)));
        }
        @Override public Set<String> records(String host, String type) throws NamingException {
            Set<String> found = records.get(host + " " + type);
            return found == null ? Collections.<String>emptySet() : found;
        }
    }
    private static InetSocketAddress remote(String address, int port) throws Exception {
        return new InetSocketAddress(InetAddress.getByName(address), port);
    }
    @Test public void acceptsUnknownAliasSharingARotatingCanonicalPool() throws Exception {
        FakeDns dns = new FakeDns();
        dns.put("na.minemen.club", "CNAME", "pool.example.");
        dns.put("pool.example", "A", "192.0.2.1");
        dns.put("new-alias.example", "CNAME", "pool.example.");
        // Connected IP is different from the current round-robin answer.
        assertTrue(new MinemenDestination().matches("NEW-ALIAS.EXAMPLE.:25565", remote("192.0.2.99", 25565), dns, 0));
    }
    @Test public void acceptsNumericDestinationWithoutKnowingItsAlias() throws Exception {
        FakeDns dns = new FakeDns();
        dns.put("eu.minemen.club", "A", "192.0.2.2");
        assertTrue(new MinemenDestination().matches("unknown.example", remote("192.0.2.2", 25565), dns, 0));
        assertFalse(new MinemenDestination().matches("unknown.example", remote("192.0.2.3", 25565), dns, 0));
        assertFalse(new MinemenDestination().matches("unknown.example", remote("192.0.2.2", 25566), dns, 0));
    }
    @Test public void followsMinecraftSrvInsteadOfWebsiteAddresses() throws Exception {
        FakeDns dns = new FakeDns();
        dns.put("minemen.club", "A", "192.0.2.10");
        dns.put("_minecraft._tcp.minemen.club", "SRV", "0 5 31000 game.example.");
        dns.put("game.example", "A", "192.0.2.11");
        dns.put("_minecraft._tcp.new-alias.example", "SRV", "0 5 31000 game.example.");
        MinemenDestination resolver = new MinemenDestination();
        assertTrue(resolver.matches("new-alias.example", remote("192.0.2.11", 31000), dns, 0));
        assertFalse(resolver.matches(null, remote("192.0.2.10", 25565), dns, 0));
        dns.records.remove("_minecraft._tcp.minemen.club SRV");
        assertFalse(new MinemenDestination().matches(null, remote("192.0.2.10", 25565), dns, 0));
    }
    @Test public void refreshExpiresOldIpAndFailuresDoNotEnableUnknownServers() throws Exception {
        FakeDns dns = new FakeDns();
        dns.put("na.minemen.club", "A", "192.0.2.1");
        MinemenDestination resolver = new MinemenDestination();
        assertTrue(resolver.matches(null, remote("192.0.2.1", 25565), dns, 0));
        dns.put("na.minemen.club", "A", "192.0.2.2");
        assertFalse(resolver.matches(null, remote("192.0.2.1", 25565), dns, 300_001));
        assertTrue(resolver.matches(null, remote("192.0.2.2", 25565), dns, 300_002));
        assertFalse(new MinemenDestination().matches("unknown.example", remote("192.0.2.5", 25565), new FakeDns(), 0));
        assertFalse(resolver.matches(null, InetSocketAddress.createUnresolved("unknown.example", 25565), dns, 0));
    }
    @Test public void supportsIpv6AndBoundsCanonicalCycles() throws Exception {
        FakeDns dns = new FakeDns();
        dns.put("as.minemen.club", "AAAA", "2001:db8::1");
        assertTrue(new MinemenDestination().matches("[2001:db8::1]:25565", remote("2001:db8::1", 25565), dns, 0));
        dns.put("loop.example", "CNAME", "loop2.example.");
        dns.put("loop2.example", "CNAME", "loop.example.");
        assertFalse(new MinemenDestination().matches("loop.example", remote("192.0.2.99", 25565), dns, 0));
    }
}
