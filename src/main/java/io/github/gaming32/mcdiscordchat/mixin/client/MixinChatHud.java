package io.github.gaming32.mcdiscordchat.mixin.client;

import io.github.gaming32.mcdiscordchat.client.ChatMessageInfo;
import io.github.gaming32.mcdiscordchat.client.McDiscordChatClient;
import io.github.gaming32.mcdiscordchat.client.MessageEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudMessage;
import net.minecraft.client.gui.hud.ChatMessageTag;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.message.MessageSignature;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
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

@Mixin(ChatHud.class)
public abstract class MixinChatHud {
    @Shadow protected abstract boolean isChatHidden();

    @Shadow protected abstract boolean isChatFocused();

    @Shadow @Final private List<ChatHudMessage.Line> visibleMessages;

    @Shadow public abstract double getChatScale();

    @Shadow public abstract int getWidth();

    @Shadow protected abstract int getLineHeight();

    @Shadow private int scrolledLines;

    @Shadow public abstract int getVisibleLineCount();

    @Shadow @Final private List<ChatHudMessage> messages;

    @Shadow @Final private MinecraftClient client;

    @Shadow @Nullable public abstract ChatScreen getChatScreen();

    @Unique
    private final Map<ChatHudMessage.Line, ChatHudMessage> lineToMessageMap = new WeakHashMap<>();
    private final Map<ChatHudMessage, ChatHudMessage.Line> messageToFirstLineMap = new WeakHashMap<>();

    @Inject(method = "render", at = @At("TAIL"))
    private void renderButtons(MatrixStack matrices, int tickDelta, CallbackInfo ci) {
        if (isChatHidden() || !isChatFocused() || visibleMessages.isEmpty()) return;
        final float chatScale = (float)getChatScale();
        final int width = MathHelper.ceil((float)getWidth() / chatScale);
        matrices.push();
        matrices.translate(4.0, 8.0, 0.0);
        matrices.scale(chatScale, chatScale, 1);

        final int lineHeight = getLineHeight();
        final double lineSpacing = client.options.getChatLineSpacing().get();
        final double lineOffset = -8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing;

        final int foregroundColor = (int)(255.0 * client.options.getChatOpacity().get() * 0.9 + 0.1) << 24 | 0xffffff;
        final int backgroundAlpha = (int)(255.0 * client.options.getTextBackgroundOpacity().get());
        final int backgroundNotHovered = backgroundAlpha << 24;
        final int backgroundHovered = backgroundNotHovered | 0x808080;

        final int mouseX = (int)((client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth() + 4 - lineHeight) / chatScale);
        final int mouseY = (int)(client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight()) - client.getWindow().getScaledHeight() + 48 - lineHeight;

        final int visibleLineCount = getVisibleLineCount();
        McDiscordChatClient.hoveredChatMessage = null;
        for (int i = 0; i + scrolledLines < visibleMessages.size() && i < visibleLineCount; i++) {
            final ChatHudMessage.Line line = visibleMessages.get(i + scrolledLines);
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
            matrices.push();
            matrices.translate(0, 0, 50);
            DrawableHelper.fill(matrices, width + 8, y - lineHeight, width + 9 + lineHeight, y, firstHovered ? backgroundHovered : backgroundNotHovered);
            if (buttonCount > 1) {
                DrawableHelper.fill(matrices, width + 9 + lineHeight, y - lineHeight, width + 10 + 2 * lineHeight, y, secondHovered ? backgroundHovered : backgroundNotHovered);
            }
            matrices.translate(0, 0, 50);
            DrawableHelper.fill(matrices, width + 8, y - lineHeight, width + 9, y, foregroundColor);
            client.textRenderer.drawWithShadow(matrices, messageInfo.isEditable() ? "\u270e" : "X", width + (messageInfo.isEditable() ? 10 : 11), textY, foregroundColor);
            if (buttonCount > 1) {
                DrawableHelper.fill(matrices, width + 9 + lineHeight, y - lineHeight, width + 10 + lineHeight, y, foregroundColor);
                client.textRenderer.drawWithShadow(matrices, "X", width + 12 + lineHeight, textY, foregroundColor);
            }
            matrices.pop();
            messageInfo.setHoveredElement(firstHovered ? 1 : secondHovered ? 2 : 0);
            if (lineHovered) {
                McDiscordChatClient.hoveredChatMessage = messageInfo;
            }
        }

        final MessageEditor editor = (MessageEditor)getChatScreen();
        if (editor != null) {
            if (editor.getEditingMessage() != null) {
                matrices.push();
                matrices.translate(0, 0, 50);
                final int y = lineHeight + 2;
                final int textY = (int)(y + lineOffset);
                final boolean hovered = mouseY >= y - lineHeight && mouseY < y && mouseX >= -2 && mouseX < lineHeight;
                DrawableHelper.fill(matrices, -2, y - lineHeight - 1, lineHeight + 1, y, hovered ? backgroundHovered : backgroundNotHovered);
                DrawableHelper.fill(matrices, lineHeight + 1, y - lineHeight - 1, width + 48, y, backgroundNotHovered);
                matrices.translate(0, 0, 50);
                //noinspection SuspiciousNameCombination
                DrawableHelper.fill(matrices, lineHeight, y - lineHeight - 1, lineHeight + 1, y, foregroundColor);
                client.textRenderer.drawWithShadow(matrices, "X", 1, textY, foregroundColor);
                final int x = client.textRenderer.drawWithShadow(matrices, "Editing ", lineHeight + 4, textY, foregroundColor);
                client.textRenderer.drawWithShadow(
                    matrices,
                    messageToFirstLineMap.get(editor.getEditingMessage().getHudMessage()).content(),
                    x, textY, foregroundColor
                );
                matrices.pop();
                editor.setHoveringCancelEdit(hovered);
            } else {
                editor.setHoveringCancelEdit(false);
            }
        }

        matrices.pop();
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignature;ILnet/minecraft/client/gui/hud/ChatMessageTag;Z)V",
        at = @At("TAIL")
    )
    private void mapLinesToMessages(Text message, MessageSignature signature, int ticks, ChatMessageTag tag, boolean refresh, CallbackInfo ci) {
        lineToMessageMap.clear();
        ChatHudMessage.Line lastLine = null;
        ChatHudMessage currentMessage = null;
        int messageIndex = 0;
        for (final ChatHudMessage.Line line : visibleMessages) {
            if (line.endOfEntry()) {
                if (currentMessage != null) {
                    messageToFirstLineMap.put(currentMessage, lastLine);
                }
                currentMessage = messages.get(messageIndex++);
            }
            lineToMessageMap.put(line, currentMessage);
            lastLine = line;
        }
        if (currentMessage != null) {
            messageToFirstLineMap.put(currentMessage, lastLine);
        }
    }
}
