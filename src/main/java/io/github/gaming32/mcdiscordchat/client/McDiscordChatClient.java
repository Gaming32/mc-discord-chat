package io.github.gaming32.mcdiscordchat.client;

import io.github.gaming32.mcdiscordchat.McDiscordChat;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;

import java.util.HashSet;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class McDiscordChatClient implements ClientModInitializer {
    public static final Int2LongMap EMOJI_IDS = new Int2LongOpenHashMap();
    public static final Set<String> EMOJI_NAMES = new HashSet<>();

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
    }

    private static void registerEmoji(PacketByteBuf buf) {
        final long id = buf.readVarLong();
        final int cp = buf.readUnsignedShort() + McDiscordChat.PUA_FIRST;
        EMOJI_IDS.put(cp, id);
    }

    private static void registerEmojiName(PacketByteBuf buf) {
        EMOJI_NAMES.add(buf.readString());
    }
}
