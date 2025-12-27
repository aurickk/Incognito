package incognito.mod.mixin.client;

import incognito.mod.detection.ExploitDetector;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tracks player interactions with signs and anvils.
 * This helps distinguish between player-initiated and server-initiated screen openings.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void incognito$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                             CallbackInfoReturnable<InteractionResult> cir) {
        Level level = player.level();
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        
        // Track sign interactions
        if (state.getBlock() instanceof SignBlock) {
            ExploitDetector.markSignInteraction();
        }
        
        // Track anvil interactions
        if (state.getBlock() instanceof AnvilBlock) {
            ExploitDetector.markAnvilInteraction();
        }
    }
}
