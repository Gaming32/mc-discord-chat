package io.github.gaming32.mcdiscordchat.mixin;

import io.github.gaming32.mcdiscordchat.McDiscordChat;
import net.minecraft.network.message.SignedChatMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "m_csymlmvj", at = @At("HEAD"))
    private void identifyMessage(SignedChatMessage signedChatMessage, CallbackInfo ci) {
        McDiscordChat.linkMessageWithId(player, signedChatMessage);
    }
}
