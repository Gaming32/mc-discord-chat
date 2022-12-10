package io.github.gaming32.mcdiscordchat.mixin.client;

import io.github.gaming32.mcdiscordchat.MessageKey;
import io.github.gaming32.mcdiscordchat.client.ChatMessageInfo;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ClientChatListener;
import net.minecraft.client.gui.hud.ChatHudMessage;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.MessageType;
import net.minecraft.network.message.SignedChatMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ClientChatListener.class)
public class MixinClientChatListener {
    @Shadow @Final private MinecraftClient client;

    @Inject(
        method = "m_ewbsevgr",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignature;Lnet/minecraft/client/gui/hud/ChatMessageTag;)V",
            shift = At.Shift.AFTER
        )
    )
    private void addMessageKey(MessageType.Parameters parameters, SignedChatMessage signedChatMessage, Text text, PlayerListEntry playerListEntry, boolean bl, Instant instant, CallbackInfoReturnable<Boolean> cir) {
        final ChatHudMessage hudMessage = McDiscordChatClient.getChatHudMessages(client).get(0);
        final MessageKey<?> messageKey = MessageKey.ofMinecraft(signedChatMessage);
        final ChatMessageInfo messageInfo = McDiscordChatClient.getChatMessageInfo(messageKey);
        messageInfo.setHudMessage(hudMessage);
        McDiscordChatClient.CHAT_MESSAGES_GUI_LOOKUP.put(hudMessage, messageInfo);
    }
}
