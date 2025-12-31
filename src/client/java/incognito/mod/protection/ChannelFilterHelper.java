package incognito.mod.protection;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static incognito.mod.config.IncognitoConstants.Channels.*;

/**
 * Shared helper for filtering channel sets in Fabric networking mixins.
 * Eliminates code duplication between FabricConfigNetworkingMixin and FabricPlayNetworkingMixin.
 */
public final class ChannelFilterHelper {
    
    private ChannelFilterHelper() {}
    
    /**
     * Filter a channel set based on current spoofing configuration.
     * 
     * @param original The original channel set
     * @param methodName The method name for logging
     * @param logged AtomicBoolean to track if we've already logged (for deduplication)
     * @return The filtered set, or null if no filtering should occur
     */
    public static Set<ResourceLocation> filterChannels(Set<ResourceLocation> original, 
                                                        String methodName, 
                                                        AtomicBoolean logged) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        // Only filter channels when channel spoofing is enabled.
        // Brand-only mode should not modify channels.
        if (!config.shouldSpoofBrand() || !config.shouldSpoofChannels()) {
            return null;
        }
        
        if (original == null || original.isEmpty()) {
            return null;
        }
        
        // VANILLA MODE: Return empty set
        if (ClientSpoofer.isVanillaMode()) {
            if (logged.compareAndSet(false, true)) {
                Incognito.LOGGER.info("[Incognito] VANILLA MODE - Returning empty channel set for {}", methodName);
            }
            return Collections.emptySet();
        }
        
        // FABRIC MODE: Filter to only fabric:* channels
        if (ClientSpoofer.isFabricMode()) {
            Set<ResourceLocation> filtered = new HashSet<>();
            for (ResourceLocation id : original) {
                if (isAllowedFabricChannel(id)) {
                    filtered.add(id);
                } else if (Incognito.LOGGER.isDebugEnabled()) {
                    Incognito.LOGGER.debug("[Incognito] FABRIC MODE - Filtering {} channel: {}", methodName, id);
                }
            }
            if (logged.compareAndSet(false, true)) {
                Incognito.LOGGER.info("[Incognito] FABRIC MODE - Filtering {} channels: {} -> {}", 
                    methodName, original.size(), filtered.size());
            }
            return filtered;
        }
        
        return null;
    }
    
    /**
     * Check if a channel is allowed in Fabric mode.
     */
    public static boolean isAllowedFabricChannel(ResourceLocation id) {
        String ns = id.getNamespace();
        if (MINECRAFT.equals(ns)) {
            return true;
        }
        return FABRIC_NAMESPACE.equals(ns) || ns.startsWith(FABRIC_NAMESPACE + "-");
    }
}

