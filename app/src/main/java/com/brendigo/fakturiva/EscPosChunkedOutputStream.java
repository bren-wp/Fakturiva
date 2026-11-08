package com.brendigo.fakturiva;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Bounded ESC/POS output for slow bulk endpoints. A partial write is never
 * automatically replayed: the printer may already have printed those bytes.
 * No Android dependency, so failure semantics can be checked on the JVM.
 */
public final class EscPosChunkedOutputStream extends OutputStream {
    public interface Sink {
        /** Return bytes accepted, or throw on transport failure. */
        int write(byte[] bytes, int offset, int length) throws IOException;
    }

    private final Sink sink;
    private final byte[] buffer;
    private int count;
    private boolean closed;

    public EscPosChunkedOutputStream(Sink sink, int chunkSize) {
        this.sink = Objects.requireNonNull(sink, "sink");
        if (chunkSize < 1 || chunkSize > 16384)
            throw new IllegalArgumentException("Invalid USB buffer size");
        this.buffer = new byte[chunkSize];
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("Print stream is closed");
    }

    @Override public void write(int value) throws IOException {
        ensureOpen();
        if (count == buffer.length) flush();
        buffer[count++] = (byte) value;
    }

    @Override public void write(byte[] data, int offset, int length) throws IOException {
        ensureOpen();
        Objects.requireNonNull(data, "data");
        if (offset < 0 || length < 0 || offset > data.length - length)
            throw new IndexOutOfBoundsException();
        while (length > 0) {
            if (count == buffer.length) flush();
            int part = Math.min(length, buffer.length - count);
            System.arraycopy(data, offset, buffer, count, part);
            count += part;
            offset += part;
            length -= part;
        }
    }

    @Override public void flush() throws IOException {
        ensureOpen();
        int pending = count;
        // No automatic replay of an uncertain partial USB transfer.
        count = 0;
        int sent = 0;
        while (sent < pending) {
            int accepted = sink.write(buffer, sent, pending - sent);
            if (accepted <= 0 || accepted > pending - sent)
                throw new IOException("USB printer write failed (a partial receipt may exist)");
            sent += accepted;
        }
    }

    @Override public void close() throws IOException {
        if (closed) return;
        try { flush(); } finally { closed = true; }
    }
}
