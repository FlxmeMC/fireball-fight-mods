package net.weavemc.mods.servercontext;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ServerAddressTest {
    @Test public void acceptsOfficialAddressesAndNormalizedVariants() {
        for (String host : new String[] {"minemen.club", "geo.minemen.club", "na.minemen.club",
                "eu.minemen.club", "as.minemen.club", "proxy.minemen.club", " GEO.MINEMEN.CLUB.:25565 "}) {
            assertTrue(host, ServerAddress.isMinemen(host));
        }
    }
    @Test public void rejectsLookalikesAndUnverifiedAddresses() {
        for (String host : new String[] {null, "", "localhost", "127.0.0.1", "[::1]:25565",
                "notminemen.club", "minemen.club.evil.example", "minemen.club@evil.example",
                "minemen.club/", ".minemen.club", "minemen..club", "-proxy.minemen.club",
                "minemen.club:0", "minemen.club:65536", "minemen.club:abc", "minemen.club.."}) {
            assertFalse(String.valueOf(host), ServerAddress.isMinemen(host));
        }
    }
}
