package io.github.gaming32.mcdiscordchat.util;

import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;

public final class IntRange extends AbstractIntSet {
    private int minInclusive, maxInclusive;

    private IntRange(int minInclusive, int maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("minInclusive > maxInclusive!");
        }
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static IntRange inclusiveInclusive(int min, int max) {
        return new IntRange(min, max);
    }

    public static IntRange inclusiveExclusive(int min, int max) {
        return new IntRange(min, max - 1);
    }

    public static IntRange exclusiveInclusive(int min, int max) {
        return new IntRange(min + 1, max);
    }

    public static IntRange exclusiveExclusive(int min, int max) {
        return new IntRange(min + 1, max - 1);
    }

    @Override
    @NotNull
    public IntIterator iterator() {
        return new IntIterator() {
            int current = minInclusive;
            boolean finished = false;

            @Override
            public int nextInt() {
                if (finished) {
                    throw new NoSuchElementException();
                }
                if (current == maxInclusive) {
                    finished = true;
                    return maxInclusive;
                }
                return current++;
            }

            @Override
            public boolean hasNext() {
                return current <= maxInclusive && !finished;
            }
        };
    }

    @Override
    public boolean remove(int i) {
        if (minInclusive < maxInclusive) {
            if (i == minInclusive) {
                minInclusive++;
                return true;
            }
            if (i == maxInclusive) {
                maxInclusive--;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean add(int i) {
        if (minInclusive > Integer.MIN_VALUE && i == minInclusive - 1) {
            minInclusive--;
            return true;
        }
        if (maxInclusive < Integer.MAX_VALUE && i == maxInclusive + 1) {
            maxInclusive++;
            return true;
        }
        return false;
    }

    @Override
    public boolean contains(int i) {
        return minInclusive <= i && i <= maxInclusive;
    }

    @Override
    public int[] toIntArray() {
        final int[] result = new int[size()];
        int i = 0;
        for (int j = minInclusive; j <= maxInclusive; j++) {
            result[i++] = j;
        }
        return result;
    }

    @Override
    public int[] toArray(int[] ints) {
        if (ints.length < size()) {
            ints = new int[size()];
        }
        int value = minInclusive;
        for (int i = 0, l = Math.min(ints.length, size()); i < l; i++) {
            ints[i] = value++;
        }
        return ints;
    }

    @Override
    public boolean addAll(IntCollection c) {
        if (c instanceof IntRange range) {
            if (range.minInclusive < minInclusive || range.maxInclusive > maxInclusive) {
                minInclusive = range.minInclusive;
                maxInclusive = range.maxInclusive;
                return true;
            }
            return false;
        }
        return super.addAll(c);
    }

    @Override
    public boolean containsAll(IntCollection c) {
        if (c instanceof IntRange range) {
            return range.minInclusive >= minInclusive && range.maxInclusive <= maxInclusive;
        }
        return super.containsAll(c);
    }

    @Override
    public int size() {
        return maxInclusive - minInclusive + 1;
    }
}
