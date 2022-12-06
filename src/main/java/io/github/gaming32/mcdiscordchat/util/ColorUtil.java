package io.github.gaming32.mcdiscordchat.util;

public class ColorUtil {
    public static boolean isGrayscale(int color) {
        final int r = color >>> 16 & 0xff;
        if (r != (color >>> 8 & 0xff)) {
            return false;
        }
        return r == (color & 0xff);
    }
}
