package incognito.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import incognito.mod.Incognito;
import incognito.mod.tracking.ModIdResolver;
import incognito.mod.tracking.ModTracker;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Mixin to track translation keys from language files.
 * Uses instanceof checks matching ExploitPreventer's approach for reliability.
 * 
 * Pack types and handling:
 * - VanillaPackResources: Vanilla Minecraft → Always whitelisted
 * - FilePackResources: Downloaded server resource packs → Session whitelisted
 * - CompositePackResources: Combined packs (can include server) → Session whitelisted
 * - ModNioResourcePack: Fabric mod packs → Tracked as mod (blocked in exploitable contexts)
 * - PathPackResources: File path packs → Passthrough (not tracked)
 */
@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
    
    @Unique
    private static boolean incognito$loggedOnce = false;
    
    /**
     * Clear translation key cache before loading new language.
     */
    @Inject(method = "loadFrom", at = @At("HEAD"))
    private static void incognito$onLoadStart(ResourceManager resourceManager, List<String> filenames, 
            boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        ModTracker.clearTranslationKeys();
        incognito$loggedOnce = false;
    }
    
    /**
     * Mark initialization complete after loading.
     */
    @Inject(method = "loadFrom", at = @At("RETURN"))
    private static void incognito$onLoadComplete(ResourceManager resourceManager, List<String> filenames,
            boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        ModTracker.markInitialized();
        
        if (!incognito$loggedOnce) {
            incognito$loggedOnce = true;
            Incognito.LOGGER.info("[Incognito] Translation key tracking: {} vanilla, {} server pack, {} total",
                ModTracker.getVanillaKeyCount(),
                ModTracker.getServerPackKeyCount(),
                ModTracker.getTranslationKeyCount());
        }
    }
    
    /**
     * Intercept language file loading to track keys by source.
     * Uses instanceof checks for reliable pack type detection.
     */
    @WrapOperation(
        method = "appendFrom",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"),
        require = 0
    )
    private static void incognito$trackTranslationKeys(
            InputStream stream, 
            BiConsumer<String, String> output, 
            Operation<Void> original,
            @Local Resource resource) {
        
        PackResources pack = resource.source();
        
        // Vanilla pack - always whitelist
        if (pack instanceof VanillaPackResources) {
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModTracker.recordVanillaTranslationKey(key);
                output.accept(key, value);
            });
            return;
        }
        
        // Server resource pack (downloaded) or composite pack - session whitelist
        // These are packs the server requires, clean clients resolve them normally
        if (pack instanceof FilePackResources || pack instanceof CompositePackResources) {
            Set<String> serverKeys = new HashSet<>();
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModTracker.recordServerPackTranslationKey(key);
                serverKeys.add(key);
                output.accept(key, value);
            });
            
            if (!serverKeys.isEmpty()) {
                Incognito.LOGGER.debug("[Incognito] Whitelisted {} server pack translation keys", serverKeys.size());
            }
            return;
        }
        
        // Path pack resources - passthrough without tracking (like ExploitPreventer)
        if (pack instanceof PathPackResources) {
            original.call(stream, output);
            return;
        }
        
        // Fabric mod pack - track as mod (will be blocked in exploitable contexts)
        if (pack instanceof ModNioResourcePack modPack) {
            String modId = modPack.getFabricModMetadata().getId();
            Set<String> modKeys = new HashSet<>();
            
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModTracker.recordTranslationKey(modId, key);
                modKeys.add(key);
                output.accept(key, value);
            });
            
            Incognito.LOGGER.debug("[Incognito] Tracked {} translation keys from mod '{}'", modKeys.size(), modId);
            return;
        }
        
        // Unknown pack type - try to determine mod ID, otherwise track as unknown
        String modId = incognito$getModIdFromPack(pack);
        if (modId != null) {
            Set<String> modKeys = new HashSet<>();
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModTracker.recordTranslationKey(modId, key);
                modKeys.add(key);
                output.accept(key, value);
            });
            
            Incognito.LOGGER.debug("[Incognito] Tracked {} translation keys from mod '{}' (via class detection)", 
                modKeys.size(), modId);
        } else {
            // Completely unknown - passthrough but log warning
            Incognito.LOGGER.debug("[Incognito] Unknown pack type: {} - passing through without tracking", 
                pack.getClass().getName());
            original.call(stream, output);
        }
    }
    
    /**
     * Try to get mod ID from a pack using class-based detection.
     */
    @Unique
    private static String incognito$getModIdFromPack(PackResources pack) {
        if (pack == null) return null;
        
        // Try reflection for Fabric's mod resource packs that don't match ModNioResourcePack
        try {
            var method = pack.getClass().getMethod("getFabricModMetadata");
            var metadata = method.invoke(pack);
            if (metadata != null) {
                var getIdMethod = metadata.getClass().getMethod("getId");
                return (String) getIdMethod.invoke(metadata);
            }
        } catch (Exception e) {
            // Not a Fabric mod pack or reflection failed
        }
        
        // Fall back to class-based detection
        return ModIdResolver.getModIdFromClass(pack.getClass());
    }
}
