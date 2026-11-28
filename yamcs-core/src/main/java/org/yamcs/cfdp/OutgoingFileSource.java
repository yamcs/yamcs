package org.yamcs.cfdp;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * The data of a file being sent (uplinked) by CFDP.
 * <p>
 * The data is read in pieces with positional reads, so the file does not have to be kept in memory. Two
 * implementations exist: a file on disk (normally a copy of the bucket object in the transfer directory) and an in
 * memory array (used for tests or when no transfer directory is available).
 * <p>
 * The reads are performed from the CFDP executor thread; they are not synchronized.
 */
public abstract class OutgoingFileSource implements AutoCloseable {
    static final int CHECKSUM_BUF_SIZE = 1024 * 1024;

    /**
     * @return the size of the file
     */
    public abstract long size();

    /**
     * Reads exactly {@code len} bytes starting at position {@code pos} in the file into {@code dst[off..off+len)}.
     *
     * @throws IOException
     *             if the data cannot be read or the file is shorter than pos+len
     */
    public abstract void read(long pos, byte[] dst, int off, int len) throws IOException;

    /**
     * Computes the CFDP modular checksum of the first {@code length} bytes of the file by streaming it.
     */
    public long checksum(long length) throws IOException {
        if (length > size()) {
            throw new IllegalArgumentException("length " + length + " > size " + size());
        }
        byte[] buf = new byte[(int) Math.min(CHECKSUM_BUF_SIZE, Math.max(length, 0))];
        long sum = 0;
        long pos = 0;
        while (pos < length) {
            int n = (int) Math.min(buf.length, length - pos);
            read(pos, buf, 0, n);
            sum += ModularChecksumCalculator.calculateChecksum(buf, 0, pos, n);
            pos += n;
        }
        return sum & 0xFFFFFFFFL;
    }

    @Override
    public abstract void close();

    public static OutgoingFileSource ofBytes(byte[] data) {
        return new ArraySource(data);
    }

    /**
     * Writes the data into a new file at the given path (truncating it if it exists), syncs it to disk and returns a
     * source reading from that file.
     */
    public static DiskSource createFile(Path path, byte[] data) throws IOException {
        FileChannel ch = FileChannel.open(path, CREATE, TRUNCATE_EXISTING, READ, WRITE);
        try {
            ByteBuffer bb = ByteBuffer.wrap(data);
            long pos = 0;
            while (bb.hasRemaining()) {
                pos += ch.write(bb, pos);
            }
            ch.force(false);
        } catch (IOException | RuntimeException e) {
            ch.close();
            throw e;
        }
        CfdpTransferStore.fsyncDirectory(path.getParent());
        return new DiskSource(path, ch, data.length);
    }

    /**
     * Opens an existing file
     */
    public static DiskSource openFile(Path path) throws IOException {
        FileChannel ch = FileChannel.open(path, READ);
        return new DiskSource(path, ch, ch.size());
    }

    static final class ArraySource extends OutgoingFileSource {
        final byte[] data;

        ArraySource(byte[] data) {
            this.data = data;
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public void read(long pos, byte[] dst, int off, int len) throws IOException {
            if (pos < 0 || pos + len > data.length) {
                throw new EOFException("Cannot read " + len + " bytes at " + pos + " from file of size " + data.length);
            }
            System.arraycopy(data, (int) pos, dst, off, len);
        }

        @Override
        public long checksum(long length) {
            return ModularChecksumCalculator.calculateChecksum(data, 0, 0, (int) length);
        }

        @Override
        public void close() {
        }
    }

    public static final class DiskSource extends OutgoingFileSource {
        final Path path;
        final FileChannel ch;
        final long size;

        DiskSource(Path path, FileChannel ch, long size) {
            this.path = path;
            this.ch = ch;
            this.size = size;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public void read(long pos, byte[] dst, int off, int len) throws IOException {
            if (pos < 0 || pos + len > size) {
                throw new EOFException("Cannot read " + len + " bytes at " + pos + " from " + path + " of size "
                        + size);
            }
            ByteBuffer bb = ByteBuffer.wrap(dst, off, len);
            long p = pos;
            while (bb.hasRemaining()) {
                int n = ch.read(bb, p);
                if (n < 0) {
                    throw new EOFException("Unexpected end of file " + path + " at " + p);
                }
                p += n;
            }
        }

        public Path getPath() {
            return path;
        }

        @Override
        public void close() {
            try {
                ch.close();
            } catch (IOException e) {
                // ignore
            }
        }

        @Override
        public String toString() {
            return path.toString();
        }
    }
}
