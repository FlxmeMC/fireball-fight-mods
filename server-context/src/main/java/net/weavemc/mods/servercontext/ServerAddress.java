package net.weavemc.mods.servercontext;

import java.util.Locale;

/** Exact DNS-label matching, never a substring/MOTD-based server check. */
public final class ServerAddress {
    private ServerAddress() { }

    public static boolean isMinemen(String address) {
        if (address == null) return false;
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            String port = host.substring(colon + 1);
            if (!port.matches("[0-9]{1,5}")) return false;
            int value = Integer.parseInt(port);
            if (value < 1 || value > 65535) return false;
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.length() > 253 || !host.matches("[a-z0-9-]+(\\.[a-z0-9-]+)*")) return false;
        for (String label : host.split("\\.")) {
            if (label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return false;
        }
        return host.equals("minemen.club") || host.endsWith(".minemen.club");
    }
}
