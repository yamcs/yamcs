package org.yamcs.cfdp;

import java.util.Arrays;

/**
 * In-memory CFDP data file, limited to 2 GB.
 * <p>
 * The CFDP service stores the incoming files on disk ({@link DiskDataFile}); this class is used by tests and tools (e.g.
 * the simulator).
 */
public class DataFile extends AbstractDataFile {
    byte[] data;
    static int MAX_SIZE = Integer.MAX_VALUE - 8;

    public DataFile() {
        this(-1);
    }

    public DataFile(long size) {
        super(false);
        checkMaxSize(size);
        this.size = size;
        if (size > 0) {
            this.data = new byte[(int) size];
        } else {
            this.data = new byte[1024];
        }
    }

    @Override
    protected int writePiece(byte[] src, int srcOffset, long fileOffset, int length) {
        long end = fileOffset + length;
        if (data.length < end) {
            checkMaxSize(end);
            var newLength = (int) Long.min(end + 1024 * 1024, MAX_SIZE);
            data = Arrays.copyOf(data, newLength);
        }
        System.arraycopy(src, srcOffset, data, (int) fileOffset, length);
        return 0;
    }

    @Override
    public synchronized byte[] getData() {
        if (size == -1) {
            throw new IllegalStateException("Size unknown");
        }
        if (data.length != size) {
            return Arrays.copyOf(data, (int) size);
        } else {
            return data;
        }
    }

    @Override
    public synchronized void setSize(long size) {
        checkMaxSize(size);
        super.setSize(size);
    }

    public static int checkMaxSize(long size) {
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "in-memory file transfers larger than " + MAX_SIZE + " not supported");
        }
        return (int) size;
    }
}
