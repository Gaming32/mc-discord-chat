package io.github.gaming32.mcdiscordchat;

import com.google.gson.stream.JsonReader;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class McDiscordChat implements ModInitializer {
    public static final String MOD_ID = "mc-discord-chat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String NAMESPACE = "discordchat";
    public static final Identifier EMOJI_SYNC_ALL = new Identifier(NAMESPACE, "emoji/sync_all");
    public static final Identifier EMOJI_SYNC_ONE = new Identifier(NAMESPACE, "emoji/sync_one");

    public static final Map<String, String> EMOJI_SHORTCODES = new HashMap<>();

    public static final Long2IntMap EMOJI_CHARS = new Long2IntOpenHashMap();
    public static final int PUA_FIRST = 0xF0000;
    public static final int PUA_LAST = 0xFFFFD;
    public static final int UNKNOWN_EMOJI_CP = PUA_FIRST;
    private static int nextEmojiCP = UNKNOWN_EMOJI_CP + 1;


    @Override
    public void onInitialize() {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(
            FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow()
                .findPath("assets/" + MOD_ID + "/emojis.json")
                .orElseThrow(() -> new IOException("Missing emojis.json")),
            StandardCharsets.UTF_8
        ))) {
            reader.beginObject();
            while (reader.hasNext()) {
                EMOJI_SHORTCODES.put(reader.nextName(), reader.nextString());
            }
            reader.endObject();
        } catch (IOException e) {
            LOGGER.error("Could not load emojis.json", e);
        }

        ServerMessageDecoratorEvent.EVENT.register((serverPlayerEntity, text) ->
            CompletableFuture.completedFuture(Text.literal(parseEmojis(text.getString())))
        );

        LOGGER.info("Initialized " + MOD_ID);
    }

    public static String parseEmojis(String text) {
        final StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\\') {
                if (i < text.length() - 1 && text.charAt(i + 1) == ':') {
                    i++;
                    continue;
                }
            } else if (c == ':') {
                final int nextColon = text.indexOf(':', i + 1);
                if (nextColon != -1) {
                    final String emoji = EMOJI_SHORTCODES.get(text.substring(i + 1, nextColon));
                    if (emoji != null) {
                        result.append(emoji);
                        i = nextColon + 1;
                        continue;
                    }
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    public static int getEmojiCP(long emojiId) {
        return EMOJI_CHARS.computeIfAbsent(emojiId, key -> {
            final int emojiCP = nextEmojiCP++;
            if (emojiCP > PUA_LAST) {
                nextEmojiCP--;
                return PUA_FIRST;
            }
            return PUA_LAST;
        });
    }
}
