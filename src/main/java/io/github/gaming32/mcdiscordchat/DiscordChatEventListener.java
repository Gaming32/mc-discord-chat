package io.github.gaming32.mcdiscordchat;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

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
        final MutableText text = Text.empty();
        text.append(Text.literal("<"));
        text.append(
            Text.literal(Objects.requireNonNull(event.getMember()).getEffectiveName())
                .styled(style -> {
                    final int color = event.getMember().getColorRaw();
                    if (color != Role.DEFAULT_COLOR_RAW) {
                        style = style.withColor(color);
                    }
                    return style;
                })
        );
        text.append(Text.literal("> "));
        if (!event.getMessage().getEmbeds().isEmpty() || !event.getMessage().getAttachments().isEmpty()) {
            text.append(Text.literal("[\u2709] ").styled(style -> style.withColor(0xddd605)));
        }

        final Text messageText = McDiscordChat.parseEmojis(indentSubsequentLines(event.getMessage().getContentRaw()));
        text.append(messageText);
        McDiscordChat.executePings(messageText);

        final MutableText vanillaText = Text.empty();
        vanillaText.append(Text.literal("[").styled(style -> style.withColor(0x2c2f33).withBold(true)));
        vanillaText.append(Text.literal("DISCORD").styled(style -> style.withColor(0x7289da).withBold(true)));
        vanillaText.append(Text.literal("] ").styled(style -> style.withColor(0x2c2f33).withBold(true)));
        vanillaText.append(text);

        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeText(text);
        final Packet<?> packet = ServerPlayNetworking.createS2CPacket(McDiscordChat.CHAT_DISCORD_MESSAGE, buf);

        for (final ServerPlayerEntity player : McDiscordChat.currentServer.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, McDiscordChat.CHAT_DISCORD_MESSAGE)) {
                player.networkHandler.sendPacket(packet);
            } else {
                player.sendSystemMessage(vanillaText);
            }
        }
    }

    private static String indentSubsequentLines(String text) {
        return text.indent(3).trim();
    }
}
