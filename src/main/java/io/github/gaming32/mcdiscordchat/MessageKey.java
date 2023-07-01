package io.github.gaming32.mcdiscordchat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

public final class MessageKey<T> {
    public enum Type {
        SIGNATURE(MessageSignature::new),
        TIMESTAMP(FriendlyByteBuf::readInstant),
        DISCORD(buf -> Long.toString(buf.readLong()));

        private final FriendlyByteBuf.Reader<?> reader;

        Type(FriendlyByteBuf.Reader<?> reader) {
            this.reader = reader;
        }

        public FriendlyByteBuf.Reader<?> getReader() {
            return reader;
        }
    }

    private final Type type;

    @NotNull
    private final T value;

    private MessageKey(Type type, T value) {
        this.type = type;
        this.value = Objects.requireNonNull(value);
    }

    public static MessageKey<MessageSignature> ofSignature(@NotNull MessageSignature signature) {
        return new MessageKey<>(Type.SIGNATURE, signature);
    }

    public static MessageKey<Instant> ofTimestamp(@NotNull Instant timestamp) {
        if (timestamp.getNano() != 0) {
            timestamp = Instant.ofEpochMilli(timestamp.toEpochMilli());
        }
        return new MessageKey<>(Type.TIMESTAMP, timestamp);
    }

    public static MessageKey<String> ofDiscord(@NotNull String discordId) {
        return new MessageKey<>(Type.DISCORD, discordId);
    }

    public static MessageKey<String> ofDiscord(long discordIdLong) {
        return ofDiscord(Long.toUnsignedString(discordIdLong));
    }

    public static MessageKey<?> ofMinecraft(PlayerChatMessage message) {
        if (message.headerSignature().isEmpty()) {
            return ofTimestamp(message.timeStamp());
        }
        return ofSignature(message.headerSignature());
    }

    public static MessageKey<?> read(FriendlyByteBuf buf) {
        final Type type = buf.readEnum(Type.class);
        return new MessageKey<>(type, type.getReader().apply(buf));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        switch (type) {
            case SIGNATURE -> ((MessageSignature)value).write(buf);
            case TIMESTAMP -> buf.writeInstant((Instant)value);
            case DISCORD -> buf.writeLong(Long.parseLong((String)value));
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MessageKey<?> other && type == other.type && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "MessageKey[type=" + type + ", value=" + value + ']';
    }

    public Type getType() {
        return type;
    }

    @NotNull
    public T getValue() {
        return value;
    }
}
