package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.config.SpoofSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks server connection events for Incognito.
 * Handles secure chat enforcement detection (based on NoPryingEyes).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    
    @Unique
    private static volatile ScheduledExecutorService incognito$scheduler;
    
    @Unique
    private static volatile ScheduledFuture<?> incognito$pendingTask;
    
    @Unique
    private static synchronized ScheduledExecutorService incognito$getScheduler() {
        if (incognito$scheduler == null || incognito$scheduler.isShutdown()) {
            incognito$scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Incognito-Scheduler");
            t.setDaemon(true);
            return t;
        });
        }
        return incognito$scheduler;
    }
    
    @Unique
    private static synchronized void incognito$shutdownScheduler() {
        if (incognito$pendingTask != null) {
            incognito$pendingTask.cancel(false);
            incognito$pendingTask = null;
        }
        if (incognito$scheduler != null && !incognito$scheduler.isShutdown()) {
            incognito$scheduler.shutdownNow();
            incognito$scheduler = null;
        }
    }
    
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener)(Object)this;
        String serverAddress = listener.getConnection().getRemoteAddress().toString();
        
        if (serverAddress.startsWith("/")) {
            serverAddress = serverAddress.substring(1);
        }
        int colonIndex = serverAddress.lastIndexOf(':');
        if (colonIndex > 0) {
            serverAddress = serverAddress.substring(0, colonIndex);
        }
        
        IncognitoConfig.getInstance().setCurrentServer(serverAddress);
        
        // Check if server enforces secure chat and handle ON_DEMAND signing
        SpoofSettings settings = IncognitoConfig.getInstance().getSettings();
        if (packet.enforcesSecureChat()) {
            Incognito.LOGGER.info("[Incognito] Server enforces secure chat");
            
            if (settings.isOnDemand()) {
                // Enable signing for this session
                settings.setTempSign(true);
                
                // Show warning toast and message
                if (!settings.isSigningToastShown()) {
                    settings.setSigningToastShown(true);
                    PrivacyLogger.alertSecureChatRequired(serverAddress);
                }
            }
        }
        
        // Schedule port scan summary after 2 seconds
        incognito$pendingTask = incognito$getScheduler().schedule(() -> {
            Minecraft.getInstance().execute(PrivacyLogger::showPortScanSummary);
        }, 2, TimeUnit.SECONDS);
    }
    
    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        incognito$shutdownScheduler();
        PrivacyLogger.resetPortScanTracking();
        PrivacyLogger.clearCooldowns();
        IncognitoConfig.getInstance().setCurrentServer(null);
        IncognitoConfig.getInstance().getSettings().resetSessionState();
    }
}
