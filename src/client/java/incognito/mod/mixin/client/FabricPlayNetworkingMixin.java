package incognito.mod.mixin.client;

import incognito.mod.protection.ChannelFilterHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Filters Fabric's advertised play channel list when spoofing.
 * Intercepts multiple methods that could expose channel information.
 */
@Mixin(ClientPlayNetworking.class)
public class FabricPlayNetworkingMixin {
    
    @Unique
    private static final AtomicBoolean incognito$logged = new AtomicBoolean(false);
    
    /**
     * Filter getGlobalReceivers - returns all globally registered receivers
     */
    @Inject(method = "getGlobalReceivers", at = @At("RETURN"), cancellable = true, remap = false)
    private static void incognito$filterGlobalReceivers(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            cir.getReturnValue(), "play.getGlobalReceivers", incognito$logged);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
    
    /**
     * Filter getReceived - returns channels the server can send to this client
     */
    @Inject(method = "getReceived", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void incognito$filterReceived(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            cir.getReturnValue(), "play.getReceived", incognito$logged);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
    
    /**
     * Filter getSendable - returns channels this client can send to the server
     */
    @Inject(method = "getSendable", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void incognito$filterSendable(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        Set<ResourceLocation> filtered = ChannelFilterHelper.filterChannels(
            cir.getReturnValue(), "play.getSendable", incognito$logged);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
}
