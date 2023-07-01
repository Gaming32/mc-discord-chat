package io.github.gaming32.mcdiscordchat.client;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.MessageKey;
import io.github.gaming32.mcdiscordchat.mixin.client.ChatComponentAccessor;
import io.github.gaming32.mcdiscordchat.mixin.client.ChatScreenAccessor;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class McDiscordChatClient implements ClientModInitializer {
    public static final Int2LongMap EMOJI_IDS = new Int2LongOpenHashMap();
    public static final Map<String, String> EMOJI_NAMES = new HashMap<>();

    public static int pingSuggestionsTransactionId;
    public static CompletableFuture<Suggestions> pingSuggestionsFuture;
    public static final Map<String, Component> pingSuggestionsDisplays = new HashMap<>();

    private static final Map<MessageKey<?>, ChatMessageInfo> CHAT_MESSAGES = new HashMap<>();
    public static final Map<GuiMessage, ChatMessageInfo> CHAT_MESSAGES_GUI_LOOKUP = new WeakHashMap<>();
    @Nullable
    public static ChatMessageInfo hoveredChatMessage;

    public static final GuiMessageTag DISCORD_TAG = new GuiMessageTag(
        0x7289da, null, Component.translatable("chat.tag.discord"), "Discord"
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
            final Component message = buf.readComponent();
            final MessageKey<?> messageKey = MessageKey.read(buf);
            final int permissions = buf.readVarInt();
            client.gui.getChat().addMessage(message, null, DISCORD_TAG);
//            client.getChatNarratorManager().narrate(message);
            final GuiMessage hudMessage = getChatHudMessages(client).get(0);
            final ChatMessageInfo messageInfo = getChatMessageInfo(messageKey);
            messageInfo.setPermissions(permissions);
            messageInfo.setHudMessage(hudMessage);
            CHAT_MESSAGES_GUI_LOOKUP.put(hudMessage, messageInfo);
        });

        registerGlobalReceiver(McDiscordChat.CHAT_PING_AUTOCOMPLETE, (client, handler, buf, responseSender) -> {
            final int transactionId = buf.readVarInt();
            if (transactionId != pingSuggestionsTransactionId - 1) return;
            final int rangeStart = buf.readVarInt();
            final int rangeLength = buf.readVarInt();
            final StringRange range = new StringRange(rangeStart, rangeStart + rangeLength);
            final Suggestions suggestions = new Suggestions(range, buf.readList(buf2 -> new Suggestion(range, buf2.readUtf())));
            pingSuggestionsDisplays.putAll(buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readComponent));
            pingSuggestionsFuture.complete(suggestions);
            pingSuggestionsFuture = null;
        });

        registerGlobalReceiver(McDiscordChat.CHAT_MESSAGE_EDIT, (client, handler, buf, responseSender) -> {
            final MessageKey<?> key = MessageKey.read(buf);
            final Component updatedContents = buf.readComponent();
            final ChatMessageInfo message = getChatMessageInfo(key);
            final ChatComponentAccessor chatHud = (ChatComponentAccessor)client.gui.getChat();
            final int index = chatHud.getAllMessages().indexOf(message.getHudMessage());
            if (index == -1) return;
            final GuiMessage newMessage = new GuiMessage(
                message.getHudMessage().addedTime(),
                updatedContents,
                null,
                message.getHudMessage().tag()
            );
            chatHud.getAllMessages().set(index, newMessage);
            chatHud.invokeRefreshTrimmedMessage();
            CHAT_MESSAGES_GUI_LOOKUP.remove(message.getHudMessage());
            CHAT_MESSAGES_GUI_LOOKUP.put(newMessage, message);
            message.setHudMessage(newMessage);
        });

        registerGlobalReceiver(McDiscordChat.CHAT_MESSAGE_REMOVE, (client, handler, buf, responseSender) -> {
            final MessageKey<?> key = MessageKey.read(buf);
            final ChatMessageInfo message = CHAT_MESSAGES.remove(key);
            if (message == null) return;
            final ChatComponentAccessor chatHud = (ChatComponentAccessor)client.gui.getChat();
            if (chatHud.getAllMessages().remove(message.getHudMessage())) {
                chatHud.invokeRefreshTrimmedMessage();
            }
        });

        registerGlobalReceiver(McDiscordChat.CHAT_MESSAGE_ORIGINAL, (client, handler, buf, responseSender) -> {
            if (client.screen instanceof ChatScreenAccessor chatScreen) {
                chatScreen.getInput().setValue(buf.readUtf());
            }
        });

        registerGlobalReceiver(McDiscordChat.CHAT_MESSAGE_PERMISSIONS, (client, handler, buf, responseSender) ->
            getChatMessageInfo(MessageKey.read(buf)).setPermissions(buf.readVarInt())
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CHAT_MESSAGES.clear();
            CHAT_MESSAGES_GUI_LOOKUP.clear();
        });
    }

    private static void registerGlobalReceiver(ResourceLocation packet, ClientPlayNetworking.PlayChannelHandler packetHandler) {
        ClientPlayNetworking.registerGlobalReceiver(packet, (client, handler, buf, responseSender) -> {
            if (client.isSameThread()) {
                packetHandler.receive(client, handler, buf, responseSender);
            } else {
                final FriendlyByteBuf newBuf = new FriendlyByteBuf(buf.copy());
                client.executeIfPossible(() -> {
                    if (handler.getConnection().isConnected()) {
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

    private static void registerEmoji(FriendlyByteBuf buf) {
        final long id = buf.readVarLong();
        final int cp = buf.readUnsignedShort() + McDiscordChat.PUA_FIRST;
        EMOJI_IDS.put(cp, id);
    }

    private static void registerEmojiName(FriendlyByteBuf buf) {
        EMOJI_NAMES.put(buf.readUtf(), Character.toString(buf.readVarInt()));
    }

    public static List<GuiMessage> getChatHudMessages(Minecraft client) {
        return ((ChatComponentAccessor)client.gui.getChat()).getAllMessages();
    }

    @NotNull
    public static ChatMessageInfo getChatMessageInfo(MessageKey<?> key) {
        return CHAT_MESSAGES.computeIfAbsent(key, ChatMessageInfo::new);
    }
}
