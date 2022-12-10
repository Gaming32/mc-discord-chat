package io.github.gaming32.mcdiscordchat;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonWriter;

import java.io.IOException;

public final class DiscordChatConfig {
    @NotNull
    private String botToken = "";

    private long messageChannel = 0L;

    @NotNull
    private String webhookUrl = "";

    private boolean opsAreDiscordModerators = true;

//    private boolean forceTimestampMessageKey = false;

    @NotNull
    private final Object2LongMap<String> extraCustomEmojis = new Object2LongOpenHashMap<>();

    public void read(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            final String key;
            switch (key = reader.nextName()) {
                case "botToken" -> botToken = reader.nextString();
                case "messageChannel" -> messageChannel = reader.nextLong();
                case "webhookUrl" -> webhookUrl = reader.nextString();
                case "opsAreDiscordModerators" -> opsAreDiscordModerators = reader.nextBoolean();
//                case "forceTimestampMessageKey" -> forceTimestampMessageKey = reader.nextBoolean();
                case "extraCustomEmojis" -> {
                    extraCustomEmojis.clear();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        extraCustomEmojis.put(reader.nextName(), reader.nextLong());
                    }
                    reader.endObject();
                }
                default -> {
                    McDiscordChat.LOGGER.warn("Unknown config key: {}", key);
                    reader.skipValue();
                }
            }
        }
        reader.endObject();
    }

    public void write(JsonWriter writer) throws IOException {
        writer.beginObject(); {
            writer.comment("The Discord bot token to use");
            writer.name("botToken").value(botToken);

            writer.comment("The channel to sync messages with");
            writer.name("messageChannel").value(messageChannel);

            writer.comment("The webhook to send messages as");
            writer.name("webhookUrl").value(webhookUrl);

            writer.comment("Whether server operators (of level 2) can delete messages sent by Discord users");
            writer.name("opsAreDiscordModerators").value(opsAreDiscordModerators);

//            writer.comment("Force the use of timestamps to identify all messages.");
//            writer.comment("Normally the message signature is used, unless it's stripped, such as by No Chat Reports.");
//            writer.name("forceTimestampMessageKey").value(forceTimestampMessageKey);

            writer.comment("Extra emojis to include for Minecraft");
            writer.name("extraCustomEmojis").beginObject(); {
                for (final Object2LongMap.Entry<String> entry : extraCustomEmojis.object2LongEntrySet()) {
                    writer.name(entry.getKey()).value(entry.getLongValue());
                }
            } writer.endObject();
        } writer.endObject();
    }

    @NotNull
    public String getBotToken() {
        return botToken;
    }

    public long getMessageChannel() {
        return messageChannel;
    }

    @NotNull
    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(@NotNull String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public boolean areOpsDiscordModerators() {
        return opsAreDiscordModerators;
    }

//    public boolean forceTimestampMessageKey() {
//        return forceTimestampMessageKey;
//    }

    @NotNull
    public Object2LongMap<String> getExtraCustomEmojis() {
        return extraCustomEmojis;
    }
}
