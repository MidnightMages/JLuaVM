package dev.asdf00.jluavm.utils;

public class ByteArrayReader {
    private final byte[] data;
    private final int start;
    private final int end;
    private int cursor;

    public ByteArrayReader(byte[] data, int start, int end) {
        this.data = data;
        this.start = start;
        this.end = end;
        cursor = start;
    }

    public ByteArrayReader(byte[] data) {
        this(data, 0, data.length);
    }

    public void skip(int cnt) {
        rangeAssert(cnt);
        cursor += cnt;
    }

    public byte readByte() {
        rangeAssert(1);
        return data[cursor++];
    }

    public boolean readBool() {
        return readByte() == 1;
    }

    public int readInt() {
        rangeAssert(4);
        return (0xff & data[cursor++]) | ((0xff & data[cursor++]) << 8) | ((0xff & data[cursor++]) << 16) | ((0xff & data[cursor++]) << 24);
    }

    public long readLong() {
        rangeAssert(8);
        return (0xffL & data[cursor++]) | ((0xffL & data[cursor++]) << 8) | ((0xffL & data[cursor++]) << 16) | ((0xffL & data[cursor++]) << 24) |
                ((0xffL & data[cursor++]) << 32) | ((0xffL & data[cursor++]) << 40) | ((0xffL & data[cursor++]) << 48) | ((0xffL & data[cursor++]) << 56);
    }

    public byte[] readArray(int len) {
        rangeAssert(len);
        var res = new byte[len];
        System.arraycopy(data, cursor, res, 0, len);
        cursor += len;
        return res;
    }

    public int remaining() {
        return end - cursor;
    }

    public ByteArrayReader slice(int len) {
        rangeAssert(len);
        var res = new ByteArrayReader(data, cursor, cursor + len);
        cursor += len;
        return res;
    }

    private void rangeAssert(int cnt) {
        if (cursor + cnt > end) {
            throw new IllegalStateException("can not read %s bytes with only %s bytes left".formatted(cnt, remaining()));
        }
    }
}
