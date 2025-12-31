package incognito.mod.protection;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.config.IncognitoConstants;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicBoolean;

import static incognito.mod.config.IncognitoConstants.Brands.*;

/**
 * Handles client brand spoofing and channel filtering logic.
 * Provides methods to check spoofing modes (vanilla, fabric, forge)
 * and determines which network channels should be blocked.
 */
public class ClientSpoofer {
    
    private static final AtomicBoolean loggedBrandSpoof = new AtomicBoolean(false);
    
    public static String getSpoofedBrand() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        if (!config.shouldSpoofBrand()) {
            return FABRIC;
        }
        
        String brand = config.getSettings().getEffectiveBrand();
        
        if (loggedBrandSpoof.compareAndSet(false, true)) {
            Incognito.LOGGER.info("[Incognito] Spoofing brand as: {}", brand);
            PrivacyLogger.alertClientBrandSpoofed(FABRIC, brand);
        }
        
        return brand;
    }
    
    public static boolean isVanillaMode() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        return config.shouldSpoofBrand() && config.shouldSpoofChannels() && VANILLA.equals(config.getEffectiveBrand());
    }
    
    public static boolean isFabricMode() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        return config.shouldSpoofBrand() && config.shouldSpoofChannels() && FABRIC.equals(config.getEffectiveBrand());
    }
    
    public static boolean isForgeMode() {
        IncognitoConfig config = IncognitoConfig.getInstance();
        return config.shouldSpoofBrand() && config.shouldSpoofChannels() && FORGE.equals(config.getEffectiveBrand());
    }
    
    public static boolean shouldBlockPayload(ResourceLocation payloadId) {
        IncognitoConfig config = IncognitoConfig.getInstance();
        
        if (!config.shouldSpoofBrand() || !config.shouldSpoofChannels()) {
            return false;
        }
        
        String channel = payloadId.toString();
        String namespace = payloadId.getNamespace();
        String path = payloadId.getPath();
        String brand = config.getEffectiveBrand();
        
        if (VANILLA.equals(brand)) {
            if (Incognito.LOGGER.isDebugEnabled()) {
            Incognito.LOGGER.debug("[Incognito] VANILLA MODE - Blocking payload: {}", channel);
            }
            return true;
        }
        
        if (FABRIC.equals(brand)) {
            if (IncognitoConstants.Channels.MINECRAFT.equals(namespace) 
                    || IncognitoConstants.Channels.COMMON.equals(namespace)) {
                return false;
            }
            
            if (IncognitoConstants.Channels.FABRIC_NAMESPACE.equals(namespace) 
                    || namespace.startsWith(IncognitoConstants.Channels.FABRIC_NAMESPACE + "-")) {
                if (Incognito.LOGGER.isDebugEnabled()) {
                Incognito.LOGGER.debug("[Incognito] FABRIC MODE - Allowing fabric channel: {}", channel);
                }
                return false;
            }
            
            if (Incognito.LOGGER.isDebugEnabled()) {
            Incognito.LOGGER.debug("[Incognito] FABRIC MODE - Blocking mod channel: {}", channel);
            }
            return true;
        }
        
        if (FORGE.equals(brand)) {
            if (IncognitoConstants.Channels.MINECRAFT.equals(namespace)) {
                return false;
            }
            
            if (IncognitoConstants.Channels.FORGE_NAMESPACE.equals(namespace) 
                    && (IncognitoConstants.Channels.LOGIN.equals(path) 
                        || IncognitoConstants.Channels.HANDSHAKE.equals(path))) {
                if (Incognito.LOGGER.isDebugEnabled()) {
                Incognito.LOGGER.debug("[Incognito] FORGE MODE - Allowing forge channel: {}", channel);
                }
                return false;
            }
            
            if (Incognito.LOGGER.isDebugEnabled()) {
            Incognito.LOGGER.debug("[Incognito] FORGE MODE - Blocking channel: {}", channel);
            }
            return true;
        }
        
        return false;
    }
    
    public static void reset() {
        loggedBrandSpoof.set(false);
    }
}
