package incognito.mod.detection;

import incognito.mod.Incognito;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public class LocalUrlDetector {
    
    private static final Pattern PRIVATE_IPV4 = Pattern.compile(
        "^(" +
        "10\\." +                           // 10.0.0.0/8
        "|172\\.(1[6-9]|2[0-9]|3[01])\\." + // 172.16.0.0/12
        "|192\\.168\\." +                   // 192.168.0.0/16
        "|127\\." +                          // 127.0.0.0/8 (loopback)
        "|0\\." +                            // 0.0.0.0/8
        "|169\\.254\\." +                    // 169.254.0.0/16 (link-local)
        "|100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\." + // 100.64.0.0/10 (CGNAT)
        ")"
    );
    
    private static final String[] LOCALHOST_NAMES = {
        "localhost",
        "localhost.localdomain",
        "local",
        "ip6-localhost",
        "ip6-loopback"
    };
    
    public static boolean isLocalUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            
            if (host == null || host.isEmpty()) {
                return false;
            }
            
            String hostLower = host.toLowerCase();
            for (String localhost : LOCALHOST_NAMES) {
                if (hostLower.equals(localhost)) {
                    Incognito.LOGGER.debug("[Incognito] Detected localhost hostname: {}", host);
                    return true;
                }
            }
            
            if (PRIVATE_IPV4.matcher(host).find()) {
                Incognito.LOGGER.debug("[Incognito] Detected private IPv4: {}", host);
                return true;
            }
            
            if (host.equals("::1") || host.equals("[::1]") || host.startsWith("fe80:")) {
                Incognito.LOGGER.debug("[Incognito] Detected IPv6 loopback/link-local: {}", host);
                return true;
            }
            
            try {
                InetAddress address = InetAddress.getByName(host);
                if (address.isLoopbackAddress() || 
                    address.isSiteLocalAddress() || 
                    address.isLinkLocalAddress() ||
                    address.isAnyLocalAddress()) {
                    Incognito.LOGGER.debug("[Incognito] Hostname {} resolves to local address: {}", 
                        host, address.getHostAddress());
                    return true;
                }
            } catch (UnknownHostException e) {
                Incognito.LOGGER.debug("[Incognito] Could not resolve hostname: {}", host);
            }
            
            return false;
            
        } catch (java.net.URISyntaxException e) {
            Incognito.LOGGER.debug("[Incognito] Error parsing URL {}: {}", url, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a description of why the URL was blocked.
     */
    public static String getBlockReason(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            
            if (host == null) {
                return "invalid URL";
            }
            
            String hostLower = host.toLowerCase();
            for (String localhost : LOCALHOST_NAMES) {
                if (hostLower.equals(localhost)) {
                    return "localhost hostname";
                }
            }
            
            if (host.startsWith("127.")) {
                return "loopback address (127.x.x.x)";
            }
            if (host.startsWith("192.168.")) {
                return "private network (192.168.x.x)";
            }
            if (host.startsWith("10.")) {
                return "private network (10.x.x.x)";
            }
            if (PRIVATE_IPV4.matcher(host).find()) {
                return "private/local IP address";
            }
            if (host.equals("::1") || host.equals("[::1]")) {
                return "IPv6 loopback";
            }
            
            return "local/private address";
            
        } catch (java.net.URISyntaxException e) {
            return "suspicious URL";
        }
    }
}

