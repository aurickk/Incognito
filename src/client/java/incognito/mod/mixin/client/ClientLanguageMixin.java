package incognito.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import incognito.mod.Incognito;
import incognito.mod.tracking.ModIdResolver;
import incognito.mod.tracking.ModTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.PackResources;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Mixin to track translation keys from language files.
 * Records which mod each translation key comes from, enabling whitelist-based protection.
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
            Incognito.LOGGER.info("[Incognito] Translation key tracking initialized: {} keys tracked",
                ModTracker.getTranslationKeyCount());
        }
    }
    
    /**
     * Intercept language file loading to track keys by source.
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
        
        String modId = incognito$getModIdFromResource(resource);
        
        if (modId == null || modId.equals("minecraft")) {
            // Vanilla - track as vanilla keys
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModTracker.recordVanillaTranslationKey(key);
                output.accept(key, value);
            });
        } else {
            // Mod - track with mod ID
            Set<String> modKeys = new HashSet<>();
            final String finalModId = modId;
            
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModTracker.recordTranslationKey(finalModId, key);
                modKeys.add(key);
                output.accept(key, value);
            });
            
            Incognito.LOGGER.debug("[Incognito] Loaded {} translation keys from mod '{}'", 
                modKeys.size(), modId);
        }
    }
    
    /**
     * Get mod ID from a Resource's pack source.
     */
    @Unique
    private static String incognito$getModIdFromResource(Resource resource) {
        if (resource == null) {
            return null;
        }
        
        try {
            PackResources pack = resource.source();
            if (pack == null) {
                return null;
            }
            
            String packId = pack.packId();
            
            // Check for vanilla pack types
            if (packId != null && (packId.equals("vanilla") || packId.equals("minecraft"))) {
                return "minecraft";
            }
            
            // Try to get mod ID from Fabric
            String packClassName = pack.getClass().getName();
            
            // Fabric's mod resource pack
            if (packClassName.contains("ModNioPackResources") || 
                packClassName.contains("ModResourcePackCreator")) {
                // Try reflection to get mod metadata
                try {
                    var method = pack.getClass().getMethod("getFabricModMetadata");
                    var metadata = method.invoke(pack);
                    if (metadata != null) {
                        var getIdMethod = metadata.getClass().getMethod("getId");
                        return (String) getIdMethod.invoke(metadata);
                    }
                } catch (Exception e) {
                    // Reflection failed, try pack ID
                }
            }
            
            // Try to match pack ID to mod container
            if (packId != null) {
                Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer(packId);
                if (modContainer.isPresent()) {
                    return modContainer.get().getMetadata().getId();
                }
                
                // Sometimes pack ID is like "fabric:modid" or "file/modid"
                if (packId.contains(":")) {
                    String possibleModId = packId.substring(packId.indexOf(':') + 1);
                    modContainer = FabricLoader.getInstance().getModContainer(possibleModId);
                    if (modContainer.isPresent()) {
                        return modContainer.get().getMetadata().getId();
                    }
                }
            }
            
            // Fall back to class-based detection
            return ModIdResolver.getModIdFromClass(pack.getClass());
            
        } catch (Exception e) {
            Incognito.LOGGER.debug("[Incognito] Failed to get mod ID from resource: {}", e.getMessage());
            return null;
        }
    }
}

