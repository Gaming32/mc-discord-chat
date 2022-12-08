package io.github.gaming32.mcdiscordchat.mixin.client;

import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.client.ChatMessageInfo;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import io.github.gaming32.mcdiscordchat.client.MessageEditor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class MixinChatScreen implements MessageEditor {
    @Shadow protected TextFieldWidget chatField;
    @Unique
    private ChatMessageInfo editingMessage;
    @Unique
    private String oldMessage;
    @Unique
    private boolean hoveringCancelEdit;

    @Inject(method = "init", at = @At("TAIL"))
    private void customClickEvent(CallbackInfo ci) {
        ScreenMouseEvents.allowMouseClick((Screen)(Object)this).register((screen, mouseX, mouseY, button) -> {
            if (hoveringCancelEdit) {
                editingMessage = null;
                chatField.setText(oldMessage);
                return false;
            }
            final ChatMessageInfo message = McDiscordChatClient.hoveredChatMessage;
            if (message == null || message.getHoveredElement() == 0) return true;
            if (message.getHoveredElement() == 1 && message.isEditable()) {
                System.out.println("Edit " + message.getMessage());
                editingMessage = message;
                oldMessage = chatField.getText();

                final PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarLong(message.getId());
                ClientPlayNetworking.send(McDiscordChat.CHAT_MESSAGE_ORIGINAL, buf);
            } else {
                McDiscordChat.LOGGER.info("Delete {} ({})", message.getId(), message.getMessage());

                final PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarLong(message.getId());
                ClientPlayNetworking.send(McDiscordChat.CHAT_MESSAGE_REMOVE, buf);
            }
            return false;
        });
    }

    @Inject(
        method = "handleChatInput",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHud;addToMessageHistory(Ljava/lang/String;)V"
        ),
        cancellable = true
    )
    private void confirmEdit(String text, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (editingMessage == null) return;

        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarLong(editingMessage.getId());
        buf.writeString(text);
        ClientPlayNetworking.send(McDiscordChat.CHAT_MESSAGE_EDIT, buf);

        editingMessage = null;
        chatField.setText(oldMessage);

        cir.setReturnValue(true);
    }

    @Override
    public ChatMessageInfo getEditingMessage() {
        return editingMessage;
    }

    @Override
    public void setHoveringCancelEdit(boolean hoveringCancelEdit) {
        this.hoveringCancelEdit = hoveringCancelEdit;
    }
}
