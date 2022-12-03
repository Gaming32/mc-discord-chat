package io.github.gaming32.mcdiscordchat.client;

import com.mojang.blaze3d.font.Font;
import com.mojang.blaze3d.font.Glyph;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.texture.NativeImage;
import io.github.gaming32.mcdiscordchat.McDiscordChat;
import io.github.gaming32.mcdiscordchat.util.IntRange;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.font.SpecialFontGlyph;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.function.Function;

public class EmojiFont implements Font {
    private final Int2ObjectMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();

    @Override
    public IntSet getProvidedGlyphs() {
        return IntRange.inclusiveInclusive(McDiscordChat.PUA_FIRST, McDiscordChat.PUA_LAST);
    }

    @Nullable
    @Override
    public Glyph getGlyph(int codePoint) {
        if (codePoint == McDiscordChat.UNKNOWN_EMOJI_CP) {
            return SpecialFontGlyph.MISSING;
        }
        return glyphs.computeIfAbsent(codePoint, cp -> new Glyph() {
            @Override
            public float getAdvance() {
                return 7f;
            }

            @Override
            public GlyphRenderer bake(Function<SheetGlyphInfo, GlyphRenderer> function) {
                return function.apply(new SheetGlyphInfo() {
                    NativeImage image;

                    @Override
                    public int getWidth() {
                        return 64;
                    }

                    @Override
                    public int getHeight() {
                        return 64;
                    }

                    @Override
                    public void upload(int offsetX, int offsetY) {
                        if (image == null) {
                            final long emojiId = McDiscordChatClient.EMOJI_IDS.get(cp);
                            try (InputStream is = new URL("https://cdn.discordapp.com/emojis/" + emojiId + ".png?size=64").openStream()) {
                                image = NativeImage.read(is);
                            } catch (IOException e) {
                                McDiscordChat.LOGGER.error("Couldn't download emoji " + emojiId, e);
                                return;
                            }
                        }
                        image.upload(0, offsetX, offsetY, 0, 0, 64, 64, false, false);
                    }

                    @Override
                    public boolean isColored() {
                        return true;
                    }

                    @Override
                    public float getOversample() {
                        return 1f;
                    }
                });
            }
        });
    }
}
