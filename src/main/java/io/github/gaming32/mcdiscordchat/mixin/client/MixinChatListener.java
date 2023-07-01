package io.github.gaming32.mcdiscordchat.mixin.client;

import io.github.gaming32.mcdiscordchat.MessageKey;
import io.github.gaming32.mcdiscordchat.client.ChatMessageInfo;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ChatListener.class)
public class MixinChatListener {
    @Shadow @Final private Minecraft minecraft;

    @Inject(
        method = "showMessageToPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            shift = At.Shift.AFTER
        )
    )
    private void addMessageKey(ChatType.Bound parameters, PlayerChatMessage signedChatMessage, Component text, PlayerInfo playerListEntry, boolean bl, Instant instant, CallbackInfoReturnable<Boolean> cir) {
        final GuiMessage hudMessage = McDiscordChatClient.getChatHudMessages(minecraft).get(0);
        final MessageKey<?> messageKey = MessageKey.ofMinecraft(signedChatMessage);
        final ChatMessageInfo messageInfo = McDiscordChatClient.getChatMessageInfo(messageKey);
        messageInfo.setHudMessage(hudMessage);
        McDiscordChatClient.CHAT_MESSAGES_GUI_LOOKUP.put(hudMessage, messageInfo);
    }
}
