package com.shipshape.backend.util;

import com.shipshape.backend.exception.BadRequestException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class UrlValidator {

    public static URI validateAndParse(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new BadRequestException("URL is required");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new BadRequestException("Malformed URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new BadRequestException("Unsupported scheme");
        }
        scheme = scheme.toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BadRequestException("Unsupported scheme");
        }

        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            throw new BadRequestException("Credentials not allowed in URL");
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new BadRequestException("Invalid host");
        }
        String hostLower = host.toLowerCase();

        if (isLocalOrReservedHost(hostLower)) {
            throw new BadRequestException("Host not allowed");
        }

        // Try to resolve and validate all addresses
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw new BadRequestException("Host not allowed");
            }
            for (InetAddress addr : addresses) {
                if (isNonPublicAddress(addr)) {
                    throw new BadRequestException("Host not allowed");
                }
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Host not allowed");
        }

        return uri;
    }

    public static void validateAddress(InetAddress addr) {
        if (isNonPublicAddress(addr)) {
            throw new BadRequestException("Host not allowed");
        }
    }

    private static boolean isLocalOrReservedHost(String hostLower) {
        if (hostLower.equals("localhost") || hostLower.equals("localhost.localdomain")) {
            return true;
        }
        if (hostLower.endsWith(".local") || hostLower.endsWith(".localhost")) {
            return true;
        }
        // common metadata/internal
        if (hostLower.equals("metadata") || hostLower.equals("metadata.google.internal")) {
            return true;
        }
        if (hostLower.contains("instance-data") || hostLower.contains("metadata")) {
            return true;
        }
        // check for obvious internal names
        if (hostLower.startsWith("internal")) {
            return true;
        }
        return false;
    }

    private static boolean isNonPublicAddress(InetAddress addr) {
        if (addr == null) {
            return true;
        }
        // loopback
        if (addr.isLoopbackAddress()) {
            return true;
        }
        // link local
        if (addr.isLinkLocalAddress()) {
            return true;
        }
        // site local/private
        if (addr.isSiteLocalAddress()) {
            return true;
        }
        // multicast
        if (addr.isMulticastAddress()) {
            return true;
        }
        // any local
        if (addr.isAnyLocalAddress()) {
            return true;
        }

        if (addr instanceof Inet4Address) {
            byte[] b = addr.getAddress(); // in network order
            int first = b[0] & 0xFF;
            // 0.0.0.0/8, 10.0.0.0/8, 127/8, 169.254/16, 172.16/12, 192.168/16, 100.64/10 (CGNAT), 192.0.0.0/24, 192.0.2.0/24, 198.18.0.0/15, 198.51.100.0/24, 203.0.113.0/24, 224+
            if (first == 0) return true;
            if (first == 10) return true;
            if (first == 127) return true;
            if (first == 169 && (b[1] & 0xFF) == 254) return true;
            if (first == 172) {
                int second = b[1] & 0xFF;
                if (second >= 16 && second <= 31) return true;
            }
            if (first == 192) {
                int second = b[1] & 0xFF;
                if (second == 168) return true;
                if (second == 0) return true;
            }
            if (first == 100) {
                int second = b[1] & 0xFF;
                if (second >= 64 && second <= 127) return true; // CGNAT
            }
            if (first == 192 && (b[1] & 0xFF) == 0 && (b[2] & 0xFF) == 0) return true;
            if (first == 198) {
                int second = b[1] & 0xFF;
                if (second == 18 || second == 19) return true;
                if (second == 51 && (b[2] & 0xFF) == 100) return true;
            }
            if (first == 203 && (b[1] & 0xFF) == 0 && (b[2] & 0xFF) == 113) return true;
            if (first >= 224) return true; // multicast/reserved
            return false;
        } else if (addr instanceof Inet6Address) {
            byte[] b = addr.getAddress();
            // ::1 loopback already caught, ::/128 unspecified caught, fc00::/7 unique local, fe80::/10 link local, ff00::/8 multicast
            if (b[0] == 0x00 && isAllZero(b)) return true; // :: 
            if ((b[0] & 0xFE) == 0xFC) return true; // fc00::/7
            if ((b[0] & 0xC0) == 0x80) {
                // fe80::/10
            }
            if ((b[0] & 0xFF) == 0xFE && ((b[1] & 0xC0) == 0x80)) {
                // fe80/10 approx
            }
            if ((b[0] & 0xF0) == 0xF0 && ((b[0] & 0x0F) >= 0x0E || (b[0] & 0xFF) == 0xFF)) {
                // ff00::/8 multicast or reserved
            }
            // broader checks
            if ((b[0] & 0xFF) == 0xFF) return true; // multicast
            if ((b[0] & 0xFE) == 0xFC) return true; // unique local
            if ((b[0] & 0xC0) == 0x80 && (b[0] & 0xFF) >= 0xFE) {
                // link local fe80::/10
            }
            // better: check ranges
            int firstByte = b[0] & 0xFF;
            if (firstByte == 0x00 && isAllZeroExceptMaybe(b)) {
                // :: 
            }
            if (firstByte >= 0xFC && firstByte <= 0xFD) return true; // unique local
            if ((firstByte & 0xFF) == 0xFE && ((b[1] & 0xC0) == 0x80 || (b[1] & 0xC0) == 0xC0)) return true; // link-local/site-local style
            if ((firstByte & 0xF0) == 0xF0) {
                // reserved/multicast
                if ((firstByte & 0xFF) >= 0xF0) return true;
            }
            return false;
        }
        return true;
    }

    private static boolean isAllZero(byte[] b) {
        for (byte x : b) {
            if (x != 0) return false;
        }
        return true;
    }

    private static boolean isAllZeroExceptMaybe(byte[] b) {
        // simplistic
        for (int i = 1; i < b.length; i++) {
            if (b[i] != 0) return false;
        }
        return b[0] == 0;
    }
}
