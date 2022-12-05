package io.github.gaming32.mcdiscordchat.util;

import net.minecraft.text.Text;
import net.minecraft.text.component.*;

public abstract class TextVisitor {
    public final void visit(Text text) {
        final TextComponent component = text.asComponent();
        if (!visitGeneric(text, component)) return;
        if (component instanceof LiteralComponent literal) {
            if (!visitLiteral(text, literal)) return;
        } else if (component instanceof TranslatableComponent translatable) {
            if (!visitTranslatable(text, translatable)) return;
        } else if (component instanceof SelectorComponent selector) {
            if (!visitSelector(text, selector)) return;
        } else if (component instanceof ScoreComponent score) {
            if (!visitScore(text, score)) return;
        } else if (component instanceof KeybindComponent keybind) {
            if (!visitKeybind(text, keybind)) return;
        } else if (component instanceof NbtComponent nbt) {
            if (!visitNbt(text, nbt)) return;
        }
        if (!postVisitGeneric(text, component)) return;
        for (final Text sibling : text.getSiblings()) {
            visit(sibling);
        }
    }

    protected boolean visitGeneric(Text text, TextComponent component) {
        return true;
    }

    protected boolean postVisitGeneric(Text text, TextComponent component) {
        return true;
    }

    protected boolean visitLiteral(Text text, LiteralComponent component) {
        return true;
    }

    protected boolean visitTranslatable(Text text, TranslatableComponent component) {
        return true;
    }

    protected boolean visitSelector(Text text, SelectorComponent component) {
        return true;
    }

    protected boolean visitScore(Text text, ScoreComponent component) {
        return true;
    }

    protected boolean visitKeybind(Text text, KeybindComponent component) {
        return true;
    }

    protected boolean visitNbt(Text text, NbtComponent component) {
        return true;
    }

    public static void walk(Text text, TextVisitor visitor) {
        visitor.visit(text);
    }
}
