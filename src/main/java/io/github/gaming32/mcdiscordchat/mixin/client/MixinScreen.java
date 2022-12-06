package io.github.gaming32.mcdiscordchat.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Style;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(Screen.class)
public abstract class MixinScreen {
    @Inject(
        method = "handleTextClick",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"
        ),
        cancellable = true
    )
    private void discordLink(Style style, CallbackInfoReturnable<Boolean> cir) throws IOException {
        //noinspection ConstantConditions
        final String url = style.getClickEvent().getValue();
        if (!url.regionMatches(true, 0, "discord://", 0, 10)) return;
        final Process process = Runtime.getRuntime().exec(switch (Util.getOperatingSystem()) {
            case WINDOWS -> new String[] {"rundll32", "url.dll,FileProtocolHandler", url};
            case OSX -> new String[] {"open", url};
            default -> new String[] {"xdg-open", url};
        });
        process.getInputStream().close();
        process.getErrorStream().close();
        process.getOutputStream().close();
        cir.setReturnValue(true);
    }
}
