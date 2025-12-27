package incognito.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.config.IncognitoConstants;
import incognito.mod.util.ServerAddressTracker;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import java.util.Objects;

/**
 * Protects against redirect-to-localhost attacks.
 */
@Mixin(HttpUtil.class)
public class HttpUtilMixin {
    
    @Unique
    private static boolean incognito$loggedRedirectProtection = false;
    
    @WrapOperation(
        method = "downloadFile",
        at = @At(value = "INVOKE", target = "Ljava/net/HttpURLConnection;getInputStream()Ljava/io/InputStream;"),
        require = 0
    )
    private static InputStream incognito$checkRedirects(
            HttpURLConnection instance, 
            Operation<InputStream> original,
            @Local(argsOnly = true) Proxy proxy,
            @Local(argsOnly = true) Map<String, String> requestProperties,
            @Local LocalRef<HttpURLConnection> httpURLConnection) throws IOException {
        
        if (!IncognitoConfig.getInstance().shouldSpoofLocalPackUrls()) {
            return original.call(instance);
        }
        
        if (ServerAddressTracker.isOnLanServer()) {
            return original.call(instance);
        }
        
        String initialHost = instance.getURL().getHost();
        if (ServerAddressTracker.isLocalAddress(initialHost)) {
            Incognito.LOGGER.warn("[Incognito] Blocked local address in HTTP download: {}", instance.getURL());
            throw new IllegalStateException("[Incognito] Blocked connection to local address: " + instance.getURL());
        }
        
        instance.setInstanceFollowRedirects(false);
        int maxRedirects = getMaxRedirects();
        int redirectCount = 0;
        int responseCode = instance.getResponseCode();
        
        while (isRedirect(responseCode) && instance.getHeaderField("Location") != null) {
            if (redirectCount >= maxRedirects) {
                throw new ProtocolException("Server redirected too many times (" + maxRedirects + ")");
            }
            
            URL redirectUrl = parseRedirectUrl(instance.getURL(), instance.getHeaderField("Location"));
            
            if (!instance.getURL().getProtocol().equalsIgnoreCase(redirectUrl.getProtocol())) {
                Incognito.LOGGER.debug("[Incognito] Stopping at cross-protocol redirect: {} -> {}", 
                    instance.getURL().getProtocol(), redirectUrl.getProtocol());
                break;
            }
            
            if (ServerAddressTracker.isLocalAddress(redirectUrl.getHost())) {
                Incognito.LOGGER.warn("[Incognito] Blocked redirect-to-localhost attack! {} -> {}", 
                    instance.getURL(), redirectUrl);
                
                if (!incognito$loggedRedirectProtection) {
                    incognito$loggedRedirectProtection = true;
                    PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER, 
                        "Blocked redirect-to-localhost attack!");
                }
                
                throw new IllegalStateException("[Incognito] Blocked redirect to local address: " + redirectUrl);
            }
            
            instance = (HttpURLConnection) redirectUrl.openConnection(proxy);
            instance.setInstanceFollowRedirects(false);
            Objects.requireNonNull(instance);
            
            if (requestProperties != null) {
                requestProperties.forEach(instance::setRequestProperty);
            }
            
            responseCode = instance.getResponseCode();
            redirectCount++;
            
            Incognito.LOGGER.debug("[Incognito] Following redirect #{}: {}", redirectCount, redirectUrl);
        }
        
        if (redirectCount >= maxRedirects) {
            throw new ProtocolException("Server redirected too many times (" + maxRedirects + ")");
        }
        
        httpURLConnection.set(instance);
        return original.call(instance);
    }
    
    @Unique
    private static boolean isRedirect(int responseCode) {
        return (responseCode >= 300 && responseCode <= 303) || 
               responseCode == 305 || 
               responseCode == 307 || 
               responseCode == 308;
    }
    
    @SuppressWarnings("deprecation")
    @Unique
    private static URL parseRedirectUrl(URL baseUrl, String location) throws MalformedURLException {
        try {
            return new URL(location);
        } catch (MalformedURLException e) {
            return new URL(baseUrl, location);
        }
    }
    
    @Unique
    private static int getMaxRedirects() {
        String maxRedirectProp = System.getProperty("http.maxRedirects");
        if (maxRedirectProp == null) {
            return IncognitoConstants.Detection.MAX_HTTP_REDIRECTS;
        }
        try {
            return Math.max(Integer.parseInt(maxRedirectProp), 1);
        } catch (NumberFormatException e) {
            return IncognitoConstants.Detection.MAX_HTTP_REDIRECTS;
        }
    }
}

