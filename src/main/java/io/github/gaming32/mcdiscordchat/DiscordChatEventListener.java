package io.github.gaming32.mcdiscordchat;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

public class DiscordChatEventListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isWebhookMessage()) {
            return;
        }
        if (event.getChannel().getIdLong() != McDiscordChat.CONFIG.getMessageChannel()) {
            return;
        }
        final Map.Entry<Component, Component> minecraftFormatted = formatDiscordMessage(
            Objects.requireNonNull(event.getMember()), event.getMessage()
        );
        McDiscordChat.executePings(minecraftFormatted.getValue());

        final MutableComponent vanillaText = Component.empty();
        vanillaText.append(Component.literal("[").withStyle(style -> style.withColor(0x2c2f33).withBold(true)));
        vanillaText.append(Component.literal("DISCORD").withStyle(style -> style.withColor(0x7289da).withBold(true)));
        vanillaText.append(Component.literal("] ").withStyle(style -> style.withColor(0x2c2f33).withBold(true)));
        vanillaText.append(minecraftFormatted.getKey());

        final MessageKey<String> messageKey = MessageKey.ofDiscord(event.getMessageId());
        McDiscordChat.MC_TO_DISCORD_MESSAGES.put(messageKey, event.getMessageId());
        McDiscordChat.MESSAGE_AUTHORS.put(messageKey, McDiscordChat.DISCORD_USER_UUID);

        for (final ServerPlayer player : McDiscordChat.currentServer.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, McDiscordChat.CHAT_DISCORD_MESSAGE)) {
                final FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeComponent(minecraftFormatted.getKey());
                messageKey.write(buf);
                final int permissions;
                if (player.hasPermissions(2) && McDiscordChat.CONFIG.areOpsDiscordModerators()) {
                    permissions = McDiscordChat.MESSAGE_DELETABLE;
                } else {
                    permissions = 0;
                }
                buf.writeVarInt(permissions);
                ServerPlayNetworking.send(player, McDiscordChat.CHAT_DISCORD_MESSAGE, buf);
            } else {
                player.sendSystemMessage(vanillaText);
            }
        }
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (event.getMember() == null) return; // Can happen if it's from the webhook
        final FriendlyByteBuf buf = PacketByteBufs.create();
        MessageKey.ofDiscord(event.getMessageId()).write(buf);
        buf.writeComponent(formatDiscordMessage(Objects.requireNonNull(event.getMember()), event.getMessage()).getKey());
        McDiscordChat.currentServer.getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(
            McDiscordChat.CHAT_MESSAGE_EDIT, buf
        ));
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        final MessageKey<?> messageKey = McDiscordChat.MC_TO_DISCORD_MESSAGES.inverse().get(event.getMessageId());
        if (messageKey == null) return;
        McDiscordChat.deleteMessage(messageKey);
    }

    private static Map.Entry<Component, Component> formatDiscordMessage(Member author, Message message) {
        final MutableComponent text = Component.empty()
            .append(Component.literal("<"))
            .append(
                Component.literal(author.getEffectiveName())
                    .withStyle(style -> {
                        final int color = author.getColorRaw();
                        if (color != Role.DEFAULT_COLOR_RAW) {
                            style = style.withColor(color);
                        }
                        return McDiscordChat.addUserTooltip(style, message.getAuthor(), author);
                    })
            ).append(Component.literal("> "));
        if (!message.getEmbeds().isEmpty() || !message.getAttachments().isEmpty()) {
            text.append(Component.literal("[\u2709] ").withStyle(style -> style.withColor(0xddd605)));
        }

        final MutableComponent messageText = McDiscordChat.parseEmojis(indentSubsequentLines(message.getContentRaw())).copy();
        if (message.isEdited()) {
            messageText.append(Component.literal(" (edited)").withStyle(style ->
                style.applyFormat(ChatFormatting.DARK_GRAY).withFont(McDiscordChat.SMALL_FONT)
            ));
        }

        text.append(messageText);

        return Map.entry(text, messageText);
    }

    private static String indentSubsequentLines(String text) {
        return text.indent(3).trim();
    }
}
