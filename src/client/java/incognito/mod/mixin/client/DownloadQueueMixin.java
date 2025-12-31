package incognito.mod.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import incognito.mod.Incognito;
import incognito.mod.config.IncognitoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Isolates resource pack cache per-account to prevent cross-account fingerprinting.
 */
@Mixin(DownloadQueue.class)
public class DownloadQueueMixin {
    @Unique
    private static volatile boolean incognito$loggedOnce = false;
    
    @Unique
    private static volatile UUID incognito$lastLoggedAccountId = null;

    @ModifyExpressionValue(
        method = "*",
        at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"),
        require = 0
    )
    private Path incognito$isolatePackPath(Path original) {
        if (!IncognitoConfig.getInstance().shouldIsolatePackCache()) {
            return original;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return original;
        }
        
        var user = mc.getUser();
        if (user == null) {
            return original;
        }

        UUID accountId = user.getProfileId();
        if (accountId == null) {
            if (!incognito$loggedOnce) {
                Incognito.LOGGER.warn("[Incognito] Failed to isolate resource pack cache - account UUID is null");
                incognito$loggedOnce = true;
            }
            return original;
        }

        Path parent = original.getParent();
        String packIdStr = original.getFileName().toString();
        
        if (parent != null && isUuidLike(packIdStr)) {
            Path isolatedPath = parent.resolve(accountId.toString()).resolve(packIdStr);
            
            if (incognito$lastLoggedAccountId == null || !incognito$lastLoggedAccountId.equals(accountId)) {
                incognito$lastLoggedAccountId = accountId;
                Incognito.LOGGER.info("[Incognito] Resource pack cache isolated per-account: {}",
                    parent.resolve(accountId.toString()));
            }
            
            return isolatedPath;
        }

        return original;
    }

    @Unique
    private static boolean isUuidLike(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return str.length() == 32 && str.matches("[0-9a-fA-F]+");
        }
    }
}

