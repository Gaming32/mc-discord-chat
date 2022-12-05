package io.github.gaming32.mcdiscordchat.mixin.client;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.CommandSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
    private void emojiSuggestions(CallbackInfo ci) {
        final String text = textField.getText().substring(0, textField.getCursor());
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
        final SuggestionsBuilder builder = new SuggestionsBuilder(text, text.lastIndexOf(':') + 1);
        final String toMatch = builder.getRemainingLowerCase();
        McDiscordChatClient.EMOJI_NAMES
            .keySet()
            .stream()
            .filter(emoji -> emoji.toLowerCase(Locale.ROOT).startsWith(toMatch))
            .forEach(emoji -> builder.suggest(emoji + ':'));
        pendingSuggestions = builder.buildFuture();
        pendingSuggestions.thenRun(() -> {
            if (pendingSuggestions.isDone() && client.options.getCommandSuggestions().get()) {
                showSuggestions(false);
            }
        });
    }

    @Redirect(
        method = "showSuggestions",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/brigadier/suggestion/Suggestion;getText()Ljava/lang/String;"
        )
    )
    private String getSuggestionText(Suggestion instance) {
        final String text = instance.getText();
        if (textField.getText().startsWith("/") || text.isEmpty()) return text;
        final String cutText = text.substring(0, text.length() - 1);
        final String emoji = McDiscordChatClient.EMOJI_NAMES.get(cutText);
        if (emoji == null) return text; // Probably not an emoji
        return emoji + ' ' + cutText;
    }

    @Mixin(CommandSuggestor.SuggestionWindow.class)
    public static class MixinSuggestionWindow {
        @Shadow @Final private String typedText;

        @Redirect(
            method = "render",
            at = @At(
                value = "INVOKE",
                target = "Lcom/mojang/brigadier/suggestion/Suggestion;getText()Ljava/lang/String;"
            )
        )
        private String getSuggestionText(Suggestion instance) {
            final String text = instance.getText();
            if (typedText.startsWith("/") || text.isEmpty()) return text;
            final String cutText = text.substring(0, text.length() - 1);
            final String emoji = McDiscordChatClient.EMOJI_NAMES.get(cutText);
            if (emoji == null) return text; // Probably not an emoji
            return emoji + ' ' + cutText;
        }
    }
}
