package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.tracking.ModIdResolver;
import incognito.mod.tracking.ModTracker;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to track keybinds registered by mods via Fabric API.
 * Uses stack trace analysis to determine which mod registered each keybind.
 */
@Mixin(value = KeyBindingRegistryImpl.class, remap = false)
public class KeyBindingRegistryImplMixin {
    
    @Unique
    private static boolean incognito$loggedOnce = false;
    
    /**
     * Track keybind registration.
     */
    @Inject(method = "registerKeyBinding", at = @At("RETURN"), require = 0)
    private static void incognito$onKeybindRegister(KeyMapping keyBinding, CallbackInfoReturnable<KeyMapping> cir) {
        if (keyBinding == null) return;
        
        String keybindName = keyBinding.getName();
        String modId = ModIdResolver.getModIdFromStacktrace();
        
        if (modId != null) {
            ModTracker.recordKeybind(modId, keybindName);
            
            if (!incognito$loggedOnce) {
                incognito$loggedOnce = true;
                Incognito.LOGGER.info("[Incognito] Keybind tracking active");
            }
        } else {
            // Unknown source - could be vanilla or unknown mod
            // Record as vanilla to be safe
            ModTracker.recordVanillaKeybind(keybindName);
            Incognito.LOGGER.debug("[Incognito] Keybind registered without identifiable mod: {}", keybindName);
        }
    }
}

