package io.github.gaming32.mcdiscordchat;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.message.MessageSignature;
import net.minecraft.network.message.SignedChatMessage;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

public final class MessageKey<T> {
    public enum Type {
        SIGNATURE(MessageSignature::new),
        TIMESTAMP(PacketByteBuf::readInstant),
        DISCORD(buf -> Long.toString(buf.readLong()));

        private final PacketByteBuf.Reader<?> reader;

        Type(PacketByteBuf.Reader<?> reader) {
            this.reader = reader;
        }

        public PacketByteBuf.Reader<?> getReader() {
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

    public static MessageKey<?> ofMinecraft(SignedChatMessage message) {
        if (message.headerSignature().m_slflbccb()) {
            return ofTimestamp(message.getTimestamp());
        }
        return ofSignature(message.headerSignature());
    }

    public static MessageKey<?> read(PacketByteBuf buf) {
        final Type type = buf.readEnumConstant(Type.class);
        return new MessageKey<>(type, type.getReader().apply(buf));
    }

    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(type);
        switch (type) {
            case SIGNATURE -> ((MessageSignature)value).m_mpdsgmtj(buf);
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
