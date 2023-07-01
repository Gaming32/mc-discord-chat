package io.github.gaming32.mcdiscordchat.client;

import io.github.gaming32.mcdiscordchat.util.ColorUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

public class SuggestorUtil {
    public static Object getSuggestionText(String typedText, String text, int color) {
        if (typedText.startsWith("/") || text.isEmpty()) {
            return text;
        }
        if (text.endsWith(":")) {
            final String cutText = text.substring(0, text.length() - 1);
            final String emoji = McDiscordChatClient.EMOJI_NAMES.get(cutText);
            if (emoji != null) {
                return emoji + ' ' + cutText;
            }
        }
        Component pingText = McDiscordChatClient.pingSuggestionsDisplays.get(text);
        if (pingText != null) {
            if (!ColorUtil.isGrayscale(color)) {
                pingText = pingText.copy().withStyle(style -> style.withColor((TextColor)null));
            }
            return pingText;
        }
        return text;
    }
}
