package incognito.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import incognito.mod.Incognito;
import incognito.mod.PrivacyLogger;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.config.IncognitoConstants;
import incognito.mod.detection.ExploitDetector;
import incognito.mod.util.KeybindDefaults;
import incognito.mod.tracking.ModTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

/**
 * Keybind detection and protection via Component-level interception.
 * Detection always happens; protection only when enabled.
 */
@Mixin(KeybindContents.class)
public class KeybindContentsMixin {
    
    @Shadow @Final
    private String name;
    
    @Unique
    private static final java.util.Set<String> recentlyLoggedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Unique
    private static long lastLogClearTime = 0;
    
    @Unique
    private static volatile boolean anyValueChangedInBatch = false;
    @Unique
    private static volatile PrivacyLogger.ExploitSource batchSource = null;
    @Unique
    private static volatile long batchStartTime = 0;
    
    @WrapOperation(
        method = "getNestedComponent",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"),
        require = 0
    )
    private Object incognito$spoofKeybindName(Supplier<?> supplier, Operation<Object> original) {
        // Get the original resolved value
        Object originalResult = original.call(supplier);
        String originalValue = originalResult instanceof Component c ? c.getString() : originalResult.toString();
        
        boolean protectionEnabled = IncognitoConfig.getInstance().isTranslationProtectionEnabled();
        
        // Ensure we've scanned runtime keybinds (captures mods like Meteor Client)
        scanRuntimeKeybindsIfNeeded();
        
        // Check if this is a keybind we should handle
        boolean isVanilla = KeybindDefaults.hasDefault(name) || ModTracker.isVanillaKeybind(name);
        boolean isKnownModKeybind = !isVanilla && ModTracker.isKnownKeybind(name);
        
        long now = System.currentTimeMillis();
        if (now - lastLogClearTime > IncognitoConstants.Timeouts.LOG_CACHE_CLEAR_INTERVAL_MS) {
            recentlyLoggedKeys.clear();
            lastLogClearTime = now;
        }
        
        String logKey = name + ":" + originalValue;
        if (recentlyLoggedKeys.add(logKey)) {
            String keyType = isVanilla ? "VANILLA" : (isKnownModKeybind ? "MOD" : "UNKNOWN");
            String modId = isKnownModKeybind ? ModTracker.getModForKeybind(name) : null;
            Incognito.LOGGER.info("[Keybind:{}] Key '{}' resolved to '{}'{}", 
                keyType, name, originalValue, 
                modId != null ? " (mod: " + modId + ")" : "");
        }
        
        String spoofedValue = null;
        boolean valueWouldChange = false;
        
        if (isVanilla) {
            spoofedValue = KeybindDefaults.getDefault(name);
            if (spoofedValue == null) {
                spoofedValue = name;
            }
            valueWouldChange = !originalValue.equals(spoofedValue);
        } else if (isKnownModKeybind) {
            spoofedValue = name;
            valueWouldChange = !originalValue.equals(spoofedValue);
        }
        
        handleExploitDetected(originalValue, spoofedValue, valueWouldChange, protectionEnabled);
        
        if (!isVanilla && !isKnownModKeybind) {
            return originalResult;
        }
        
        if (protectionEnabled) {
            return Component.literal(spoofedValue);
        } else {
            return originalResult;
        }
    }
    
    private void handleExploitDetected(String originalValue, String spoofedValue, boolean valueWouldChange, boolean protectionEnabled) {
        String alertEntry;
        if (spoofedValue == null) {
            // Unknown key - not a registered keybind
            alertEntry = "[" + name + "] '" + originalValue + "' (unknown key)";
        } else if (valueWouldChange) {
            if (protectionEnabled) {
                alertEntry = "[" + name + "] '" + originalValue + "'→'" + spoofedValue + "'";
            } else {
                alertEntry = "[" + name + "] '" + originalValue + "' (protection OFF)";
            }
        } else {
            alertEntry = "[" + name + "] '" + originalValue + "' (unchanged)";
        }
        
        // Use unified detector for source detection
        PrivacyLogger.ExploitSource source = ExploitDetector.detectSource();
        boolean playerInitiated = ExploitDetector.wasPlayerInitiated(source);
        
        long now = System.currentTimeMillis();
        if (now - batchStartTime > IncognitoConstants.Timeouts.BATCH_WINDOW_MS) {
            anyValueChangedInBatch = false;
            batchSource = null;
            batchStartTime = now;
        }
        if (valueWouldChange) {
            anyValueChangedInBatch = true;
        }
        if (batchSource == null || source != PrivacyLogger.ExploitSource.UNKNOWN) {
            batchSource = source;
        }
        
        // Deduplicate alerts
        if (!ExploitDetector.shouldAlert(alertEntry)) {
            return;
        }
        
        // Log to console
        String initiator = playerInitiated ? "player" : "server";
        String action = protectionEnabled && valueWouldChange ? "Spoofed" : "Detected";
        Incognito.LOGGER.info("[Keybind] {} ({}-initiated {}): {}", 
            action, initiator, source.getDisplayName().toLowerCase(), alertEntry);
        
        // Log detection
        PrivacyLogger.logDetection("TranslationExploit:" + source.getDisplayName(), alertEntry);
        
        if (!playerInitiated && ExploitDetector.shouldSendHeaderAlert()) {
            if (anyValueChangedInBatch) {
                String sourceName = (batchSource != null ? batchSource : source).getDisplayName().toLowerCase();
                PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER, "Translation exploit detected via " + sourceName + "!");
            } else {
                PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER, "Translation exploit detected!");
            }
            PrivacyLogger.toast(PrivacyLogger.AlertType.DANGER, "Translation Exploit Detected");
        }
        
        if (protectionEnabled && valueWouldChange) {
            PrivacyLogger.sendKeybindDetail(alertEntry);
        }
    }
    
    @Unique
    private static boolean runtimeKeybindsScanned = false;
    @Unique
    private static long lastScanTime = 0;
    
    @Unique
    private static void scanRuntimeKeybindsIfNeeded() {
        long now = System.currentTimeMillis();
        
        if (runtimeKeybindsScanned && (now - lastScanTime) < IncognitoConstants.Timeouts.KEYBIND_RESCAN_INTERVAL_MS) {
            return;
        }
        
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            
            KeyMapping[] keyMappings = mc.options.keyMappings;
            if (keyMappings == null) return;
            
            int newKeybinds = 0;
            for (KeyMapping mapping : keyMappings) {
                if (mapping == null) continue;
                
                String keyName = mapping.getName();
                if (keyName == null) continue;
                
                // Skip if already known
                if (KeybindDefaults.hasDefault(keyName) || ModTracker.isKnownKeybind(keyName)) {
                    continue;
                }
                
                // Register as a mod keybind (we don't know which mod, so use "unknown")
                ModTracker.recordKeybind("runtime", keyName);
                newKeybinds++;
            }
            
            if (!runtimeKeybindsScanned && newKeybinds > 0) {
                Incognito.LOGGER.info("[Incognito] Runtime scan found {} additional keybinds", newKeybinds);
            }
            
            runtimeKeybindsScanned = true;
            lastScanTime = now;
        } catch (RuntimeException e) {
            Incognito.LOGGER.debug("[Incognito] Runtime keybind scan failed: {}", e.getMessage());
        }
    }
}
