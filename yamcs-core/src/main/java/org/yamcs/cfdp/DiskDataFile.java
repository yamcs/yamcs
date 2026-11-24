package org.yamcs.cfdp;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32C;

import org.yamcs.logging.Log;

/**
 * CFDP data file stored on disk, surviving restarts.
 * <p>
 * Two files are used:
 * <ul>
 * <li>the data file: a sparse file where each piece of data is written at its offset with a positional write.</li>
 * <li>the journal: a header followed by fixed size records (offset: long, length: long, crc32c: int), one for each
 * piece written in the data file. The records are buffered in memory and written to the journal file when the buffer
 * is full or at {@link #sync()}.</li>
 * </ul>
 *
 * <p>
 * When the file is re-opened (after a restart or crash), the journal is replayed and the CRC of each record is verified
 * against the data file; the records whose CRC does not match (data not flushed to disk before a crash) or which are
 * truncated are dropped: the corresponding segments become missing again and will be re-requested (in acknowledged
 * mode). The modular checksum is recomputed in the same pass from the verified data.
 * <p>
 * For each received segment the CRC32C is maintained in memory (by combining the CRCs of the pieces) such that the
 * journal can be compacted into one record per segment without reading the data back. The compaction is performed at
 * sync when the number of records in the journal is much larger than the number of segments.
 */
public class DiskDataFile extends AbstractDataFile {
    static final Log log = new Log(DiskDataFile.class);

    // "CFDPJRNL"
    static final long MAGIC = 0x434644504A524E4CL;
    static final int VERSION = 1;
    static final int HEADER_SIZE = 16;
    static final int RECORD_SIZE = 20;
    static final int JOURNAL_BUF_SIZE = 64 * 1024;
    static final int READ_BUF_SIZE = 1024 * 1024;

    // compact the journal if it has more than this number of records...
    static int compactMinRecords = 64 * 1024;
    // ...and more than this factor times the number of segments
    static final int COMPACT_FACTOR = 4;

    final Path dataPath;
    final Path journalPath;
    private FileChannel dataCh;
    private FileChannel journalCh;
    private final ByteBuffer journalBuf = ByteBuffer.allocate(JOURNAL_BUF_SIZE).order(ByteOrder.BIG_ENDIAN);
    // number of records in the journal file + buffered
    private long journalRecords;
    private boolean dirty;
    private final CRC32C crc32c = new CRC32C();
    private boolean closed = false;

    // statistics about the last open (restore)
    private long droppedRecords;
    private long droppedBytes;

    private DiskDataFile(Path dataPath, Path journalPath) {
        super(true);
        this.dataPath = dataPath;
        this.journalPath = journalPath;
    }

    /**
     * Creates a new (empty) data file; if the files exist, they are truncated.
     */
    public static DiskDataFile create(Path dataPath, Path journalPath) throws IOException {
        DiskDataFile df = new DiskDataFile(dataPath, journalPath);
        df.dataCh = FileChannel.open(dataPath, CREATE, TRUNCATE_EXISTING, READ, WRITE);
        try {
            df.journalCh = FileChannel.open(journalPath, CREATE, TRUNCATE_EXISTING, WRITE);
            DiskDataFile.writeFully(df.journalCh, header());
        } catch (IOException e) {
            df.dataCh.close();
            throw e;
        }
        df.journalRecords = 0;
        return df;
    }

    /**
     * Opens an existing data file, replaying and verifying the journal.
     * <p>
     * If the journal does not exist or its header is invalid, the file is considered empty. If the data file does not
     * exist, it is created.
     *
     * @param maxEnd
     *            records extending beyond this offset are dropped. Use -1 for no limit.
     */
    public static DiskDataFile open(Path dataPath, Path journalPath, long maxEnd) throws IOException {
        DiskDataFile df = new DiskDataFile(dataPath, journalPath);
        df.dataCh = FileChannel.open(dataPath, CREATE, READ, WRITE);
        try {
            df.replayJournal(maxEnd);
            // rewrite the journal with only the verified records, merged into segments;
            // this also gets rid of the dropped and truncated records
            df.dataCh.force(false);
            df.compactJournal();
        } catch (IOException | RuntimeException e) {
            df.close();
            throw e;
        }
        return df;
    }

