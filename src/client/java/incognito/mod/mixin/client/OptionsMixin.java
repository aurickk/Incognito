package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.tracking.ModTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to track vanilla keybinds when Options is initialized.
 */
@Mixin(Options.class)
public class OptionsMixin {
    
    @Shadow @Final public KeyMapping keyUp;
    @Shadow @Final public KeyMapping keyDown;
    @Shadow @Final public KeyMapping keyLeft;
    @Shadow @Final public KeyMapping keyRight;
    @Shadow @Final public KeyMapping keyJump;
    @Shadow @Final public KeyMapping keyShift;
    @Shadow @Final public KeyMapping keySprint;
    @Shadow @Final public KeyMapping keyInventory;
    @Shadow @Final public KeyMapping keyDrop;
    @Shadow @Final public KeyMapping keyChat;
    @Shadow @Final public KeyMapping keyPlayerList;
    @Shadow @Final public KeyMapping keyPickItem;
    @Shadow @Final public KeyMapping keyCommand;
    @Shadow @Final public KeyMapping keyScreenshot;
    @Shadow @Final public KeyMapping keyTogglePerspective;
    @Shadow @Final public KeyMapping keySmoothCamera;
    @Shadow @Final public KeyMapping keyFullscreen;
    @Shadow @Final public KeyMapping keySpectatorOutlines;
    @Shadow @Final public KeyMapping keySwapOffhand;
    @Shadow @Final public KeyMapping keySaveHotbarActivator;
    @Shadow @Final public KeyMapping keyLoadHotbarActivator;
    @Shadow @Final public KeyMapping keyAdvancements;
    @Shadow @Final public KeyMapping[] keyHotbarSlots;
    @Shadow @Final public KeyMapping keyAttack;
    @Shadow @Final public KeyMapping keyUse;
    @Shadow @Final public KeyMapping keySocialInteractions;
    
    @Unique
    private static boolean incognito$trackedVanillaKeybinds = false;
    
    /**
     * Track vanilla keybinds after Options is loaded.
     */
    @Inject(method = "load()V", at = @At("RETURN"), require = 0)
    private void incognito$trackVanillaKeybinds(CallbackInfo ci) {
        if (incognito$trackedVanillaKeybinds) return;
        incognito$trackedVanillaKeybinds = true;
        
        // Track all vanilla keybinds
        incognito$recordKeybind(keyUp);
        incognito$recordKeybind(keyDown);
        incognito$recordKeybind(keyLeft);
        incognito$recordKeybind(keyRight);
        incognito$recordKeybind(keyJump);
        incognito$recordKeybind(keyShift);
        incognito$recordKeybind(keySprint);
        incognito$recordKeybind(keyInventory);
        incognito$recordKeybind(keyDrop);
        incognito$recordKeybind(keyChat);
        incognito$recordKeybind(keyPlayerList);
        incognito$recordKeybind(keyPickItem);
        incognito$recordKeybind(keyCommand);
        incognito$recordKeybind(keyScreenshot);
        incognito$recordKeybind(keyTogglePerspective);
        incognito$recordKeybind(keySmoothCamera);
        incognito$recordKeybind(keyFullscreen);
        incognito$recordKeybind(keySpectatorOutlines);
        incognito$recordKeybind(keySwapOffhand);
        incognito$recordKeybind(keySaveHotbarActivator);
        incognito$recordKeybind(keyLoadHotbarActivator);
        incognito$recordKeybind(keyAdvancements);
        incognito$recordKeybind(keySocialInteractions);
        incognito$recordKeybind(keyAttack);
        incognito$recordKeybind(keyUse);
        
        // Track hotbar slots
        for (KeyMapping hotbarKey : keyHotbarSlots) {
            incognito$recordKeybind(hotbarKey);
        }
        
        Incognito.LOGGER.info("[Incognito] Tracked {} vanilla keybinds", ModTracker.getKeybindCount());
    }
    
    @Unique
    private void incognito$recordKeybind(KeyMapping keyMapping) {
        if (keyMapping != null) {
            ModTracker.recordVanillaKeybind(keyMapping.getName());
        }
    }
}

