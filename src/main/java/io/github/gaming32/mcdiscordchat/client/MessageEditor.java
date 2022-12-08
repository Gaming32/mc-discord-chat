package io.github.gaming32.mcdiscordchat.client;

public interface MessageEditor {
    ChatMessageInfo getEditingMessage();

    void setHoveringCancelEdit(boolean hoveringCancelEdit);
}
