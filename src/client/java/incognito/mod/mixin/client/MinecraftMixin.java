package incognito.mod.mixin.client;

import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
    private void incognito$disableTelemetry(CallbackInfoReturnable<Boolean> info) {
        if (IncognitoConfig.getInstance().shouldDisableTelemetry()) {
            Incognito.LOGGER.debug("[Incognito] Blocking telemetry");
            info.setReturnValue(false);
        }
    }
}
