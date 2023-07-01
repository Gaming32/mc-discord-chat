package io.github.gaming32.mcdiscordchat.client;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.util.IntRange;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.function.Function;

public class EmojiFont implements GlyphProvider {
    private final Int2ObjectMap<GlyphInfo> glyphs = new Int2ObjectOpenHashMap<>();

    @NotNull
    @Override
    public IntSet getSupportedGlyphs() {
        return IntRange.inclusiveInclusive(McDiscordChat.PUA_FIRST, McDiscordChat.PUA_LAST);
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int codePoint) {
        if (codePoint == McDiscordChat.UNKNOWN_EMOJI_CP) {
            return SpecialGlyphs.MISSING;
        }
        if (codePoint < McDiscordChat.PUA_FIRST || codePoint > McDiscordChat.PUA_LAST) {
            return null;
        }
        return glyphs.computeIfAbsent(codePoint, cp -> new GlyphInfo() {
            @Override
            public float getAdvance() {
                return 7f;
            }

            @NotNull
            @Override
            public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
                return function.apply(new SheetGlyphInfo() {
                    NativeImage image;

                    @Override
                    public int getPixelWidth() {
                        return 64;
                    }

                    @Override
                    public int getPixelHeight() {
                        return 64;
                    }

                    @Override
                    public void upload(int offsetX, int offsetY) {
                        if (getImage() == null) return;
                        image.upload(0, offsetX, offsetY, 0, 0, image.getWidth(), image.getHeight(), false, false);
                    }

                    @Override
                    public boolean isColored() {
                        return true;
                    }

                    @Override
                    public float getOversample() {
                        if (getImage() == null) {
                            return 0;
                        }
                        return Math.max(getImage().getWidth(), image.getHeight()) / 7f;
                    }

                    private NativeImage getImage() {
                        if (image == null) {
                            final long emojiId = McDiscordChatClient.EMOJI_IDS.get(cp);
                            try (InputStream is = new URL("https://cdn.discordapp.com/emojis/" + emojiId + ".png?size=64").openStream()) {
                                image = NativeImage.read(is);
                            } catch (IOException e) {
                                McDiscordChat.LOGGER.error("Couldn't download emoji " + emojiId, e);
                            }
                        }
                        return image;
                    }
                });
            }
        });
    }
}
