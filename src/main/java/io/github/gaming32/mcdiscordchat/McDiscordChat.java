package io.github.gaming32.mcdiscordchat;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
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
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class McDiscordChat implements ModInitializer {
    public static final String MOD_ID = "mc-discord-chat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceLocation EMOJI_SYNC_CHARS = new ResourceLocation(MOD_ID, "emoji/sync_chars");
    public static final ResourceLocation EMOJI_SYNC_CHAR = new ResourceLocation(MOD_ID, "emoji/sync_char");
    public static final ResourceLocation EMOJI_SYNC_NAMES = new ResourceLocation(MOD_ID, "emoji/sync_names");
    public static final ResourceLocation EMOJI_SYNC_NAME = new ResourceLocation(MOD_ID, "emoji/sync_name");
    public static final ResourceLocation CHAT_DISCORD_MESSAGE = new ResourceLocation(MOD_ID, "chat/discord_message");
    public static final ResourceLocation CHAT_PING_AUTOCOMPLETE = new ResourceLocation(MOD_ID, "chat/ping_autocomplete");
    public static final ResourceLocation CHAT_MESSAGE_EDIT = new ResourceLocation(MOD_ID, "chat/message_edit");
    public static final ResourceLocation CHAT_MESSAGE_REMOVE = new ResourceLocation(MOD_ID, "chat/message_remove");
    public static final ResourceLocation CHAT_MESSAGE_ORIGINAL = new ResourceLocation(MOD_ID, "chat/message_original");
    public static final ResourceLocation CHAT_MESSAGE_PERMISSIONS = new ResourceLocation(MOD_ID, "chat/message_permissions");

    public static final ResourceLocation SMALL_FONT = new ResourceLocation(MOD_ID, "small");

    public static final int MESSAGE_EDITABLE = 0x1;
    public static final int MESSAGE_DELETABLE = 0x2;

    public static final ResourceLocation PING_SOUND_ID = new ResourceLocation(MOD_ID, "ping");
    public static final SoundEvent PING_SOUND_EVENT = new SoundEvent(PING_SOUND_ID);

    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json5");
    public static final DiscordChatConfig CONFIG = new DiscordChatConfig();

    public static final Map<String, String> EMOJI_SHORTCODES = new HashMap<>();
    public static final Long2IntMap EMOJI_CHARS = new Long2IntOpenHashMap();
    private static final Int2ObjectMap<Object2LongMap.Entry<String>> EMOJI_CHARS_REVERSE = new Int2ObjectOpenHashMap<>();
    public static final int PUA_FIRST = 0xF0000;
    public static final int PUA_LAST = 0xFFFFD;
    public static final int UNKNOWN_EMOJI_CP = PUA_FIRST;
    private static int nextEmojiCP = UNKNOWN_EMOJI_CP + 1;
    public static final Map<String, Long2BooleanMap.Entry> EMOJI_NAMES = new HashMap<>();
    public static final Long2BooleanMap.Entry BUILTIN_EMOJI_MARKER = new AbstractLong2BooleanMap.BasicEntry();

    static final UUID DISCORD_USER_UUID = Util.NIL_UUID;
    static final Map<MessageKey<?>, UUID> MESSAGE_AUTHORS = new HashMap<>();
    private static final Map<MessageKey<?>, String> ORIGINAL_MESSAGES = new HashMap<>();
    static final BiMap<MessageKey<?>, String> MC_TO_DISCORD_MESSAGES = HashBiMap.create();

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
            final MessageKey<?> messageKey = MessageKey.ofMinecraft(message);
            MESSAGE_AUTHORS.put(messageKey, sender.getUUID());
            ORIGINAL_MESSAGES.put(messageKey, message.signedContent().plain());

            final Component text = message.serverContent();
            executePings(text);
            if (chatWebhook != null) {
                assert jda != null;
                chatWebhook.send(
                    new WebhookMessageBuilder()
                        .setContent(internalToDiscord(text))
                        .setUsername(sender.getDisplayName().getString())
                        .setAvatarUrl("https://crafatar.com/renders/head/" + sender.getUUID() + "?overlay=true")
                        .setAllowedMentions(getAllowedMentions())
                        .build()
                ).thenAccept(discordMessage ->
                    MC_TO_DISCORD_MESSAGES.put(messageKey, Long.toUnsignedString(discordMessage.getId()))
                );
            }
        });

        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (overlay) return;
            if (chatWebhook != null) {
                assert jda != null;
                chatWebhook.send(
                    new WebhookMessageBuilder()
                        .setContent(internalToDiscord(message))
                        .setAllowedMentions(getAllowedMentions())
                        .build()
                );
            }
        });

        ServerMessageDecoratorEvent.EVENT.register((serverPlayerEntity, text) ->
            CompletableFuture.completedFuture(parseEmojis(text.getString()))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(EMOJI_CHARS.size());
            for (final Long2IntMap.Entry entry : EMOJI_CHARS.long2IntEntrySet()) {
                writeEmoji(buf, entry.getLongKey(), entry.getIntValue());
            }
            sender.sendPacket(EMOJI_SYNC_CHARS, buf);

            buf = PacketByteBufs.create();
            buf.writeVarInt(EMOJI_NAMES.size());
            for (final var entry : EMOJI_NAMES.entrySet()) {
                buf.writeUtf(entry.getKey());
                if (entry.getValue() == BUILTIN_EMOJI_MARKER) {
                    buf.writeVarInt(EMOJI_SHORTCODES.get(entry.getKey()).codePointAt(0));
                } else {
                    buf.writeVarInt(EMOJI_CHARS.get(entry.getValue().getLongKey()));
                }
            }
            sender.sendPacket(EMOJI_SYNC_NAMES, buf);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            readConfig();

            EMOJI_NAMES.keySet().retainAll(EMOJI_SHORTCODES.keySet());
            for (final Object2LongMap.Entry<String> extraCustom : CONFIG.getExtraCustomEmojis().object2LongEntrySet()) {
                // Extra custom emojis cannot be animated currently.
                addEmojiName(extraCustom.getKey(), extraCustom.getLongValue(), false);
                getEmojiCP(':' + extraCustom.getKey() + ':', extraCustom.getLongValue(), false);
            }

            if (!CONFIG.getBotToken().isEmpty()) {
                LOGGER.info("Starting Discord bot...");
                try {
                    jda = JDABuilder.create(
                        CONFIG.getBotToken(),
                        GatewayIntent.getIntents(GatewayIntent.DEFAULT | GatewayIntent.getRaw(
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_PRESENCES
                        ))
                    ).addEventListeners(new DiscordChatEventListener())
                        .build().awaitReady();
                } catch (Exception e) {
                    LOGGER.error("Bot failed to start!", e);
                    jda = null;
                }
                if (jda != null) {
                    LOGGER.info("Discord bot started!");
                    for (final RichCustomEmoji emoji : jda.getEmojis()) {
                        final String start = emoji.isAnimated() ? "a:" : ":";
                        addEmojiName(emoji.getName(), emoji.getIdLong(), emoji.isAnimated());
                        getEmojiCP(start + emoji.getName() + ':', emoji.getIdLong(), false);
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

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MESSAGE_AUTHORS.clear();
            ORIGINAL_MESSAGES.clear();
            MC_TO_DISCORD_MESSAGES.clear();
            currentServer = null;
        });

        ServerPlayNetworking.registerGlobalReceiver(CHAT_PING_AUTOCOMPLETE, (server, player, handler, buf, responseSender) -> {
            final int transactionId = buf.readVarInt();
            final String partialMessage = buf.readUtf();

            final int lastAt = partialMessage.lastIndexOf('@');

            final SuggestionsBuilder suggestionsBuilder = new SuggestionsBuilder(partialMessage, lastAt + 1);
            final Map<String, Component> results = new HashMap<>();

            if (lastAt == 0 || partialMessage.charAt(lastAt - 1) != '<') {
                final String username = partialMessage.substring(lastAt + 1).toLowerCase(Locale.ROOT);
                final int lastSpace = partialMessage.lastIndexOf(' ');
                if (lastSpace < lastAt) {
                    for (final ServerPlayer otherPlayer : server.getPlayerList().getPlayers()) {
                        if (otherPlayer.getScoreboardName().toLowerCase(Locale.ROOT).startsWith(username)) {
                            if (results.putIfAbsent(otherPlayer.getScoreboardName(), otherPlayer.getDisplayName()) == null) {
                                suggestionsBuilder.suggest(otherPlayer.getScoreboardName());
                            }
                        }
                    }
                }

                if (jda != null) {
                    final TextChannel channel = jda.getTextChannelById(CONFIG.getMessageChannel());
                    if (channel != null) {
                        for (final Member member : channel.getGuild().getMemberCache()) {
                            final String tag = member.getUser().getAsTag();
                            final String name = member.getEffectiveName();
                            if (
                                tag.toLowerCase(Locale.ROOT).startsWith(username) ||
                                    name.toLowerCase(Locale.ROOT).startsWith(username)
                            ) {
                                final Component displayName = Component.literal(member.getEffectiveName())
                                    .withStyle(style -> {
                                        if (member.getColorRaw() != Role.DEFAULT_COLOR_RAW) {
                                            style = style.withColor(member.getColorRaw());
                                        }
                                        return style;
                                    });
                                if (results.putIfAbsent(tag, displayName) == null) {
                                    suggestionsBuilder.suggest(tag);
                                }
                            }
                        }
                        for (final Role role : channel.getGuild().getRoleCache()) {
                            if (!role.isMentionable()) continue;
                            final String name = role.getName();
                            if (name.toLowerCase(Locale.ROOT).startsWith(username)) {
                                final Component displayName = Component.literal(role.getName())
                                    .withStyle(style -> {
                                        if (role.getColorRaw() != Role.DEFAULT_COLOR_RAW) {
                                            style = style.withColor(role.getColorRaw());
                                        }
                                        return style;
                                    });
                                if (results.putIfAbsent(name, displayName) == null) {
                                    suggestionsBuilder.suggest(name);
                                }
                            }
                        }
                    }
                }
            }

            final Suggestions suggestions = suggestionsBuilder.build();
            final FriendlyByteBuf response = PacketByteBufs.create();
            response.writeVarInt(transactionId);
            response.writeVarInt(suggestions.getRange().getStart());
            response.writeVarInt(suggestions.getRange().getLength());
            response.writeCollection(suggestions.getList(), (buf2, suggestion) ->
                buf2.writeUtf(suggestion.getText())
            );
            response.writeMap(results, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeComponent);
            responseSender.sendPacket(CHAT_PING_AUTOCOMPLETE, response);
        });

        ServerPlayNetworking.registerGlobalReceiver(CHAT_MESSAGE_REMOVE, (server, player, handler, buf, responseSender) -> {
            final MessageKey<?> messageKey = MessageKey.read(buf);
            final UUID author = MESSAGE_AUTHORS.get(messageKey);
            if (author == null) return;
            boolean hasPermission = false;
            if (author.equals(DISCORD_USER_UUID)) {
                if (player.hasPermissions(2) && CONFIG.areOpsDiscordModerators()) {
                    hasPermission = true;
                }
            } else if (player.hasPermissions(2) || author.equals(player.getUUID())) {
                hasPermission = true;
            }
            if (hasPermission) {
                final String discordMessageId = deleteMessage(messageKey);
                if (jda != null) {
                    final TextChannel channel = jda.getTextChannelById(CONFIG.getMessageChannel());
                    if (channel != null) {
                        channel.deleteMessageById(discordMessageId).queue(
                            ignored -> {}, t -> LOGGER.error("Failed to delete message", t)
                        );
                    }
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(CHAT_MESSAGE_ORIGINAL, (server, player, handler, buf, responseSender) -> {
            final FriendlyByteBuf response = PacketByteBufs.create();
            response.writeUtf(ORIGINAL_MESSAGES.get(MessageKey.read(buf)));
            responseSender.sendPacket(CHAT_MESSAGE_ORIGINAL, response);
        });

        ServerPlayNetworking.registerGlobalReceiver(CHAT_MESSAGE_EDIT, (server, player, handler, buf, responseSender) -> {
            final MessageKey<?> messageKey = MessageKey.read(buf);
            final String newContents = buf.readUtf();
            final UUID author = MESSAGE_AUTHORS.get(messageKey);
            if (author == null || !author.equals(player.getUUID())) return;
            ORIGINAL_MESSAGES.put(messageKey, newContents);
            ServerMessageDecoratorEvent.EVENT.invoker().decorate(player, Component.literal(newContents)).thenAccept(text -> {
                if (chatWebhook != null) {
                    final String discordId = MC_TO_DISCORD_MESSAGES.get(messageKey);
                    if (discordId != null) {
                        chatWebhook.edit(discordId, internalToDiscord(text));
                    }
                }

                final FriendlyByteBuf response = PacketByteBufs.create();
                messageKey.write(response);
                response.writeComponent(
                    Component.translatable("chat.type.text", player.getDisplayName(), text)
                        .append(Component.literal(" (edited)").withStyle(style ->
                            style.applyFormat(ChatFormatting.DARK_GRAY).withFont(McDiscordChat.SMALL_FONT)
                        ))
                );
                currentServer.getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(CHAT_MESSAGE_EDIT, response));
            });
        });

        Registry.register(Registry.SOUND_EVENT, PING_SOUND_ID, PING_SOUND_EVENT);

        LOGGER.info("Initialized " + MOD_ID);
    }

    static String deleteMessage(MessageKey<?> messageKey) {
        final String discordId = MC_TO_DISCORD_MESSAGES.remove(messageKey);
        MESSAGE_AUTHORS.remove(messageKey);
        final FriendlyByteBuf buf = PacketByteBufs.create();
        messageKey.write(buf);
        McDiscordChat.currentServer.getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(
            McDiscordChat.CHAT_MESSAGE_REMOVE, buf
        ));
        return discordId;
    }

    public static void broadcastMessagePermissions(ServerPlayer author, PlayerChatMessage message) {
        final MessageKey<?> messageKey = MessageKey.ofMinecraft(message);
        for (final ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, CHAT_MESSAGE_PERMISSIONS)) {
                final FriendlyByteBuf buf = PacketByteBufs.create();
                messageKey.write(buf);
                final int permissions;
                if (player == author) {
                    permissions = MESSAGE_EDITABLE | MESSAGE_DELETABLE;
                } else if (player.hasPermissions(2)) {
                    permissions = MESSAGE_DELETABLE;
                } else {
                    permissions = 0;
                }
                buf.writeVarInt(permissions);
                ServerPlayNetworking.send(player, CHAT_MESSAGE_PERMISSIONS, buf);
            }
        }
    }

    private static AllowedMentions getAllowedMentions() {
        assert jda != null;
        return AllowedMentions.none()
            .withParseUsers(true)
            .withRoles(
                jda.getRoleCache()
                    .stream()
                    .filter(Role::isMentionable)
                    .map(Role::getId)
                    .collect(Collectors.toList())
            );
    }

    private static String internalToDiscord(Component internal) {
        final StringBuilder result = new StringBuilder();
        TextVisitor.walk(internal, new TextVisitor() {
            @Override
            protected boolean visitLiteral(Component text, LiteralContents component) {
                component.text().codePoints().forEach(cp -> {
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
            protected boolean visitTranslatable(Component text, TranslatableContents component) {
                if (component.getKey().startsWith("chat.mention.discord")) {
                    result.append(component.getArgs()[1]);
                } else {
                    result.append(text.getString());
                }
                return false;
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

    public static void executePings(Component message) {
        for (final ServerPlayer player : collectPings(message)) {
            player.playNotifySound(
                ServerPlayNetworking.canSend(player, CHAT_DISCORD_MESSAGE)
                    ? PING_SOUND_EVENT
                    : SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.AMBIENT, 0.5f, 1.5f
            );
        }
    }

    public static Set<ServerPlayer> collectPings(Component message) {
        final Set<ServerPlayer> pinged = new HashSet<>();
        TextVisitor.walk(message, new TextVisitor() {
            @Override
            protected boolean visitTranslatable(Component text, TranslatableContents component) {
                if (component.getKey().equals("chat.mention.minecraft")) {
                    pinged.add(currentServer.getPlayerList().getPlayerByName(component.getArgs()[1].toString()));
                    return false;
                }
                return true;
            }
        });
        return pinged;
    }

    public static Component parseEmojis(String text) {
        final MutableComponent result = Component.empty();
        final StringBuilder current = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\\') {
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
                                final long emojiId = Long.parseLong(text, nextColon + 1, lastGt, 10);
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
                } else if (jda != null) {
                    final TextChannel channel = jda.getTextChannelById(CONFIG.getMessageChannel());
                    if (channel != null) {
                        final int lastGt = text.indexOf('>', i + 1);
                        if (lastGt != -1 && lastGt - i > 2) {
                            final String mentionType;
                            if (text.charAt(i + 1) == '#') {
                                mentionType = "#";
                            } else if (text.charAt(i + 1) == '@') {
                                if (text.charAt(i + 2) == '!') {
                                    mentionType = "@!";
                                } else if (text.charAt(i + 2) == '&') {
                                    mentionType = "@&";
                                } else {
                                    mentionType = "@";
                                }
                            } else {
                                mentionType = null;
                            }
                            if (mentionType != null) {
                                try {
                                    result.append(current.toString());
                                    current.setLength(0);
                                    final long mentionId = Long.parseLong(text, i + mentionType.length() + 1, lastGt, 10);
                                    switch (mentionType) {
                                        case "@", "@!" -> {
                                            final User user = jda.getUserById(mentionId);
                                            final Member member = channel.getGuild().getMemberById(mentionId);
                                            final String displayName;
                                            final int roleColor;
                                            if (member == null) {
                                                if (user == null) {
                                                    displayName = "<@" + mentionId + ">";
                                                } else {
                                                    displayName = "@" + user.getName();
                                                }
                                                roleColor = 0x7d92dd;
                                            } else {
                                                displayName = "@" + member.getEffectiveName();
                                                roleColor = member.getColorRaw();
                                            }
                                            result.append(
                                                Component.translatable(
                                                    "chat.mention.discord.custom",
                                                    displayName,
                                                    "<@" + mentionId + '>'
                                                ).withStyle(style -> {
                                                    style = style.withColor(
                                                        roleColor == Role.DEFAULT_COLOR_RAW ? 0x7d92dd : roleColor
                                                    );
                                                    if (user != null) {
                                                        return addUserTooltip(style, user, member);
                                                    }
                                                    if (member != null) {
                                                        return addUserTooltip(style, member.getUser(), member);
                                                    }
                                                    return style;
                                                })
                                            );
                                        }
                                        case "@&" -> {
                                            final Role role = channel.getGuild().getRoleById(mentionId);
                                            final String displayName;
                                            final int roleColor;
                                            if (role == null) {
                                                displayName = "<@&" + mentionId + ">";
                                                roleColor = 0x7d92dd;
                                            } else {
                                                displayName = "@" + role.getName();
                                                roleColor = role.getColorRaw();
                                            }
                                            result.append(
                                                Component.translatable(
                                                    "chat.mention.discord.custom",
                                                    displayName,
                                                    "<@&" + mentionId + '>'
                                                ).withStyle(style -> style.withColor(
                                                    roleColor == Role.DEFAULT_COLOR_RAW ? 0x7d92dd : roleColor
                                                ))
                                            );
                                        }
                                        case "#" -> {
                                            final Channel mentionChannel = channel.getGuild().getChannelById(Channel.class, mentionId);
                                            final String displayName;
                                            if (mentionChannel == null) {
                                                displayName = "<#" + mentionId + ">";
                                            } else {
                                                displayName = "#" + mentionChannel.getName();
                                            }
                                            result.append(
                                                Component.translatable(
                                                    "chat.mention.discord.custom",
                                                    displayName,
                                                    "<#" + mentionId + '>'
                                                ).withStyle(style ->
                                                    style.withColor(0x7d92dd)
                                                        .withClickEvent(new ClickEvent(
                                                            ClickEvent.Action.OPEN_URL,
                                                            "discord://discord.com/channels/" +
                                                                channel.getGuild().getId() + "/" + mentionId
                                                        ))
                                                        .withHoverEvent(new HoverEvent(
                                                            HoverEvent.Action.SHOW_TEXT,
                                                            Component.literal("Open channel in Discord client")
                                                        ))
                                                )
                                            );
                                        }
                                    }
                                    i = lastGt + 1;
                                    continue;
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                }
            } else if (c == '@') {
                String username;
                // Search for Discord user
                if (jda != null) {
                    final TextChannel channel = jda.getTextChannelById(CONFIG.getMessageChannel());
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
                            if (channel != null) {
                                member = channel.getGuild().getMember(user);
                            } else {
                                member = null;
                            }
                            result.append(current.toString());
                            current.setLength(0);
                            final User finalUser = user;
                            result.append(
                                Component.translatable(
                                    "chat.mention.discord",
                                    member == null ? user.getName() : member.getEffectiveName(),
                                    "<@" + user.getId() + '>'
                                ).withStyle(style -> addUserTooltip(style.withColor(
                                    member == null || member.getColorRaw() == Role.DEFAULT_COLOR_RAW
                                        ? 0x7d92dd : member.getColorRaw()
                                ), finalUser, member))
                            );
                            i = poundIndex + 5;
                            continue;
                        }
                    }
                }
                // Search for Minecraft player
                int spaceIndex = text.indexOf(' ', i + 1);
                if (spaceIndex == -1) {
                    spaceIndex = text.length();
                }
                username = text.substring(i + 1, spaceIndex);
                ServerPlayer player = currentServer.getPlayerList().getPlayerByName(username);
                while (player == null && username.length() > 1) {
                    username = username.substring(0, username.length() - 1);
                    player = currentServer.getPlayerList().getPlayerByName(username);
                }
                if (player != null) {
                    result.append(current.toString());
                    current.setLength(0);
                    final Component displayName = player.getDisplayName();
                    Style style = displayName.getStyle();
                    if (style.getColor() == null) {
                        style = style.withColor(0x7d92dd);
                    }
                    result.append(
                        Component.translatable(
                            "chat.mention.minecraft",
                            displayName,
                            player.getScoreboardName()
                        ).setStyle(style)
                    );
                    i += username.length() + 1;
                    continue;
                }
                // Search for Discord role
                if (jda != null) {
                    final TextChannel channel = jda.getTextChannelById(CONFIG.getMessageChannel());
                    if (channel != null) {
                        final List<Role> roles = getMatchingRoles(channel.getGuild(), text, i);
                        if (!roles.isEmpty()) {
                            final Role role = roles.stream()
                                .min(Comparator.comparing(r -> r.getName().length()))
                                .orElseThrow();
                            if (text.regionMatches(true, i + 1, role.getName(), 0, role.getName().length())) {
                                result.append(current.toString());
                                current.setLength(0);
                                result.append(
                                    Component.translatable(
                                        "chat.mention.discord",
                                        role.getName(),
                                        "<@&" + role.getId() + '>'
                                    ).withStyle(style -> style.withColor(
                                        role.getColorRaw() == Role.DEFAULT_COLOR_RAW
                                            ? 0x7d92dd : role.getColorRaw()
                                    ))
                                );
                                i += role.getName().length() + 1;
                                continue;
                            }
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

    static Style addUserTooltip(Style style, User user, @Nullable Member member) {
        return style
            .withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Component.empty()
                    .append(
                        Component.literal(member != null ? member.getEffectiveName() : user.getName())
                            .withStyle(style2 -> {
                                if (member != null) {
                                    final int color = member.getColorRaw();
                                    if (color != Role.DEFAULT_COLOR_RAW) {
                                        return style2.withColor(color);
                                    }
                                }
                                return style2;
                            })
                    )
                    .append(statusToText(member))
                    .append(activityToText(member))
                    .append("\nShift-click to ping")
            ))
            .withInsertion('@' + user.getAsTag());
    }

    private static Component statusToText(@Nullable Member member) {
        if (member == null) return Component.empty();
        final OnlineStatus status = member.getOnlineStatus();
        if (status == OnlineStatus.ONLINE || status == OnlineStatus.IDLE || status == OnlineStatus.DO_NOT_DISTURB) {
            return Component.literal(" \u25cf").withStyle(style -> style.withColor(switch (member.getOnlineStatus()) {
                case ONLINE -> 0x43b581;
                case IDLE -> 0xfaa61a;
                case DO_NOT_DISTURB -> 0xed4245;
                default -> throw new AssertionError();
            }));
        }
        return Component.literal(" \u25cc");
    }

    private static Component activityToText(@Nullable Member member) {
        if (member == null) return Component.empty();
        final List<Activity> activities = member.getActivities();
        if (activities.isEmpty()) return Component.empty();

        final Activity activity = activities.get(0);
        final MutableComponent result = Component.literal("\n");
        if (activity.getEmoji() != null) {
            result.append(parseEmojis(activity.getEmoji().getFormatted() + ' '));
        }
        result.append(switch (activity.getType()) {
            case PLAYING -> "Playing ";
            case STREAMING -> "Streaming ";
            case LISTENING -> "Listening ";
            case WATCHING -> "Watching ";
            case CUSTOM_STATUS -> "";
            case COMPETING -> "Competing in ";
        });
        return result.append(
                Component.literal(activity.getName())
                    .withStyle(style -> style.withBold(true))
            );
    }

    private static List<Role> getMatchingRoles(Guild guild, String text, int atI) {
        List<Role> filterIter = guild.getRoleCache()
            .stream()
            .filter(Role::isMentionable)
            .toList();
        if (!filterIter.isEmpty()) {
            int[] textI = {atI + 1};
            int[] nameI = {0};
            while (textI[0] < text.length()) {
                filterIter = filterIter.stream()
                    .filter(role -> {
                        final String name = role.getName();
                        if (nameI[0] >= name.length()) return false;
                        return text.regionMatches(true, textI[0], name, nameI[0], 1);
                    })
                    .toList();
                if (filterIter.size() <= 1) break;
                textI[0]++;
                nameI[0]++;
            }
        }
        return filterIter;
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
                final FriendlyByteBuf buf = PacketByteBufs.create();
                writeEmoji(buf, emojiId, emojiCP);
                currentServer.getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(EMOJI_SYNC_CHAR, buf));
            }
            return emojiCP;
        });
    }

    private static void writeEmoji(FriendlyByteBuf buf, long emojiId, int cp) {
        buf.writeVarLong(emojiId);
        buf.writeShort(cp - PUA_FIRST);
    }
}
