package io.github.gaming32.mcdiscordchat.mixin.client;

import com.google.gson.JsonObject;
import io.github.gaming32.mcdiscordchat.client.EmojiFont;
import net.minecraft.client.font.FontLoader;
import net.minecraft.client.font.FontType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontType.class)
public class MixinFontType {
    private static final String CUSTOM_ID = "discord_emojis";

    @Inject(method = "byId", at = @At("HEAD"), cancellable = true)
    private static void dontError(String id, CallbackInfoReturnable<FontType> cir) {
        if (id.equals(CUSTOM_ID)) {
            cir.setReturnValue(FontType.SPACE);
        }
    }

    @Inject(method = "createLoader", at = @At("HEAD"), cancellable = true)
    private void customLoader(JsonObject json, CallbackInfoReturnable<FontLoader> cir) {
        if (json.getAsJsonPrimitive("type").getAsString().equals(CUSTOM_ID)) {
            cir.setReturnValue(resourceManager -> new EmojiFont());
        }
    }
}
