package io.github.gaming32.mcdiscordchat.mixin;

import io.github.gaming32.mcdiscordchat.McDiscordChat;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {
    @Shadow public ServerPlayer player;

    @Inject(method = "addPendingMessage", at = @At("HEAD"))
    private void identifyMessage(PlayerChatMessage signedChatMessage, CallbackInfo ci) {
        McDiscordChat.broadcastMessagePermissions(player, signedChatMessage);
    }
}
