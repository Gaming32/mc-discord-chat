package io.github.gaming32.mcdiscordchat.mixin.client;

import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.client.ChatMessageInfo;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Inject(method = "init", at = @At("TAIL"))
    private void customClickEvent(CallbackInfo ci) {
        ScreenMouseEvents.allowMouseClick((Screen)(Object)this).register((screen, mouseX, mouseY, button) -> {
            final ChatMessageInfo message = McDiscordChatClient.hoveredChatMessage;
            if (message == null || message.getHoveredElement() == 0) return true;
            if (message.getHoveredElement() == 1 && message.isEditable()) {
                System.out.println("Edit " + message.getMessage());
            } else {
                McDiscordChat.LOGGER.info("Delete {} ({})", message.getId(), message.getMessage());
                final PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarLong(message.getId());
                ClientPlayNetworking.send(McDiscordChat.CHAT_MESSAGE_REMOVE, buf);
            }
            return false;
        });
    }
}
