package io.github.gaming32.mcdiscordchat;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.google.gson.stream.JsonReader;
import io.github.gaming32.mcdiscordchat.util.TextVisitor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.AbstractLong2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.component.LiteralComponent;
import net.minecraft.text.component.TranslatableComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class McDiscordChat implements ModInitializer {
    public static final String MOD_ID = "mc-discord-chat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier EMOJI_SYNC_CHARS = new Identifier(MOD_ID, "emoji/sync_chars");
    public static final Identifier EMOJI_SYNC_CHAR = new Identifier(MOD_ID, "emoji/sync_char");
    public static final Identifier EMOJI_SYNC_NAMES = new Identifier(MOD_ID, "emoji/sync_names");
    public static final Identifier EMOJI_SYNC_NAME = new Identifier(MOD_ID, "emoji/sync_name");
    public static final Identifier CHAT_DISCORD_MESSAGE = new Identifier(MOD_ID, "chat/discord_message");

    public static final Identifier PING_SOUND_ID = new Identifier(MOD_ID, "ping");
    public static final SoundEvent PING_SOUND_EVENT = new SoundEvent(PING_SOUND_ID);

    public static final Map<String, String> EMOJI_SHORTCODES = new HashMap<>();

    public static final Long2IntMap EMOJI_CHARS = new Long2IntOpenHashMap();
    private static final Int2ObjectMap<Object2LongMap.Entry<String>> EMOJI_CHARS_REVERSE = new Int2ObjectOpenHashMap<>();
    public static final int PUA_FIRST = 0xF0000;
    public static final int PUA_LAST = 0xFFFFD;
    public static final int UNKNOWN_EMOJI_CP = PUA_FIRST;
    private static int nextEmojiCP = UNKNOWN_EMOJI_CP + 1;

    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json5");
    public static final DiscordChatConfig CONFIG = new DiscordChatConfig();
    public static final Map<String, Long2BooleanMap.Entry> EMOJI_NAMES = new HashMap<>();
    public static final Long2BooleanMap.Entry BUILTIN_EMOJI_MARKER = new AbstractLong2BooleanMap.BasicEntry();

    static MinecraftServer currentServer;

    @Nullable
    public static JDA jda;
    @Nullable
    public static JDAWebhookClient chatWebhook;

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
        for (final String key : EMOJI_SHORTCODES.keySet()) {
            EMOJI_NAMES.put(key, BUILTIN_EMOJI_MARKER);
        }

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (chatWebhook != null) {
                assert jda != null;
                chatWebhook.send(
                    new WebhookMessageBuilder()
                        .setContent(internalToDiscord(
                            message.unsignedContent().orElseGet(() -> message.signedBody().content().decorated())
                        ))
                        .setUsername(sender.getDisplayName().getString())
                        .setAvatarUrl("https://crafatar.com/renders/head/" + sender.getUuid() + "?overlay=true")
                        .setAllowedMentions(
                            AllowedMentions.none()
                                .withParseUsers(true)
                                .withRoles(
                                    jda.getRoleCache()
                                        .stream()
                                        .filter(Role::isMentionable)
                                        .map(Role::getId)
                                        .collect(Collectors.toList())
                                )
                        )
                        .build()
                );
            }
        });

        ServerMessageDecoratorEvent.EVENT.register((serverPlayerEntity, text) ->
            CompletableFuture.completedFuture(parseEmojis(text.getString()))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(EMOJI_CHARS.size());
            for (final Long2IntMap.Entry entry : EMOJI_CHARS.long2IntEntrySet()) {
                writeEmoji(buf, entry.getLongKey(), entry.getIntValue());
            }
            sender.sendPacket(EMOJI_SYNC_CHARS, buf);

            buf = PacketByteBufs.create();
            buf.writeVarInt(EMOJI_NAMES.size());
            for (final var entry : EMOJI_NAMES.entrySet()) {
                buf.writeString(entry.getKey());
                if (entry.getValue() == BUILTIN_EMOJI_MARKER) {
                    buf.writeVarInt(EMOJI_SHORTCODES.get(entry.getKey()).codePointAt(0));
                } else {
                    buf.writeVarInt(EMOJI_CHARS.get(entry.getValue().getLongKey()));
                }
            }
            sender.sendPacket(EMOJI_SYNC_NAMES, buf);

            if (chatWebhook != null) {
                chatWebhook.send(handler.player.getDisplayName().getString() + " joined the game.");
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (chatWebhook != null) {
                chatWebhook.send(handler.player.getDisplayName().getString() + " left the game.");
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            readConfig();

            EMOJI_NAMES.keySet().retainAll(EMOJI_SHORTCODES.keySet());
            for (final Object2LongMap.Entry<String> extraCustom : CONFIG.getExtraCustomEmojis().object2LongEntrySet()) {
                // Extra custom emojis cannot be animated currently.
                addEmojiName(extraCustom.getKey(), extraCustom.getLongValue(), false);
                getEmojiCP(extraCustom.getKey(), extraCustom.getLongValue(), false);
            }

            if (!CONFIG.getBotToken().isEmpty()) {
                LOGGER.info("Starting Discord bot...");
                try {
                    jda = JDABuilder.createDefault(CONFIG.getBotToken())
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                        .addEventListeners(new DiscordChatEventListener())
                        .build().awaitReady();
                } catch (Exception e) {
                    LOGGER.error("Bot failed to start!", e);
                    jda = null;
                }
                if (jda != null) {
                    LOGGER.info("Discord bot started!");
                    for (final Guild guild : jda.getGuilds()) {
                        guild.loadMembers().onSuccess(members ->
                            LOGGER.info("Loaded {} members from guild {}.", members.size(), guild.getName())
                        );
                    }
                    for (final RichCustomEmoji emoji : jda.getEmojis()) {
                        addEmojiName(emoji.getName(), emoji.getIdLong(), emoji.isAnimated());
                        getEmojiCP(emoji.getName(), emoji.getIdLong(), false);
                    }
                    if (CONFIG.getWebhookUrl().isEmpty()) {
                        if (CONFIG.getMessageChannel() != 0L) {
                            final TextChannel channel = jda.getTextChannelById(CONFIG.getMessageChannel());
                            if (channel != null) {
                                channel.createWebhook(channel.getGuild().getSelfMember().getEffectiveName()).queue(webhook -> {
                                    LOGGER.info("Successfully created webhook");
                                    CONFIG.setWebhookUrl(webhook.getUrl());
                                    writeConfig();
                                    chatWebhook = JDAWebhookClient.from(webhook);
                                    chatWebhook.send("Starting server...");
                                });
                            }
                        }
                    } else {
                        chatWebhook = JDAWebhookClient.withUrl(CONFIG.getWebhookUrl());
                        chatWebhook.send("Starting server...");
                    }
                }
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            if (chatWebhook != null) {
                chatWebhook.send("Server started!");
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (chatWebhook != null) {
                chatWebhook.send("Server stopped!");
                chatWebhook.close();
                chatWebhook = null;
            }
            if (jda != null) {
                jda.shutdown();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);

        Registry.register(Registry.SOUND_EVENT, PING_SOUND_ID, PING_SOUND_EVENT);

        LOGGER.info("Initialized " + MOD_ID);
    }

    private static String internalToDiscord(Text internal) {
        final StringBuilder result = new StringBuilder();
        TextVisitor.walk(internal, new TextVisitor() {
            @Override
            protected boolean visitLiteral(Text text, LiteralComponent component) {
                component.literal().codePoints().forEach(cp -> {
                    if (cp >= PUA_FIRST && cp <= PUA_LAST) {
                        final var discordInfo = EMOJI_CHARS_REVERSE.get(cp);
                        if (discordInfo != null) {
                            result.append('<')
                                .append(discordInfo.getKey())
                                .append(discordInfo.getLongValue())
                                .append('>');
                            return;
                        }
                    }
                    result.appendCodePoint(cp);
                });
                return true;
            }

            @Override
            protected boolean visitTranslatable(Text text, TranslatableComponent component) {
                if (component.getKey().equals("chat.discord.ping")) {
                    result.append("<@").append(component.getArgs()[1]).append('>');
                    return false;
                }
                return true;
            }
        });
        return result.toString();
    }

    private static void addEmojiName(String name, long id, boolean animated) {
        final Long2BooleanMap.Entry entry = new AbstractLong2BooleanMap.BasicEntry(id, animated);
        if (EMOJI_NAMES.putIfAbsent(name, entry) == null) return; // It's new. Good.
        int tilde = 1;
        while (true) {
            final String tildedName = name + '~' + tilde;
            if (EMOJI_NAMES.putIfAbsent(tildedName, entry) == null) break;
            tilde++;
        }
    }

    private static void readConfig() {
        try (org.quiltmc.json5.JsonReader reader = org.quiltmc.json5.JsonReader.json5(CONFIG_PATH)) {
            CONFIG.read(reader);
        } catch (IOException e) {
            LOGGER.warn("Failed to load Discord config", e);
        }
        writeConfig();
    }

    private static void writeConfig() {
        try (JsonWriter writer = JsonWriter.json5(CONFIG_PATH)) {
            CONFIG.write(writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write Discord config", e);
        }
    }

    public static Text parseEmojis(String text) {
        final MutableText result = Text.empty();
        final StringBuilder current = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\\') {
                if (i < text.length() - 1 && text.charAt(i + 1) == ':') {
                    i++;
                    continue;
                }
                if (i < text.length() - 1 && text.charAt(i + 1) == '<') {
                    current.append('<');
                    i += 2;
                    continue;
                }
            } else if (c == ':') {
                final int nextColon = text.indexOf(':', i + 1);
                if (nextColon != -1) {
                    final String emojiName = text.substring(i + 1, nextColon);
                    final Long2BooleanMap.Entry emoji = EMOJI_NAMES.get(emojiName);
                    if (emoji == BUILTIN_EMOJI_MARKER) {
                        current.append(EMOJI_SHORTCODES.get(emojiName));
                        i = nextColon + 1;
                        continue;
                    }
                    if (emoji != null) {
                        final String longNameStart = emoji.getBooleanValue() ? "a:" : ":";
                        current.appendCodePoint(getEmojiCP(longNameStart + emojiName + ':', emoji.getLongKey(), true));
                        i = nextColon + 1;
                        continue;
                    }
                }
            } else if (c == '<') {
                final int startI = i;
                final boolean isLikelyEmoji;
                if (i < text.length() - 1 && text.charAt(i + 1) == ':') {
                    isLikelyEmoji = true;
                    i += 2;
                } else if (i < text.length() - 2 && text.charAt(i + 1) == 'a' && text.charAt(i + 2) == ':') {
                    isLikelyEmoji = true;
                    i += 3;
                } else {
                    isLikelyEmoji = false;
                }
                if (isLikelyEmoji) {
                    final int nextColon = text.indexOf(':', i);
                    if (nextColon != -1) {
                        final int lastGt = text.indexOf('>', nextColon + 1);
                        if (lastGt != -1) {
                            try {
                                final long emojiId = Long.parseLong(text.substring(nextColon + 1, lastGt));
                                current.appendCodePoint(getEmojiCP(text.substring(startI + 1, nextColon + 1), emojiId, true));
                                i = lastGt + 1;
                                continue;
                            } catch (NumberFormatException e) {
                                i = startI;
                            }
                        } else {
                            i = startI;
                        }
                    } else {
                        i = startI;
                    }
                }
            } else if (c == '@') {
                int spaceIndex = text.indexOf(' ', i + 1);
                if (spaceIndex == -1) {
                    spaceIndex = text.length();
                }
                String username = text.substring(i + 1, spaceIndex);
                final ServerPlayerEntity player = currentServer.getPlayerManager().getPlayer(username);
                if (player != null) {
//                    player.playSound(
//                        ServerPlayNetworking.canSend(player, CHAT_DISCORD_MESSAGE)
//                            ? PING_SOUND_EVENT
//                            : SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
//                        SoundCategory.AMBIENT, 1f, 1f
//                    );
                    result.append(current.toString());
                    current.setLength(0);
                    final Text displayName = player.getDisplayName();
                    Style style = displayName.getStyle();
                    if (style.getColor() == null) {
                        style = style.withColor(0x7d92dd);
                    }
                    result.append(Text.literal("@").setStyle(style));
                    result.append(displayName.copy().setStyle(style));
                    i = spaceIndex;
                    continue;
                }
                if (jda != null) {
                    final int poundIndex = text.indexOf('#', i + 1);
                    if (poundIndex != -1 && poundIndex < text.length() - 4) {
                        username = text.substring(i + 1, poundIndex + 5);
                        User user;
                        try {
                            user = jda.getUserByTag(username);
                        } catch (IllegalArgumentException e) {
                            user = null;
                        }
                        if (user != null) {
                            final Member member;
                            final TextChannel channel = jda.getTextChannelById(CONFIG.getMessageChannel());
                            if (channel != null) {
                                member = channel.getGuild().getMember(user);
                            } else {
                                member = null;
                            }
                            result.append(current.toString());
                            current.setLength(0);
                            result.append(
                                Text.translatable(
                                    "chat.discord.ping",
                                    member == null ? user.getName() : member.getEffectiveName(),
                                    user.getId()
                                ).styled(style -> style.withColor(
                                    member == null || member.getColorRaw() == Role.DEFAULT_COLOR_RAW
                                        ? 0x7d92dd : member.getColorRaw()
                                ))
                            );
                            i = poundIndex + 5;
                            continue;
                        }
                    }
                }
            }
            current.append(c);
            i++;
        }
        result.append(current.toString());
        return result;
    }

    public static int getEmojiCP(String emojiName, long emojiId, boolean syncWithClients) {
        return EMOJI_CHARS.computeIfAbsent(emojiId, key -> {
            final int emojiCP = nextEmojiCP++;
            if (emojiCP > PUA_LAST) {
                nextEmojiCP--;
                return PUA_FIRST;
            }
            EMOJI_CHARS_REVERSE.put(emojiCP, new AbstractObject2LongMap.BasicEntry<>(emojiName, emojiId));
            if (syncWithClients) {
                final PacketByteBuf buf = PacketByteBufs.create();
                writeEmoji(buf, emojiId, emojiCP);
                currentServer.getPlayerManager().sendToAll(ServerPlayNetworking.createS2CPacket(EMOJI_SYNC_CHAR, buf));
            }
            return emojiCP;
        });
    }

    private static void writeEmoji(PacketByteBuf buf, long emojiId, int cp) {
        buf.writeVarLong(emojiId);
        buf.writeShort(cp - PUA_FIRST);
    }
}
