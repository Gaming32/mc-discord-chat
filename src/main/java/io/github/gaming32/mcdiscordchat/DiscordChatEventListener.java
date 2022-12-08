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
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
        final Map.Entry<Text, Text> minecraftFormatted = formatDiscordMessage(
            Objects.requireNonNull(event.getMember()), event.getMessage()
        );
        McDiscordChat.executePings(minecraftFormatted.getValue());

        final MutableText vanillaText = Text.empty();
        vanillaText.append(Text.literal("[").styled(style -> style.withColor(0x2c2f33).withBold(true)));
        vanillaText.append(Text.literal("DISCORD").styled(style -> style.withColor(0x7289da).withBold(true)));
        vanillaText.append(Text.literal("] ").styled(style -> style.withColor(0x2c2f33).withBold(true)));
        vanillaText.append(minecraftFormatted.getKey());

        final long messageId = McDiscordChat.nextMessageId++;
        McDiscordChat.DISCORD_MESSAGE_IDS.put(event.getMessageIdLong(), messageId);

        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeText(minecraftFormatted.getKey());
        buf.writeVarLong(messageId);
        final Packet<?> packet = ServerPlayNetworking.createS2CPacket(McDiscordChat.CHAT_DISCORD_MESSAGE, buf);

        for (final ServerPlayerEntity player : McDiscordChat.currentServer.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, McDiscordChat.CHAT_DISCORD_MESSAGE)) {
                player.networkHandler.sendPacket(packet);
            } else {
                player.sendSystemMessage(vanillaText);
            }
        }
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        final long messageId = McDiscordChat.DISCORD_MESSAGE_IDS.get(event.getMessageIdLong());
        if (messageId == -1L) return;
        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarLong(messageId);
        buf.writeText(formatDiscordMessage(Objects.requireNonNull(event.getMember()), event.getMessage()).getKey());
        McDiscordChat.currentServer.getPlayerManager().sendToAll(ServerPlayNetworking.createS2CPacket(
            McDiscordChat.CHAT_MESSAGE_EDIT, buf
        ));
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        final long messageId = McDiscordChat.DISCORD_MESSAGE_IDS.remove(event.getMessageIdLong());
        if (messageId == -1L) return;
        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarLong(messageId);
        McDiscordChat.currentServer.getPlayerManager().sendToAll(ServerPlayNetworking.createS2CPacket(
            McDiscordChat.CHAT_MESSAGE_REMOVE, buf
        ));
    }

    private static Map.Entry<Text, Text> formatDiscordMessage(Member author, Message message) {
        final MutableText text = Text.empty();
        text.append(Text.literal("<"));
        text.append(
            Text.literal(author.getEffectiveName())
                .styled(style -> {
                    final int color = author.getColorRaw();
                    if (color != Role.DEFAULT_COLOR_RAW) {
                        style = style.withColor(color);
                    }
                    return McDiscordChat.addUserTooltip(style, message.getAuthor(), author);
                })
        );
        text.append(Text.literal("> "));
        if (!message.getEmbeds().isEmpty() || !message.getAttachments().isEmpty()) {
            text.append(Text.literal("[\u2709] ").styled(style -> style.withColor(0xddd605)));
        }

        final MutableText messageText = McDiscordChat.parseEmojis(indentSubsequentLines(message.getContentRaw())).copy();
        if (message.isEdited()) {
            messageText.append(Text.literal(" (edited)").styled(style ->
                style.withFormatting(Formatting.DARK_GRAY)
            ));
        }

        text.append(messageText);

        return Map.entry(text, messageText);
    }

    private static String indentSubsequentLines(String text) {
        return text.indent(3).trim();
    }
}
