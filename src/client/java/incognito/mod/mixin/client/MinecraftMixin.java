package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import incognito.mod.detection.ExploitContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Core Minecraft client hooks for:
 * - Telemetry blocking
 * - Exploit context cleanup (detection is in screen-specific mixins)
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    
    @Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
    private void incognito$disableTelemetry(CallbackInfoReturnable<Boolean> info) {
        if (IncognitoConfig.getInstance().shouldDisableTelemetry()) {
            Incognito.LOGGER.debug("[Incognito] Blocking telemetry");
            info.setReturnValue(false);
        }
    }
    
    /**
     * Handle exploit context cleanup when screens change.
     * Context DETECTION is handled by screen-specific mixins (SignEditScreenMixin, etc.)
     * This only handles CLEANUP when leaving exploitable screens.
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void incognito$onSetScreen(Screen screen, CallbackInfo ci) {
        // Only handle cleanup - detection is in screen constructor mixins
        if (screen == null && ExploitContext.isInExploitableContext()) {
            // Closing to null while in exploitable context - defer cleanup
            // This protects packet serialization that happens after screen closes
            incognito$scheduleContextCleanup();
        } else if (screen != null && !incognito$isExploitableScreen(screen)) {
            // Switching to a non-exploitable screen - clear immediately
            ExploitContext.exitContext();
        }
        // If switching to another exploitable screen, context will be set by that screen's mixin
    }
    
    /**
     * Check if a screen is one of the exploitable screen types.
     */
    @Unique
    private boolean incognito$isExploitableScreen(Screen screen) {
        return screen instanceof AbstractSignEditScreen 
            || screen instanceof AnvilScreen 
            || screen instanceof BookEditScreen;
    }
    
    /**
     * Schedule context cleanup after a brief delay.
     * This ensures packet serialization completes before context is cleared.
     */
    @Unique
    private void incognito$scheduleContextCleanup() {
        Minecraft.getInstance().execute(() -> {
            // Clear on next tick, after any pending packet serialization
            ExploitContext.exitContext();
        });
    }
}
