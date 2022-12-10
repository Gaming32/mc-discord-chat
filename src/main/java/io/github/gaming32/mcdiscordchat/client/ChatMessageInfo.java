package io.github.gaming32.mcdiscordchat.client;

import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.MessageKey;
import net.minecraft.client.gui.hud.ChatHudMessage;

public final class ChatMessageInfo {
    private final MessageKey<?> key;
    private ChatHudMessage hudMessage;
    private int permissions;
    private int hoveredElement = -1;

    public ChatMessageInfo(MessageKey<?> key) {
        this.key = key;
    }

    public MessageKey<?> getKey() {
        return key;
    }

    public ChatHudMessage getHudMessage() {
        return hudMessage;
    }

    public void setHudMessage(ChatHudMessage hudMessage) {
        this.hudMessage = hudMessage;
    }

    public int getPermissions() {
        return permissions;
    }

    public void setPermissions(int permissions) {
        this.permissions = permissions;
    }

    public boolean isEditable() {
        return (permissions & McDiscordChat.MESSAGE_EDITABLE) != 0;
    }

    public boolean isDeletable() {
        return (permissions & McDiscordChat.MESSAGE_DELETABLE) != 0;
    }

    public int getHoveredElement() {
        return hoveredElement;
    }

    public void setHoveredElement(int hoveredElement) {
        this.hoveredElement = hoveredElement;
    }
}
