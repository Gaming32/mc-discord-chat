package io.github.gaming32.mcdiscordchat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.gaming32.mcdiscordchat.client.ChatMessageInfo;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import io.github.gaming32.mcdiscordchat.client.MessageEditor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {
    @Shadow protected abstract boolean isChatHidden();

    @Shadow protected abstract boolean isChatFocused();

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Shadow public abstract double getScale();

    @Shadow public abstract int getWidth();

    @Shadow protected abstract int getLineHeight();

    @Shadow private int chatScrollbarPos;

    @Shadow public abstract int getLinesPerPage();

    @Shadow @Final private List<GuiMessage> allMessages;

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Nullable public abstract ChatScreen getFocusedChat();

    @Unique
    private final Map<GuiMessage.Line, GuiMessage> lineToMessageMap = new WeakHashMap<>();
    private final Map<GuiMessage, GuiMessage.Line> messageToFirstLineMap = new WeakHashMap<>();

    @Inject(method = "render", at = @At("TAIL"))
    private void renderButtons(PoseStack matrices, int tickDelta, CallbackInfo ci) {
        if (isChatHidden() || !isChatFocused() || trimmedMessages.isEmpty()) return;
        final float chatScale = (float)getScale();
        final int width = Mth.ceil((float)getWidth() / chatScale);
        matrices.pushPose();
        matrices.translate(4.0, 8.0, 0.0);
        matrices.scale(chatScale, chatScale, 1);

        final int lineHeight = getLineHeight();
        final double lineSpacing = minecraft.options.chatLineSpacing().get();
        final double buttonOffset = 9.0 * lineSpacing;
        final double lineOffset = -8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing;

        final int foregroundColor = (int)(255.0 * minecraft.options.chatOpacity().get() * 0.9 + 0.1) << 24 | 0xffffff;
        final int backgroundAlpha = (int)(255.0 * minecraft.options.textBackgroundOpacity().get());
        final int backgroundNotHovered = backgroundAlpha << 24;
        final int backgroundHovered = backgroundNotHovered | 0x808080;

        final int mouseX = (int)((minecraft.mouseHandler.xpos() * (double)minecraft.getWindow().getGuiScaledWidth() / (double)minecraft.getWindow().getScreenWidth() + 4 - lineHeight + buttonOffset) / chatScale);
        final int mouseY = (int)((minecraft.mouseHandler.ypos() * (double)minecraft.getWindow().getGuiScaledHeight() / (double)minecraft.getWindow().getScreenHeight() - minecraft.getWindow().getGuiScaledHeight() + 48 - lineHeight + buttonOffset) / chatScale);

        final int visibleLineCount = getLinesPerPage();
        McDiscordChatClient.hoveredChatMessage = null;
        for (int i = 0; i + chatScrollbarPos < trimmedMessages.size() && i < visibleLineCount; i++) {
            final GuiMessage.Line line = trimmedMessages.get(i + chatScrollbarPos);
            if (line == null) continue;
            final ChatMessageInfo messageInfo = McDiscordChatClient.CHAT_MESSAGES_GUI_LOOKUP.get(lineToMessageMap.get(line));
            if (
                messageInfo == null ||
                messageInfo.getPermissions() == 0 ||
                messageToFirstLineMap.get(messageInfo.getHudMessage()) != line
            ) continue;
            final int y = -i * lineHeight;
            final int textY = (int)(y + lineOffset);
            final int buttonCount = Integer.bitCount(messageInfo.getPermissions());
            final boolean lineHovered = mouseY >= y - lineHeight && mouseY < y;
            final boolean firstHovered = lineHovered && mouseX >= width + 8 && mouseX < width + 9 + lineHeight;
            final boolean secondHovered = lineHovered && mouseX >= width + 9 + lineHeight && mouseX <= width + 10 + 2 * lineHeight;
            matrices.pushPose();
            matrices.translate(0, 0, 50);
            GuiComponent.fill(matrices, width + 8, y - lineHeight, width + 9 + lineHeight, y, firstHovered ? backgroundHovered : backgroundNotHovered);
            if (buttonCount > 1) {
                GuiComponent.fill(matrices, width + 9 + lineHeight, y - lineHeight, width + 10 + 2 * lineHeight, y, secondHovered ? backgroundHovered : backgroundNotHovered);
            }
            matrices.translate(0, 0, 50);
            GuiComponent.fill(matrices, width + 8, y - lineHeight, width + 9, y, foregroundColor);
            minecraft.font.drawShadow(matrices, messageInfo.isEditable() ? "\u270e" : "X", width + (messageInfo.isEditable() ? 10 : 11), textY, foregroundColor);
            if (buttonCount > 1) {
                GuiComponent.fill(matrices, width + 9 + lineHeight, y - lineHeight, width + 10 + lineHeight, y, foregroundColor);
                minecraft.font.drawShadow(matrices, "X", width + 12 + lineHeight, textY, foregroundColor);
            }
            matrices.popPose();
            messageInfo.setHoveredElement(firstHovered ? 1 : secondHovered ? 2 : 0);
            if (lineHovered) {
                McDiscordChatClient.hoveredChatMessage = messageInfo;
            }
        }

        final MessageEditor editor = (MessageEditor)getFocusedChat();
        if (editor != null) {
            if (editor.getEditingMessage() != null) {
                matrices.pushPose();
                matrices.translate(0, 0, 50);
                final int y = lineHeight + 2;
                final int textY = (int)(y + lineOffset);
                final boolean hovered = mouseY >= y - lineHeight && mouseY < y && mouseX >= -2 && mouseX < lineHeight;
                GuiComponent.fill(matrices, -2, y - lineHeight - 1, lineHeight + 1, y, hovered ? backgroundHovered : backgroundNotHovered);
                GuiComponent.fill(matrices, lineHeight + 1, y - lineHeight - 1, width + 48, y, backgroundNotHovered);
                matrices.translate(0, 0, 50);
                //noinspection SuspiciousNameCombination
                GuiComponent.fill(matrices, lineHeight, y - lineHeight - 1, lineHeight + 1, y, foregroundColor);
                minecraft.font.drawShadow(matrices, "X", 1, textY, foregroundColor);
                final int x = minecraft.font.drawShadow(matrices, "Editing ", lineHeight + 4, textY, foregroundColor);
                minecraft.font.drawShadow(
                    matrices,
                    messageToFirstLineMap.get(editor.getEditingMessage().getHudMessage()).content(),
                    x, textY, foregroundColor
                );
                matrices.popPose();
                editor.setHoveringCancelEdit(hovered);
            } else {
                editor.setHoveringCancelEdit(false);
            }
        }

        matrices.popPose();
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        at = @At("TAIL")
    )
    private void mapLinesToMessages(Component message, MessageSignature signature, int ticks, GuiMessageTag tag, boolean refresh, CallbackInfo ci) {
        lineToMessageMap.clear();
        GuiMessage.Line lastLine = null;
        GuiMessage currentMessage = null;
        int messageIndex = 0;
        for (final GuiMessage.Line line : trimmedMessages) {
            if (line.endOfEntry()) {
                if (currentMessage != null) {
                    messageToFirstLineMap.put(currentMessage, lastLine);
                }
                currentMessage = allMessages.get(messageIndex++);
            }
            lineToMessageMap.put(line, currentMessage);
            lastLine = line;
        }
        if (currentMessage != null) {
            messageToFirstLineMap.put(currentMessage, lastLine);
        }
    }
}
