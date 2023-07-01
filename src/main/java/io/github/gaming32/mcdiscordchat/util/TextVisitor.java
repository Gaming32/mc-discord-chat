package io.github.gaming32.mcdiscordchat.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.*;

public abstract class TextVisitor {
    public final void visit(Component text) {
        final ComponentContents component = text.getContents();
        if (!visitGeneric(text, component)) return;
        if (component instanceof LiteralContents literal) {
            if (!visitLiteral(text, literal)) return;
        } else if (component instanceof TranslatableContents translatable) {
            if (!visitTranslatable(text, translatable)) return;
        } else if (component instanceof SelectorContents selector) {
            if (!visitSelector(text, selector)) return;
        } else if (component instanceof ScoreContents score) {
            if (!visitScore(text, score)) return;
        } else if (component instanceof KeybindContents keybind) {
            if (!visitKeybind(text, keybind)) return;
        } else if (component instanceof NbtContents nbt) {
            if (!visitNbt(text, nbt)) return;
        }
        if (!postVisitGeneric(text, component)) return;
        for (final Component sibling : text.getSiblings()) {
            visit(sibling);
        }
    }

    protected boolean visitGeneric(Component text, ComponentContents component) {
        return true;
    }

    protected boolean postVisitGeneric(Component text, ComponentContents component) {
        return true;
    }

    protected boolean visitLiteral(Component text, LiteralContents component) {
        return true;
    }

    protected boolean visitTranslatable(Component text, TranslatableContents component) {
        return true;
    }

    protected boolean visitSelector(Component text, SelectorContents component) {
        return true;
    }

    protected boolean visitScore(Component text, ScoreContents component) {
        return true;
    }

    protected boolean visitKeybind(Component text, KeybindContents component) {
        return true;
    }

    protected boolean visitNbt(Component text, NbtContents component) {
        return true;
    }

    public static void walk(Component text, TextVisitor visitor) {
        visitor.visit(text);
    }
}
