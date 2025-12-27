package incognito.mod.mixin.client;

import incognito.mod.detection.TrackPackDetector;
import incognito.mod.protection.ResourcePackGuard;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void onConnect(CallbackInfo ci) {
        TrackPackDetector.reset();
        ResourcePackGuard.onServerJoin();
    }
}
