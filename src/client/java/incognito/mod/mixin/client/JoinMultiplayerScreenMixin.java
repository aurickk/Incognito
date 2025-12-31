package incognito.mod.mixin.client;

import incognito.mod.config.IncognitoConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an Incognito settings button to the multiplayer server list screen.
 * Button is fixed to the top-right of the footer area.
 * 
 * No resize handling needed - init() is called during resize which recreates
 * the button at the correct position.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    
    @Unique private static final int BUTTON_WIDTH = 70;
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int MARGIN = 7;
    
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void incognito$addSettingsButton(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(
            Component.literal("Incognito"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new IncognitoConfigScreen(this));
                }
            }
        )
        .bounds(this.width - BUTTON_WIDTH - MARGIN, this.height - 56, BUTTON_WIDTH, BUTTON_HEIGHT)
        .tooltip(Tooltip.create(Component.literal("Open Incognito settings")))
        .build());
    }
}

