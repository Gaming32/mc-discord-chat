package io.github.gaming32.mcdiscordchat;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.network.packet.s2c.play.SystemMessageS2CPacket;
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
        text.append(Text.literal("[").styled(style -> style.withColor(0x2c2f33).withBold(true)));
        text.append(Text.literal("DISCORD").styled(style -> style.withColor(0x7289da).withBold(true)));
        text.append(Text.literal("] ").styled(style -> style.withColor(0x2c2f33).withBold(true)));
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
        text.append(McDiscordChat.parseEmojis(event.getMessage().getContentRaw()));
        McDiscordChat.currentServer.getPlayerManager().sendToAll(new SystemMessageS2CPacket(text, false));
    }
}
