package io.github.gaming32.mcdiscordchat.client;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.mixin.client.ChatHudAccessor;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudMessage;
import net.minecraft.client.gui.hud.ChatMessageTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class McDiscordChatClient implements ClientModInitializer {
    public static final Int2LongMap EMOJI_IDS = new Int2LongOpenHashMap();
    public static final Map<String, String> EMOJI_NAMES = new HashMap<>();

    public static int pingSuggestionsTransactionId;
    public static CompletableFuture<Suggestions> pingSuggestionsFuture;
    public static final Map<String, Text> pingSuggestionsDisplays = new HashMap<>();

    private static final Long2ObjectMap<ChatMessageInfo> CHAT_MESSAGES = new Long2ObjectOpenHashMap<>();
    public static final Map<ChatHudMessage, ChatMessageInfo> CHAT_MESSAGES_GUI_LOOKUP = new HashMap<>();
    @Nullable
    public static ChatMessageInfo hoveredChatMessage;

    public static final ChatMessageTag DISCORD_TAG = new ChatMessageTag(
        0x7289da, null, Text.translatable("chat.tag.discord"), "Discord"
    );

    @Override
    public void onInitializeClient() {
        registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_CHARS, (client, handler, buf, responseSender) -> {
            EMOJI_IDS.clear();
            final int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                registerEmoji(buf);
            }
        });

        registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_CHAR, (client, handler, buf, responseSender) ->
            registerEmoji(buf)
        );

        registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_NAMES, (client, handler, buf, responseSender) -> {
            EMOJI_NAMES.clear();
            final int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                registerEmojiName(buf);
            }
        });

        registerGlobalReceiver(McDiscordChat.EMOJI_SYNC_NAME, (client, handler, buf, responseSender) ->
            registerEmojiName(buf)
        );

        registerGlobalReceiver(McDiscordChat.CHAT_DISCORD_MESSAGE, (client, handler, buf, responseSender) -> {
            final Text message = buf.readText();
            client.inGameHud.getChatHud().addMessage(message, null, DISCORD_TAG);
            client.getChatNarratorManager().narrate(message);
            applyMessageInfo(client, buf);
        });

        registerGlobalReceiver(McDiscordChat.CHAT_PING_AUTOCOMPLETE, (client, handler, buf, responseSender) -> {
            final int transactionId = buf.readVarInt();
            if (transactionId != pingSuggestionsTransactionId - 1) return;
            final int rangeStart = buf.readVarInt();
            final int rangeLength = buf.readVarInt();
            final StringRange range = new StringRange(rangeStart, rangeStart + rangeLength);
            final Suggestions suggestions = new Suggestions(range, buf.readList(buf2 -> new Suggestion(range, buf2.readString())));
            pingSuggestionsDisplays.putAll(buf.readMap(PacketByteBuf::readString, PacketByteBuf::readText));
            pingSuggestionsFuture.complete(suggestions);
            pingSuggestionsFuture = null;
        });

        registerGlobalReceiver(McDiscordChat.CHAT_MESSAGE_ID, (client, handler, buf, responseSender) ->
            applyMessageInfo(client, buf)
        );

        registerGlobalReceiver(McDiscordChat.CHAT_MESSAGE_EDIT, (client, handler, buf, responseSender) -> {
            final long id = buf.readVarLong();
            final Text updatedContents = buf.readText();
            final ChatMessageInfo message = CHAT_MESSAGES.get(id);
            if (message == null) return;
            final ChatHudAccessor chatHud = (ChatHudAccessor)client.inGameHud.getChatHud();
            final int index = chatHud.getMessages().indexOf(message.getMessage());
            if (index == -1) return;
            final ChatHudMessage newMessage = new ChatHudMessage(
                message.getMessage().addedTime(),
                updatedContents,
                null,
                message.getMessage().tag()
            );
            chatHud.getMessages().set(index, newMessage);
            chatHud.refreshMessageRenders();
            CHAT_MESSAGES_GUI_LOOKUP.remove(message.getMessage());
            CHAT_MESSAGES_GUI_LOOKUP.put(newMessage, message);
            message.setMessage(newMessage);
        });

        registerGlobalReceiver(McDiscordChat.CHAT_MESSAGE_REMOVE, (client, handler, buf, responseSender) -> {
            final long id = buf.readVarLong();
            final ChatMessageInfo message = CHAT_MESSAGES.remove(id);
            if (message == null) return;
            final ChatHudAccessor chatHud = (ChatHudAccessor)client.inGameHud.getChatHud();
            if (chatHud.getMessages().remove(message.getMessage())) {
                chatHud.refreshMessageRenders();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CHAT_MESSAGES.clear();
            CHAT_MESSAGES_GUI_LOOKUP.clear();
        });
    }

    private static void registerGlobalReceiver(Identifier packet, ClientPlayNetworking.PlayChannelHandler packetHandler) {
        ClientPlayNetworking.registerGlobalReceiver(packet, (client, handler, buf, responseSender) -> {
            if (client.isOnThread()) {
                packetHandler.receive(client, handler, buf, responseSender);
            } else {
                final PacketByteBuf newBuf = new PacketByteBuf(buf.copy());
                client.executeIfPossible(() -> {
                    if (handler.getConnection().isOpen()) {
                        try {
                            packetHandler.receive(client, handler, newBuf, responseSender);
                        } catch (Exception e) {
                            if (handler.shouldPropagateHandlingExceptions()) {
                                throw e;
                            }

                            McDiscordChat.LOGGER.error("Failed to handle packet " + packet + ", suppressing error", e);
                        }
                    } else {
                        McDiscordChat.LOGGER.debug("Ignoring packet due to disconnection: {}", packet);
                    }
                });
            }
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

    private static void applyMessageInfo(MinecraftClient client, PacketByteBuf buf) {
        final long id = buf.readVarLong();
        final int flags = buf.readVarInt();
        final ChatHudMessage message = ((ChatHudAccessor)client.inGameHud.getChatHud()).getMessages().get(0);
        final ChatMessageInfo messageInfo = new ChatMessageInfo(id, message, flags);
        CHAT_MESSAGES.put(id, messageInfo);
        CHAT_MESSAGES_GUI_LOOKUP.put(message, messageInfo);
    }
}
