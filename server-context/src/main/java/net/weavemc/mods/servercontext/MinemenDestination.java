package net.weavemc.mods.servercontext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/** Resolves official Minecraft endpoints, including SRV and rotating CNAME pools. Called off-thread. */
final class MinemenDestination {
    private static final String[] OFFICIAL_HOSTS = {
        "minemen.club", "geo.minemen.club", "na.minemen.club", "eu.minemen.club", "as.minemen.club"
    };
    private Set<String> trusted = Collections.emptySet();
    private long trustedUntil;

    interface Dns {
        Set<String> records(String host, String type) throws NamingException;
    }

    synchronized boolean matches(String enteredAddress, SocketAddress remoteAddress) {
        if (!(remoteAddress instanceof InetSocketAddress)) return false;
        InetSocketAddress remote = (InetSocketAddress) remoteAddress;
        if (remote.isUnresolved()) return false;
        Hashtable<String, String> environment = new Hashtable<String, String>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "1000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = null;
        try {
            context = new InitialDirContext(environment);
            final DirContext dnsContext = context;
            Dns dns = new Dns() {
                @Override public Set<String> records(String host, String type) throws NamingException {
                    Attributes attributes = dnsContext.getAttributes(host, new String[] {type});
                    Attribute attribute = attributes.get(type);
                    Set<String> values = new HashSet<String>();
                    if (attribute != null) {
                        NamingEnumeration<?> entries = attribute.getAll();
                        try { while (entries.hasMore()) values.add(entries.next().toString()); }
                        finally { entries.close(); }
                    }
                    return values;
                }
            };
            return matches(enteredAddress, remote, dns, System.currentTimeMillis());
        } catch (NamingException exception) {
            return false;
        } finally {
            if (context != null) try { context.close(); } catch (NamingException ignored) { }
        }
    }

    boolean matches(String enteredAddress, InetSocketAddress remote, Dns dns, long now) {
        if (remote.isUnresolved()) return false;
        if (now >= trustedUntil || trusted.isEmpty()) {
            Set<String> refreshed = new HashSet<String>();
            for (String host : OFFICIAL_HOSTS) refreshed.addAll(resolve(host, 25565, dns));
            // Never reuse old DNS indefinitely after a failed refresh.
            trusted = refreshed;
            trustedUntil = now + (refreshed.isEmpty() ? 10_000L : 300_000L);
        }
        if (trusted.contains(ipKey(remote.getAddress().getHostAddress(), remote.getPort()))) return true;
        HostPort entered = parse(enteredAddress);
        if (entered == null) return false;
        Set<String> candidate = resolve(entered.host, entered.port, dns);
        candidate.retainAll(trusted);
        String portSuffix = ":" + remote.getPort();
        for (String identity : candidate) if (identity.endsWith(portSuffix)) return true;
        return false;
    }

    private static Set<String> resolve(String host, int port, Dns dns) {
        Set<String> identities = new HashSet<String>();
        Set<String> serviceRecords = port == 25565 ? lookup(dns, "_minecraft._tcp." + host, "SRV") : Collections.<String>emptySet();
        // The root domain's A/AAAA records serve the website, not the Minecraft server.
        if (host.equals("minemen.club") && serviceRecords.isEmpty()) return identities;
        if (!serviceRecords.isEmpty()) {
            for (String record : serviceRecords) {
                String[] parts = record.trim().split("\\s+");
                if (parts.length != 4) continue;
                try {
                    int servicePort = Integer.parseInt(parts[2]);
                    if (servicePort > 0 && servicePort <= 65535 && !parts[3].equals(".")) {
                        resolveHost(normalize(parts[3]), servicePort, dns, identities, new HashSet<String>());
                    }
                } catch (NumberFormatException ignored) { }
            }
        } else {
            resolveHost(host, port, dns, identities, new HashSet<String>());
        }
        return identities;
    }

    private static void resolveHost(String host, int port, Dns dns, Set<String> result, Set<String> visited) {
        if (visited.size() >= 8 || !visited.add(host)) return;
        Set<String> aliases = lookup(dns, host, "CNAME");
        Set<String> addresses = lookup(dns, host, "A");
        addresses.addAll(lookup(dns, host, "AAAA"));
        // Only a host with DNS evidence contributes a canonical identity.
        if (!aliases.isEmpty() || !addresses.isEmpty()) result.add("dns:" + host + ":" + port);
        for (String address : addresses) result.add(ipKey(address, port));
        for (String alias : aliases) resolveHost(normalize(alias), port, dns, result, visited);
    }

    private static Set<String> lookup(Dns dns, String host, String type) {
        try { return new HashSet<String>(dns.records(host, type)); }
        catch (NamingException exception) { return new HashSet<String>(); }
    }

    private static String ipKey(String address, int port) {
        try { return "ip:" + java.net.InetAddress.getByName(address).getHostAddress() + ":" + port; }
        catch (java.net.UnknownHostException exception) { return "invalid"; }
    }

    private static String normalize(String host) {
        String result = host.trim().toLowerCase(Locale.ROOT);
        return result.endsWith(".") ? result.substring(0, result.length() - 1) : result;
    }

    private static HostPort parse(String address) {
        if (address == null) return null;
        String host = address.trim();
        int port = 25565;
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end < 0) return null;
            if (end + 1 < host.length()) {
                if (host.charAt(end + 1) != ':') return null;
                try { port = Integer.parseInt(host.substring(end + 2)); }
                catch (NumberFormatException exception) { return null; }
            }
            host = host.substring(1, end);
        } else if (host.indexOf(':') == host.lastIndexOf(':') && host.contains(":")) {
            int colon = host.indexOf(':');
            try { port = Integer.parseInt(host.substring(colon + 1)); }
            catch (NumberFormatException exception) { return null; }
            host = host.substring(0, colon);
        }
        host = normalize(host);
        if (host.isEmpty() || port < 1 || port > 65535 || !host.matches("[a-z0-9.:-]+")) return null;
        return new HostPort(host, port);
    }

    private static final class HostPort {
        final String host;
        final int port;
        HostPort(String host, int port) { this.host = host; this.port = port; }
    }
}
