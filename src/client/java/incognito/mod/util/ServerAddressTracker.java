package incognito.mod.util;

import incognito.mod.Incognito;
import incognito.mod.detection.LocalUrlDetector;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

/**
 * Tracks the current server address for LAN detection.
 * Allows local URLs when connected to LAN servers while blocking them on remote servers.
 * 
 * @see <a href="https://github.com/NikOverflow/ExploitPreventer">ExploitPreventer</a>
 */
public class ServerAddressTracker {
    
    private static volatile String currentServerAddress = null;
    private static volatile Boolean isOnLocalServer = null;
    
    public static void onConnect(SocketAddress socketAddress) {
        try {
            if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
                currentServerAddress = inetSocketAddress.getAddress().getHostAddress();
                isOnLocalServer = isLocalAddress(currentServerAddress);
                
                Incognito.LOGGER.debug("[Incognito] Connected to server: {} (isLocal: {})", 
                    currentServerAddress, isOnLocalServer);
            } else {
                currentServerAddress = null;
                isOnLocalServer = null;
            }
        } catch (Exception e) {
            Incognito.LOGGER.debug("[Incognito] Failed to get server address: {}", e.getMessage());
            currentServerAddress = null;
            isOnLocalServer = null;
        }
    }
    
    public static void onDisconnect() {
        Incognito.LOGGER.debug("[Incognito] Disconnected from server");
        currentServerAddress = null;
        isOnLocalServer = null;
    }
    
    public static boolean isOnLanServer() {
        return Boolean.TRUE.equals(isOnLocalServer);
    }
    
    public static String getCurrentServerAddress() {
        return currentServerAddress;
    }
    
    public static boolean isLocalAddress(String host) {
        if (host == null) {
            return false;
        }
        
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || 
                    address.isLoopbackAddress() || 
                    address.isSiteLocalAddress() || 
                    address.isLinkLocalAddress()) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            Incognito.LOGGER.debug("[Incognito] Could not resolve host for LAN check: {}", host);
        }
        
        return false;
    }
    
    /**
     * Determines if a local URL should be blocked based on server type.
     * LAN servers: allow local URLs (legitimate). Remote servers: block local URLs (attack).
     */
    public static boolean shouldBlockLocalUrl(String url) {
        if (!LocalUrlDetector.isLocalUrl(url)) {
            return false;
        }
        
        if (isOnLanServer()) {
            Incognito.LOGGER.debug("[Incognito] Allowing local URL on LAN server: {}", url);
            return false;
        }
        
        return true;
    }
}

