package io.github.gaming32.mcdiscordchat.client;

import io.github.gaming32.mcdiscordchat.McDiscordChat;
import net.minecraft.client.gui.hud.ChatHudMessage;

public final class ChatMessageInfo {
    private final long id;
    private ChatHudMessage message;
    private final int flags;
    private int hoveredElement = -1;

    public ChatMessageInfo(long id, ChatHudMessage message, int flags) {
        this.id = id;
        this.message = message;
        this.flags = flags;
    }

    public long getId() {
        return id;
    }

    public ChatHudMessage getMessage() {
        return message;
    }

    public void setMessage(ChatHudMessage message) {
        this.message = message;
    }

    public int getFlags() {
        return flags;
    }

    public boolean isEditable() {
        return (flags & McDiscordChat.MESSAGE_EDITABLE) != 0;
    }

    public boolean isDeletable() {
        return (flags & McDiscordChat.MESSAGE_DELETABLE) != 0;
    }

    public int getHoveredElement() {
        return hoveredElement;
    }

    public void setHoveredElement(int hoveredElement) {
        this.hoveredElement = hoveredElement;
    }
}
