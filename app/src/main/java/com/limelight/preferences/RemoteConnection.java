package com.limelight.preferences;

import android.content.SharedPreferences;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * A stream to a PC outside this network (the internet, a VPN such as Tailscale) uses its own bitrate, which starts at
 * 1.5 Mbps. Only the address the stream uses decides: a private, link-local or loopback
 * address, a .local name or a bare host name is this network; any other address or name is remote. Names are never
 * looked up, so this costs nothing at launch.
 */
public final class RemoteConnection {
    public static final String BITRATE_PREF = "seekbar_remote_bitrate_kbps";
    public static final int DEFAULT_BITRATE_KBPS = 1500;
    private static final int MIN_BITRATE_KBPS = 500;
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private RemoteConnection() { }

    public static boolean isRemote(String host) {
        if (host == null) return false;
        String h = host.trim();
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (h.isEmpty()) return false;
        boolean literal = IPV4.matcher(h).matches() || h.indexOf(':') >= 0;
        if (!literal) {
            String name = h.toLowerCase(java.util.Locale.ROOT);
            return name.indexOf('.') >= 0 && !name.endsWith(".local") && !name.endsWith(".lan") && !name.endsWith(".home.arpa");
        }
        try {
            InetAddress address = InetAddress.getByName(h);
            if (address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()) return false;
            // IPv6 unique local addresses (fc00::/7) are this network's too.
            return !(address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC);
        } catch (Exception e) {
            return false;
        }
    }

    public static int bitrateKbps(SharedPreferences prefs) {
        return Math.max(MIN_BITRATE_KBPS, prefs.getInt(BITRATE_PREF, DEFAULT_BITRATE_KBPS));
    }
}
