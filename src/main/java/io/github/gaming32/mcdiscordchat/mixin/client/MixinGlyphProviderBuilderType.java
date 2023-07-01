package io.github.gaming32.mcdiscordchat.mixin.client;

import com.google.gson.JsonObject;
import io.github.gaming32.mcdiscordchat.client.EmojiFont;
import net.minecraft.client.gui.font.providers.GlyphProviderBuilder;
import net.minecraft.client.gui.font.providers.GlyphProviderBuilderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphProviderBuilderType.class)
public class MixinGlyphProviderBuilderType {
    private static final String CUSTOM_ID = "discord_emojis";

    @Inject(method = "byName", at = @At("HEAD"), cancellable = true)
    private static void dontError(String id, CallbackInfoReturnable<GlyphProviderBuilderType> cir) {
        if (id.equals(CUSTOM_ID)) {
            cir.setReturnValue(GlyphProviderBuilderType.SPACE);
        }
    }

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void customLoader(JsonObject json, CallbackInfoReturnable<GlyphProviderBuilder> cir) {
        if (json.getAsJsonPrimitive("type").getAsString().equals(CUSTOM_ID)) {
            cir.setReturnValue(resourceManager -> new EmojiFont());
        }
    }
}
