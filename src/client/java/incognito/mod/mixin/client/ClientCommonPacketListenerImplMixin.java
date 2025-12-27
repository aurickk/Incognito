package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.detection.LocalUrlDetector;
import incognito.mod.detection.TrackPackDetector;
import incognito.mod.util.ServerAddressTracker;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin to intercept resource pack requests.
 * 
 * LOCAL URL SPOOFING:
 * Instead of intercepting packets and sending manual FAILED_DOWNLOAD responses (which causes
 * texture reloads), we redirect local URLs to a guaranteed-to-fail address (0.0.0.0:0).
 * This makes Minecraft's normal download flow fail at the HTTP level, exactly like vanilla
 * behavior when a local service doesn't exist. No texture reloads occur because the failure
 * happens at the same point in the code path as natural failures.
 * 
 * PORT SCAN DETECTION:
 * Detection always occurs for local URL probes, regardless of protection settings.
 * This ensures full visibility into scanning attempts even when protection is off.
 * 
 * FAKE PACK ACCEPT:
 * When enabled, we send ACCEPTED/DOWNLOADED/SUCCESSFULLY_LOADED without actually downloading
 * the pack. This prevents resource pack fingerprinting while allowing connection to servers
 * that require packs.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    
    /**
     * URL that is guaranteed to fail immediately at the HTTP level.
     * 0.0.0.0:0 is an invalid address that will cause immediate connection failure,
     * matching vanilla behavior when a local service doesn't exist.
     */
    @Unique
    private static final String FAIL_URL = "http://0.0.0.0:0/incognito-blocked";
    
    /**
     * Count of spoofed local URLs for logging.
     */
    @Unique
    private static int incognito$localUrlSpoofCount = 0;
    
    /**
     * Redirect the URL from the packet for local URLs (only when protection is enabled).
     * This makes Minecraft try to download from an invalid address, causing a natural
     * HTTP failure without texture reloads.
     */
    @Redirect(
        method = "handleResourcePackPush",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/ClientboundResourcePackPushPacket;url()Ljava/lang/String;")
    )
    private String incognito$redirectLocalUrl(ClientboundResourcePackPushPacket packet) {
        String originalUrl = packet.url();
        
        // Only redirect if protection is enabled
        if (IncognitoConfig.getInstance().shouldSpoofLocalPackUrls() && ServerAddressTracker.shouldBlockLocalUrl(originalUrl)) {
            incognito$localUrlSpoofCount++;
            String reason = LocalUrlDetector.getBlockReason(originalUrl);
            
            // Log the EXACT original URL from the server packet (before any parsing)
            Incognito.LOGGER.info("[Incognito] Blocking local port scan #{}: ORIGINAL URL = \"{}\" (reason: {})", 
                incognito$localUrlSpoofCount, originalUrl, reason);
            
            // Return the fail URL - Minecraft will try to download from this and fail naturally
            return FAIL_URL;
        }
        
        return originalUrl;
    }
    
    /**
     * Handle resource pack pushes:
     * - ALWAYS detect local URL probes (port scans) - even if protection is off
     * - Track packs for fingerprinting detection
     * - Optionally fake accept packs
     */
    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void onResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        String url = packet.url();
        UUID packId = packet.id();
        
        boolean isLocalUrlProbe = ServerAddressTracker.shouldBlockLocalUrl(url);
        boolean protectionEnabled = IncognitoConfig.getInstance().shouldSpoofLocalPackUrls();
        
        // ALWAYS detect and alert for local URL probes, regardless of protection setting
        if (isLocalUrlProbe) {
            // Alert with protection status (blocked vs just detected)
            PrivacyLogger.alertLocalPortScanDetected(url, protectionEnabled);
            
            // Record for detection purposes
            TrackPackDetector.recordRequest(url, packet.hash());
            
            // If protection is enabled, the @Redirect will handle the URL substitution
            // We don't cancel here - let the packet flow through
            if (protectionEnabled) {
                return; // Let @Redirect handle it
            }
            // If protection is off, continue with normal flow but we've alerted
        }
        
        // Record the request for detection/logging
        boolean suspicious = TrackPackDetector.recordRequest(url, packet.hash());
        
        // Alert if suspicious (for logging purposes)
        if (suspicious && TrackPackDetector.consumeNotifySuspiciousOnce()) {
            PrivacyLogger.alertTrackPackDetected(url);
        }
        
        // Check for fingerprinting pattern
        if (TrackPackDetector.isFingerprinting() && TrackPackDetector.consumeNotifyPatternOnce()) {
            PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER,
                "Resource pack fingerprinting pattern detected!");
            PrivacyLogger.toast(PrivacyLogger.AlertType.DANGER, "Resource Pack Fingerprinting Detected");
        }
        
        // Note: Fake pack accept feature is not yet implemented in current config
        // If needed, add shouldFakePackAccept() to IncognitoConfig and SpoofSettings
    }
    
}

