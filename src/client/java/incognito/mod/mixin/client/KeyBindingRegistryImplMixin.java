package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to log keybinds registered by mods via Fabric API.
 * Note: Mod keybinds are automatically blocked by protection logic since
 * they won't be in the vanilla translation keys whitelist.
 */
@Mixin(value = KeyBindingRegistryImpl.class, remap = false)
public class KeyBindingRegistryImplMixin {
    
    @Unique
    private static boolean incognito$loggedOnce = false;
    
    /**
     * Log mod keybind registration for debugging purposes.
     */
    @Inject(method = "registerKeyBinding", at = @At("RETURN"), require = 0)
    private static void incognito$onKeybindRegister(KeyMapping keyBinding, CallbackInfoReturnable<KeyMapping> cir) {
        if (keyBinding == null) return;
        
        if (!incognito$loggedOnce) {
            incognito$loggedOnce = true;
            Incognito.LOGGER.debug("[Incognito] Mod keybind registration detected (these will be blocked in exploitable contexts)");
        }
        
        Incognito.LOGGER.debug("[Incognito] Mod keybind registered: {}", keyBinding.getName());
    }
}

