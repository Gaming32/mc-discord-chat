package io.github.gaming32.mcdiscordchat.mixin.client;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import io.github.gaming32.mcdiscordchat.util.ColorUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.CommandSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestor.class)
public abstract class MixinCommandSuggestor {
    @Shadow @Final TextFieldWidget textField;

    @Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow @Final MinecraftClient client;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(
        method = "refresh",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/CommandSuggestor;getLastPlayerNameStart(Ljava/lang/String;)I"
        ),
        cancellable = true
    )
    private void customSuggestions(CallbackInfo ci) {
        final String text = textField.getText().substring(0, textField.getCursor());
        emojiSuggestions(ci, text);
        if (!ci.isCancelled()) {
            pingSuggestions(ci, text);
        }
        if (ci.isCancelled() && pendingSuggestions != null) {
            pendingSuggestions.thenRun(() -> {
                if (pendingSuggestions.isDone() && client.options.getCommandSuggestions().get()) {
                    showSuggestions(false);
                }
            });
        }
    }

    private void emojiSuggestions(CallbackInfo ci, String text) {
        final int lastColon = text.lastIndexOf(':');
        if (lastColon == -1) return;
        if (lastColon > 0) {
            final int secondLastColon = text.lastIndexOf(':', lastColon - 1);
            if (
                secondLastColon != -1 &&
                    McDiscordChatClient.EMOJI_NAMES.containsKey(
                        text.substring(secondLastColon + 1, lastColon)
                    )
            ) return;
        }
        ci.cancel();
        final SuggestionsBuilder builder = new SuggestionsBuilder(text, lastColon + 1);
        final String toMatch = builder.getRemainingLowerCase();
        McDiscordChatClient.EMOJI_NAMES
            .keySet()
            .stream()
            .filter(emoji -> emoji.toLowerCase(Locale.ROOT).startsWith(toMatch))
            .forEach(emoji -> builder.suggest(emoji + ':'));
        pendingSuggestions = builder.buildFuture();
    }

    private void pingSuggestions(CallbackInfo ci, String text) {
        if (text.indexOf('@') == -1) return;
        ci.cancel();
        pendingSuggestions = McDiscordChatClient.pingSuggestionsFuture = new CompletableFuture<>();
        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(McDiscordChatClient.pingSuggestionsTransactionId++);
        buf.writeString(text);
        if (ClientPlayNetworking.canSend(McDiscordChat.CHAT_PING_AUTOCOMPLETE)) {
            ClientPlayNetworking.send(McDiscordChat.CHAT_PING_AUTOCOMPLETE, buf);
        }
    }

    @Redirect(
        method = "showSuggestions",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/brigadier/suggestion/Suggestion;getText()Ljava/lang/String;"
        )
    )
    private String getSuggestionText(Suggestion instance) {
        return instance.getText(); // TODO: implement like below
    }

    @Mixin(CommandSuggestor.SuggestionWindow.class)
    public static class MixinSuggestionWindow {
        @Shadow @Final private String typedText;

        @Redirect(
            method = "render",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Ljava/lang/String;FFI)I"
            )
        )
        private int drawCustomSuggestion(TextRenderer instance, MatrixStack matrices, String text, float x, float y, int color) {
            if (typedText.startsWith("/") || text.isEmpty()) {
                return instance.drawWithShadow(matrices, text, x, y, color);
            }
            if (text.endsWith(":")) {
                final String cutText = text.substring(0, text.length() - 1);
                final String emoji = McDiscordChatClient.EMOJI_NAMES.get(cutText);
                if (emoji != null) {
                    return instance.drawWithShadow(matrices, emoji + ' ' + cutText, x, y, color);
                }
            }
            Text pingText = McDiscordChatClient.pingSuggestionsDisplays.get(text);
            if (pingText != null) {
                if (!ColorUtil.isGrayscale(color)) {
                    pingText = pingText.copy().styled(style -> style.withColor((TextColor)null));
                }
                return instance.drawWithShadow(matrices, pingText, x, y, color);
            }
            return instance.drawWithShadow(matrices, text, x, y, color);
        }
    }
}
