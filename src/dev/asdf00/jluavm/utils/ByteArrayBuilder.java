package dev.asdf00.jluavm.utils;

import java.util.Arrays;
import java.util.Objects;

public class ByteArrayBuilder {
    private static final int DEFAULT_INITIAL = 16;

    private byte[] data;
    private int size;

    public ByteArrayBuilder() {
        this(DEFAULT_INITIAL);
    }

    public ByteArrayBuilder(int initial) {
        if (initial < 1) {
            throw new IllegalArgumentException("Positive initial value needed for ByteArrayBuilder");
        }
        data = new byte[initial];
        size = 0;
    }

    public ByteArrayBuilder(byte[] initial) {
        if (initial.length < 1) {
            data = new byte[DEFAULT_INITIAL];
            size = 0;
        } else {
            data = initial;
            size = initial.length;
        }
    }

    public int size() {
        return size;
    }

    public ByteArrayBuilder set(int idx, byte value) {
        Objects.checkIndex(idx, size);
        data[idx] = value;
        return this;
    }

    public ByteArrayBuilder append(byte value) {
        if (data.length == Integer.MAX_VALUE) {
            throw new IllegalStateException("Maximum size of ByteArrayBuilder (Integer.MAX_VALUE) exceeded");
        }
        if (data.length >= size) {
            // grow data array to fit another value
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = value;
        return this;
    }

    public ByteArrayBuilder append(boolean value) {
        return append((byte) (value ? 1 : 0));
    }

    public ByteArrayBuilder append(int value) {
        append((byte) value);
        append((byte) (value >> 8));
        append((byte) (value >> 16));
        append((byte) (value >> 24));
        return this;
    }

    public ByteArrayBuilder append(long value) {
        append((byte) value);
        append((byte) (value >> 8));
        append((byte) (value >> 16));
        append((byte) (value >> 24));
        append((byte) (value >> 32));
        append((byte) (value >> 40));
        append((byte) (value >> 48));
        append((byte) (value >> 56));
        return this;
    }

    public ByteArrayBuilder appendAll(byte[] bytes) {
        if ((long) bytes.length + (long) size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Maximum size of ByteArrayBuilder (Integer.MAX_VALUE) exceeded");
        }
        if (size + bytes.length > data.length) {
            // grow data array
            data = Arrays.copyOf(data, size + bytes.length);
            System.arraycopy(bytes, 0, data, size, bytes.length);
        } else {
            System.arraycopy(bytes, 0, data, size, bytes.length);
        }
        return this;
    }

    public ByteArrayBuilder appendAll(Iterable<Byte> bytes) {
        for (var b : bytes) {
            append(b);
        }
        return this;
    }

    public byte[] toArray() {
        if (data.length == size) {
            return data;
        }
        return Arrays.copyOfRange(data, 0, size);
    }
}
