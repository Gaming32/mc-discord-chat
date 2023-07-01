package io.github.gaming32.mcdiscordchat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import io.github.gaming32.mcdiscordchat.client.SuggestorUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {
    @Shadow @Final EditBox input;

    @Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow @Final Minecraft minecraft;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(
        method = "updateCommandInfo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/CommandSuggestions;getLastWordIndex(Ljava/lang/String;)I"
        ),
        cancellable = true
    )
    private void customSuggestions(CallbackInfo ci) {
        final String text = input.getValue().substring(0, input.getCursorPosition());
        emojiSuggestions(ci, text);
        if (!ci.isCancelled()) {
            pingSuggestions(ci, text);
        }
        if (ci.isCancelled() && pendingSuggestions != null) {
            pendingSuggestions.thenRun(() -> {
                if (pendingSuggestions.isDone() && minecraft.options.autoSuggestions().get()) {
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
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(McDiscordChatClient.pingSuggestionsTransactionId++);
        buf.writeUtf(text);
        if (ClientPlayNetworking.canSend(McDiscordChat.CHAT_PING_AUTOCOMPLETE)) {
            ClientPlayNetworking.send(McDiscordChat.CHAT_PING_AUTOCOMPLETE, buf);
        }
    }

    @Redirect(
        method = "showSuggestions",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"
        )
    )
    private int getSuggestionText(Font instance, String text) {
        final Object useText = SuggestorUtil.getSuggestionText(input.getValue(), text, 0xffffff);
        if (useText instanceof String) {
            return instance.width((String)useText);
        }
        return instance.width((Component)useText);
    }

    @Mixin(CommandSuggestions.SuggestionsList.class)
    public static class MixinSuggestionsList {
        @Shadow @Final private String originalContents;

        @Redirect(
            method = "render",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/Font;drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I"
            )
        )
        private int drawCustomSuggestion(Font instance, PoseStack matrices, String text, float x, float y, int color) {
            final Object useText = SuggestorUtil.getSuggestionText(originalContents, text, color);
            if (useText instanceof String) {
                return instance.drawShadow(matrices, (String)useText, x, y, color);
            }
            return instance.drawShadow(matrices, (Component)useText, x, y, color);
        }
    }
}