    private static ByteBuffer header() {
        ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE);
        bb.putLong(MAGIC).putInt(VERSION).putInt(RECORD_SIZE);
        bb.flip();
        return bb;
    }

    private void replayJournal(long maxEnd) throws IOException {
        if (!Files.exists(journalPath)) {
            log.warn("Journal {} does not exist, considering the file empty", journalPath);
            return;
        }
        long dataLength = dataCh.size();
        ByteBuffer readBuf = ByteBuffer.allocateDirect(READ_BUF_SIZE);
        ByteBuffer jbuf = ByteBuffer.allocate(JOURNAL_BUF_SIZE);
        try (FileChannel jch = FileChannel.open(journalPath, READ)) {
            long jsize = jch.size();
            if (jsize < HEADER_SIZE) {
                log.warn("Journal {} has no valid header, considering the file empty", journalPath);
                return;
            }
            jbuf.limit(HEADER_SIZE);
            readFully(jch, jbuf, 0);
            jbuf.flip();
            long magic = jbuf.getLong();
            int version = jbuf.getInt();
            int recSize = jbuf.getInt();
            if (magic != MAGIC || version != VERSION || recSize != RECORD_SIZE) {
                log.warn("Journal {} has an invalid header, considering the file empty", journalPath);
                return;
            }
            long numRecords = (jsize - HEADER_SIZE) / RECORD_SIZE;
            if ((jsize - HEADER_SIZE) % RECORD_SIZE != 0) {
                log.info("Journal {} has a truncated last record, ignoring it", journalPath);
                droppedRecords++;
            }
            long jpos = HEADER_SIZE;
            long n = 0;
            while (n < numRecords) {
                jbuf.clear();
                int toRead = (int) Math.min(JOURNAL_BUF_SIZE / RECORD_SIZE, numRecords - n) * RECORD_SIZE;
                jbuf.limit(toRead);
                readFully(jch, jbuf, jpos);
                jpos += toRead;
                jbuf.flip();
                while (jbuf.remaining() >= RECORD_SIZE) {
                    long offset = jbuf.getLong();
                    long length = jbuf.getLong();
                    int crc = jbuf.getInt();
                    n++;
                    replayRecord(offset, length, crc, maxEnd, dataLength, readBuf);
                }
            }
            journalRecords = n;
        }
        if (droppedRecords > 0) {
            log.warn("Journal {}: {} records ({} bytes) could not be verified and have been dropped",
                    journalPath, droppedRecords, droppedBytes);
        }
    }

    private void replayRecord(long offset, long length, int crc, long maxEnd, long dataLength, ByteBuffer readBuf)
            throws IOException {
        long end = offset + length;
        if (offset < 0 || length <= 0 || end > dataLength || (maxEnd >= 0 && end > maxEnd)) {
            drop(length);
            return;
        }
        if (segments.overlaps(offset, end)) {
            // should not happen because the data is written only once and the journal is compacted at restore
            drop(length);
            return;
        }
        crc32c.reset();
        long msum = 0;
        long pos = offset;
        while (pos < end) {
            readBuf.clear();
            readBuf.limit((int) Math.min(readBuf.capacity(), end - pos));
            if (!readFully(dataCh, readBuf, pos)) {
                drop(length);
                return;
            }
            readBuf.flip();
            crc32c.update(readBuf);
            readBuf.rewind();
            msum += ModularChecksumCalculator.calculateChecksum(readBuf, pos);
            pos += readBuf.limit();
        }
        if ((int) crc32c.getValue() != crc) {
            drop(length);
            return;
        }
        segments.addDisjoint(offset, end, crc);
        modularChecksum += msum;
    }

    private void drop(long length) {
        droppedRecords++;
        if (length > 0) {
            droppedBytes += length;
        }
    }

    @Override
    protected int writePiece(byte[] data, int dataOffset, long fileOffset, int length) {
        checkOpen();
        crc32c.reset();
        crc32c.update(data, dataOffset, length);
        int crc = (int) crc32c.getValue();
        try {
            ByteBuffer bb = ByteBuffer.wrap(data, dataOffset, length);
            long pos = fileOffset;
            while (bb.hasRemaining()) {
                pos += dataCh.write(bb, pos);
            }
            if (journalBuf.remaining() < RECORD_SIZE) {
                flushJournal();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing to " + dataPath, e);
        }
        journalBuf.putLong(fileOffset).putLong(length).putInt(crc);
        journalRecords++;
        dirty = true;
        return crc;
    }

    private void flushJournal() throws IOException {
        journalBuf.flip();
        writeFully(journalCh, journalBuf);
        journalBuf.clear();
    }

    /**
     * Writes the buffered journal records and forces the data and journal to disk. If the journal is large compared to
     * the number of segments, it is compacted.
     */
    @Override
    public void sync() throws IOException {
        // the force is done outside the lock such that the PDUs can continue to be written in parallel
        // (the sync is called from a different thread than the one writing the data)
        boolean compact;
        FileChannel dch, jch;
        synchronized (this) {
            if (!dirty || closed) {
                return;
            }
            flushJournal();
            dirty = false;
            compact = journalRecords > compactMinRecords
                    && journalRecords > COMPACT_FACTOR * (long) segments.count();
            dch = dataCh;
            jch = journalCh;
        }
        if (compact) {
            synchronized (this) {
                if (closed) {
                    return;
                }
                // all the data covered by the segments has to be on disk before writing the compacted journal
                flushJournal();
                dataCh.force(false);
                compactJournal();
            }
        } else {
            dch.force(false);
            jch.force(false);
        }
    }

    /**
     * Rewrite the journal with one record per segment. The data has to be on disk before calling this.
     */
    private void compactJournal() throws IOException {
        Path tmp = journalPath.resolveSibling(journalPath.getFileName() + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, CREATE, TRUNCATE_EXISTING, WRITE)) {
            ByteBuffer bb = ByteBuffer.allocate(JOURNAL_BUF_SIZE);
            bb.put(header());
            for (var seg : segments.segments()) {
                long offset = seg.start();
                long length = seg.length();
                if (bb.remaining() < RECORD_SIZE) {
                    bb.flip();
                    writeFully(ch, bb);
                    bb.clear();
                }
                bb.putLong(offset).putLong(length).putInt(seg.crc());
            }
            bb.flip();
            writeFully(ch, bb);
            ch.force(false);
        }
        if (journalCh != null) {
            journalCh.close();
        }
        Files.move(tmp, journalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        CfdpTransferStore.fsyncDirectory(journalPath.getParent());
        journalCh = FileChannel.open(journalPath, WRITE);
        journalCh.position(journalCh.size());
        journalRecords = segments.count();
    }

    @Override
    public synchronized void setSize(long size) {
        super.setSize(size);
    }

    /**
     * Reads the whole file into a byte array. The size has to be known and smaller than 2GB.
     */
    @Override
    public synchronized byte[] getData() {
        checkOpen();
        if (size < 0) {
            throw new IllegalStateException("Size unknown");
        }
        if (size > Integer.MAX_VALUE - 8) {
            throw new UnsupportedOperationException("Cannot load in memory a file of size " + size);
        }
        byte[] b = new byte[(int) size];
        int pos = 0;
        try {
            while (pos < size) {
                // read in chunks such that the JDK does not allocate a huge temporary direct buffer
                ByteBuffer bb = ByteBuffer.wrap(b, pos, Math.min(READ_BUF_SIZE, (int) size - pos));
                int n = dataCh.read(bb, pos);
                if (n < 0) {
                    break; // sparse file shorter than the size, the remaining is 0
                }
                pos += n;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading " + dataPath, e);
        }
        return b;
    }

    /**
     * Number of records dropped when the file has been opened, because they could not be verified
     */
    public long getDroppedRecords() {
        return droppedRecords;
    }

    /**
     * Number of bytes corresponding to the records dropped when the file has been opened.
     */
    public long getDroppedBytes() {
        return droppedBytes;
    }

    long getJournalRecords() {
        return journalRecords;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            if (journalCh != null && journalBuf.position() > 0) {
                flushJournal();
            }
        } catch (IOException e) {
            log.warn("Error flushing journal {}", journalPath, e);
        }
        closeChannels();
    }

    /**
     * Closes the files without writing the buffered journal records; used in tests to simulate a crash.
     */
    synchronized void closeWithoutFlush() {
        closeChannels();
    }

    private void closeChannels() {
        closed = true;
        for (FileChannel ch : new FileChannel[] { dataCh, journalCh }) {
            if (ch != null) {
                try {
                    ch.close();
                } catch (IOException e) {
                    log.warn("Error closing file", e);
                }
            }
        }
    }

    @Override
    public synchronized void delete() {
        closeChannels();
        try {
            Files.deleteIfExists(dataPath);
            Files.deleteIfExists(journalPath);
        } catch (IOException e) {
            log.warn("Error deleting {}", dataPath, e);
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("File " + dataPath + " is closed");
        }
    }

    private static void writeFully(FileChannel ch, ByteBuffer bb) throws IOException {
        while (bb.hasRemaining()) {
            ch.write(bb);
        }
    }

    /**
     * read until the buffer is full
     *
     * @return false if end of file has been reached before
     */
    private static boolean readFully(FileChannel ch, ByteBuffer bb, long pos) throws IOException {
        while (bb.hasRemaining()) {
            int n = ch.read(bb, pos);
            if (n < 0) {
                return false;
            }
            pos += n;
        }
        return true;
    }

    @Override
    public String toString() {
        return dataPath.toString();
    }
}
