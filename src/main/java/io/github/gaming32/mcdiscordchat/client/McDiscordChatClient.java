package io.github.gaming32.mcdiscordchat.client;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.gaming32.mcdiscordchat.McDiscordChat;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.hud.ChatMessageTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class McDiscordChatClient implements ClientModInitializer {
    public static final Int2LongMap EMOJI_IDS = new Int2LongOpenHashMap();
    public static final Map<String, String> EMOJI_NAMES = new HashMap<>();

    public static int pingSuggestionsTransactionId;
    public static CompletableFuture<Suggestions> pingSuggestionsFuture;
    public static Map<String, Text> pingSuggestionsDisplays = Map.of();

    public static final ChatMessageTag DISCORD_TAG = new ChatMessageTag(
        0x7289da, null, Text.translatable("chat.tag.discord"), "Discord"
    );

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_CHARS, (client, handler, buf, responseSender) -> {
            EMOJI_IDS.clear();
            final int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                registerEmoji(buf);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_CHAR, (client, handler, buf, responseSender) ->
            registerEmoji(buf)
        );

        ClientPlayNetworking.registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_NAMES, (client, handler, buf, responseSender) -> {
            EMOJI_NAMES.clear();
            final int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                registerEmojiName(buf);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_NAME, (client, handler, buf, responseSender) ->
            registerEmojiName(buf)
        );

        ClientPlayNetworking.registerGlobalReceiver(McDiscordChat.CHAT_DISCORD_MESSAGE, (client, handler, buf, responseSender) -> {
            final Text message = buf.readText();
            client.inGameHud.getChatHud().addMessage(message, null, DISCORD_TAG);
            client.getChatNarratorManager().narrate(message);
        });

        ClientPlayNetworking.registerGlobalReceiver(McDiscordChat.CHAT_PING_AUTOCOMPLETE, (client, handler, buf, responseSender) -> {
            final int transactionId = buf.readVarInt();
            if (transactionId != pingSuggestionsTransactionId - 1) return;
            final int rangeStart = buf.readVarInt();
            final int rangeLength = buf.readVarInt();
            final StringRange range = new StringRange(rangeStart, rangeStart + rangeLength);
            final Suggestions suggestions = new Suggestions(range, buf.readList(buf2 -> new Suggestion(range, buf2.readString())));
            pingSuggestionsDisplays = buf.readMap(PacketByteBuf::readString, PacketByteBuf::readText);
            pingSuggestionsFuture.complete(suggestions);
            pingSuggestionsFuture = null;
        });
    }

    private static void registerEmoji(PacketByteBuf buf) {
        final long id = buf.readVarLong();
        final int cp = buf.readUnsignedShort() + McDiscordChat.PUA_FIRST;
        EMOJI_IDS.put(cp, id);
    }

    private static void registerEmojiName(PacketByteBuf buf) {
        EMOJI_NAMES.put(buf.readString(), Character.toString(buf.readVarInt()));
    }
}
