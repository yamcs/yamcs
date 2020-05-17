package org.yamcs.replication;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.logging.Log;
import org.yamcs.utils.StringConverter;

/**
 * Stores transactions in a memory mapped file. The data is split into pages, each page has a fixed number of
 * transactions.
 * <p>
 * An index gives a pointer to the beginning of each page to allow to jump faster to a given transaction number.
 * <p>
 * The metadata transactions form a linked list in order to allow to send them all when a client connects.
 * 
 * <p>
 * Header:
 * 
 * <pre>
 * 12 bytes magic "YAMCS_STREAM"
 *  1 byte version
 *  3 bytes spare
 *  8 bytes first_id =  first transaction in the file = file_id - used for consistency check(if someone renames the file)
 *  4 bytes page_size - number of transactions on page 
 *  4 bytes max_pages - max number of pages (and the size of the index)
 * 
 *  8 bytes last_mod = last modification time
 *  4 bytes n =  number of full pages. If n=max_pages, the file is full, cannot be written to it
 *  4 bytes m = number of transactions on the page n
 *  4 bytes firstMetadataPos - position of the first metadata transaction
 *  (max_pages+1) x 4 bytes idx - transaction index 
 *      idx[i] (i=0..max_pages) - offset in the file where transaction with id id_first + i*m starts
 *      idx[i] = 0 -> no such transaction. this means num_tx < i*m
 *      idx[max_pages] -> pointer to the end of the file.
 * </pre>
 * 
 * transaction data:
 * 
 * <pre>
 * 
 * 1 byte type
 * 3 bytes - size of the data that follows = 8 + n (+4 for metadata)
 * 8 bytes txid
 * n bytes data
 * [next metadata position] - for metadata the first 4 bytes of the data is the position of the next metadata record
 * </pre>
 * 
 * <p>
 * The methods of this class throw {@link UncheckedIOException} instead of {@link IOException}. When
 * working with memory mapped files in java, an IO error will cause an unspecified unchecked exception or even crash of
 * Java (because
 * file data is accessed using memory reads/writes). Therefore we prefer not to give a false sense of security by
 * throwing
 * IOException only in some limited situations and converted all these to {@link UncheckedIOException}..
 * 
 * <p>
 * The one occasion when Java crashes while no hardware failures are present is when the disk is full.
 * <p>
 * TODO: add a checker and stop writing data if the disk usage is above a threshold.
 * 
 * @author nm
 *
 */
public class ReplicationFile implements Closeable {
    static final String RPL_FILENAME_PREFIX = "RPL";
    final static byte[] MAGIC = { 'Y', 'A', 'M', 'C', 'S', '_', 'S', 'T', 'R', 'E', 'A', 'M' };

    final Log log;

    ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private MappedByteBuffer buf;
    private int lastMetadataPos;
    private FileChannel fc;
    final private boolean readOnly;
    final private Header1 hdr1;
    final private Header2 hdr2;
    private boolean fileFull = false;
    File file;
    int syncNumTx = 500;
    int syncCount = syncNumTx;

    class Header1 { // this is the first part - fixed - of the header

        final static byte VERSION = 0;
        final static int LENGTH = 32;
        final long firstId; // first transaction id
        final int pageSize, maxPages;
        // this is written at the beginning of the file

        Header1(long id, int pageSize, int maxPages) {
            this.firstId = id;
            this.pageSize = pageSize;
            this.maxPages = maxPages;
            buf.put(MAGIC);
            buf.putInt(VERSION << 24);
            buf.putLong(firstId);
            buf.putInt(pageSize);
            buf.putInt(maxPages);
        }

        public Header1(long firstTxId) {
            checkHdr1(firstTxId);
            firstId = firstTxId;
            pageSize = buf.getInt();
            maxPages = buf.getInt();
        }

        private void checkHdr1(long firstTxId) {
            byte[] magic = new byte[MAGIC.length];
            buf.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new CorruptedFileException(file,
                        "bad file, magic entry does not match: " + StringConverter.arrayToHexString(magic)
                                + ". Expected " + StringConverter.arrayToHexString(MAGIC));
            }
            int version = buf.getInt() >> 24;
            if (version != VERSION) {
                throw new CorruptedFileException(file, "bad version: " + version + ". Expected " + VERSION);
            }
            long id = buf.getLong();
            if (id != firstTxId) {
                throw new CorruptedFileException(file, "bad firstId " + id + " expected " + firstTxId);
            }
        }

        @Override
        public String toString() {
            return "Header1 [firstId=" + firstId + ", pageSize=" + pageSize + ", maxPages=" + maxPages + "]";
        }
    }

    class Header2 {
        final static int HDR_IDX_OFFSET = Header1.LENGTH + 20;

        int numFullPages; // number of full pages
        int lastPageNumTx; // number of transaction on the last page
        long lastMod; // last modification

        public Header2(boolean newFile) {
            if (newFile) {
                this.numFullPages = 0;
                this.lastPageNumTx = 0;
                this.lastMod = System.currentTimeMillis();
                write();
                buf.position(HDR_IDX_OFFSET - 4);
                buf.putInt(0);// first metadata pointer

                writeIndex(0, endOffset());
                buf.putInt(endOffset());// position of the transaction 0
                for (int i = 1; i <= hdr1.maxPages; i++) {
                    writeIndex(i, 0);
                }
            } else {
                buf.position(Header1.LENGTH);
                lastMod = buf.getLong();
                numFullPages = buf.getInt();
                lastPageNumTx = buf.getInt();
            }
        }

        void write() {
            buf.putLong(Header1.LENGTH, lastMod);
            buf.putInt(Header1.LENGTH + 8, numFullPages);
            buf.putInt(Header1.LENGTH + 12, lastPageNumTx);
        }

        public int firstMetadataPointer() {
            return buf.getInt(HDR_IDX_OFFSET - 4);
        }

        public int endOffset() {
            return HDR_IDX_OFFSET + 4 * (hdr1.maxPages + 1);
        }

        public int getIndex(int n) {
            return buf.getInt(HDR_IDX_OFFSET + n * 4);
        }

        void writeIndex(int n, int txPos) {
            buf.putInt(HDR_IDX_OFFSET + n * 4, txPos);
        }

        @Override
        public String toString() {
            return "Header2 [numFullPages=" + numFullPages + ", lastPageNumTx=" + lastPageNumTx + ", lastMod="
                    + Instant.ofEpochMilli(lastMod)
                    + "]";
        }
    }

    public long getFirstId() {
        return hdr1.firstId;
    }

    /**
     * Creates a new empty file
     * 
     * @param dir
     * @param id
     * @param pageSize
     * @param maxPages
     * @param maxFileSize
     */
    private ReplicationFile(String yamcsInstance, String dir, long id, int pageSize, int maxPages, int maxFileSize) {
        log = new Log(this.getClass(), yamcsInstance);
        file = getFile(dir, id);
        if (file.exists()) {
            throw new IllegalArgumentException("File " + file + " exists. Refusing to overwrite");
        }
        try {
            fc = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.READ);
            buf = fc.map(MapMode.READ_WRITE, 0, maxFileSize);
            hdr1 = new Header1(id, pageSize, maxPages);
            hdr2 = new Header2(true);
            this.readOnly = false;
            this.lastMetadataPos = Header2.HDR_IDX_OFFSET - 4;
            buf.position(hdr2.endOffset());
            log.info("Created new replication file {} pageSize: {}, maxPages:{}", file, hdr1.pageSize, hdr1.maxPages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Open an existing file for append
     */
    private ReplicationFile(String yamcsInstance, String dir, long firstTxId, int maxFileSize) {
        log = new Log(this.getClass(), yamcsInstance);
        this.readOnly = false;
        file = getFile(dir, firstTxId);
        try {
            fc = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
            buf = fc.map(MapMode.READ_WRITE, 0, maxFileSize);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        hdr1 = new Header1(firstTxId);
        hdr2 = new Header2(false);

        log.debug("{}, {}", hdr1, hdr2);
        // position the buffer at the end
        buf.position(getPosition(numTx()));

        // find the offset where the next metadata has to be written
        this.lastMetadataPos = Header2.HDR_IDX_OFFSET - 4;
        int p;
        while ((p = buf.getInt(lastMetadataPos)) > 0) {
            lastMetadataPos = p + 12;// txid+size
        }

        log.info("Opened for append {} pageSize: {}, maxPages:{}, num_tx: {}", file, hdr1.pageSize, hdr1.maxPages,
                numTx());
    }

    /**
     * Open an existing file read only
     */
    private ReplicationFile(String yamcsInstance, String dir, long firstTxId) {
        log = new Log(this.getClass(), yamcsInstance);
        this.readOnly = true;

        file = getFile(dir, firstTxId);
        try {
            fc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
            buf = fc.map(MapMode.READ_ONLY, 0, fc.size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        hdr1 = new Header1(firstTxId);
        hdr2 = new Header2(false);
        log.debug("hdr1: {}, hdr2: {}", hdr1, hdr2);

        this.lastMetadataPos = Header2.HDR_IDX_OFFSET - 4;

        // set the filefull and the buf position as if they were from a file that has filled up
        this.fileFull = true;
        buf.position(getPosition((int) (getNextTxId() - hdr1.firstId)));

        log.info("Opened read-only {} pageSize: {}, maxPages:{}, num_tx: {}", file, hdr1.pageSize, hdr1.maxPages,
                numTx());
    }

    public static ReplicationFile newFile(String yamcsInstance, String dir, long firstTxId, int pageSize, int maxPages,
            int maxFileSize) {
        return new ReplicationFile(yamcsInstance, dir, firstTxId, pageSize, maxPages, maxFileSize);
    }

    public static ReplicationFile openReadOnly(String yamcsInstance, String dir, long firstTxId) {
        return new ReplicationFile(yamcsInstance, dir, firstTxId);
    }

    public static ReplicationFile openReadWrite(String yamcsInstance, String dir, long firstTxId, int maxFileSize) {
        return new ReplicationFile(yamcsInstance, dir, firstTxId, maxFileSize);
    }

    static File getFile(String dir, long firstTxId) {
        return new File(String.format("%s/RPL_%016x.dat", dir, firstTxId));
    }

    public long writeData(Transaction tx) {
        return writeData(tx, false);
    }

    /**
     * Write transaction to the file and returns the transaction id.
     * <p>
     * returns -1 if the transaction could not be written because the file is full.
     * 
     * @param tx
     * @return
     */
    public long writeData(Transaction tx, boolean sync) {
        if (readOnly) {
            throw new IllegalStateException("Read only file");
        } else if (!fc.isOpen()) {
            throw new IllegalStateException("The file is closed");
        }
        rwlock.writeLock().lock();
        try {
            if (fileFull) {
                return -1;
            } else if (hdr2.numFullPages == hdr1.maxPages) {
                fileFull = true;
                doForceWrite();
                return -1; // file full
            } else if (buf.remaining() < 16) {
                fileFull = true;
                doForceWrite();
                return -1;
            }

            long txid = hdr1.firstId + numTx();
            log.trace("Writing transaction {} at position {}", txid, buf.position());
            int pos = buf.position();
            buf.putInt(0);// this is where the transaction header containing the type and size is written below
            buf.putLong(txid);

            byte type = tx.getType();
            if (Transaction.isMetadata(type)) {
                log.trace("Wrote at offset {} the pointer to the next metadata at {}", lastMetadataPos, pos);
                buf.putInt(lastMetadataPos, pos);
                lastMetadataPos = buf.position();
                buf.putInt(0);
            }

            try {
                tx.marshall(buf);
            } catch (BufferOverflowException e) {// end of file
                return -1;
            }

            int totalsize = buf.position() - pos;
            buf.putInt(pos, (type << 24) | (totalsize - 4));

            hdr2.lastMod = System.currentTimeMillis();
            log.trace("Wrote transaction {} of type {} at position {}, total size: {}", txid, type, pos, totalsize);

            hdr2.lastPageNumTx++;
            if (hdr2.lastPageNumTx == hdr1.pageSize) {
                hdr2.numFullPages++;
                hdr2.lastPageNumTx = 0;
                hdr2.writeIndex(hdr2.numFullPages, buf.position());
            }

            syncCount--;
            if (sync || syncCount == 0) {
                doForceWrite();
            }

            return txid;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * Returns a {@link ReplicationTail} containing a read only {@link ByteBuffer} having the position on given txId and
     * with the limit set to the current position of the file.
     * 
     * <p>
     * the tail can be sent back in {@link #getNewData(ReplicationTail)} to obtain more data if available. If
     * {@link ReplicationTail#eof} is true, it
     * means the file is full so no more data will be available in the future.
     *
     * <p>
     * if the txId is smaller than the first transaction of this file, an {@link IllegalArgumentException} is thrown.
     * <p>
     * If the txId is greater than the highest transaction in this file , null is returned
     * <p>
     * The returned tail can have 0 transactions; it can be used later to get more data.
     * 
     * @param txId
     * @return
     */
    public ReplicationTail tail(long txId) {
        int txNum = (int) (txId - hdr1.firstId);
        if (txNum < 0) {
            throw new IllegalArgumentException(txId + " is smaller than " + hdr1.firstId);
        }
        rwlock.readLock().lock();
        try {

            int pos = getPosition(txNum);
            if (pos < 0) {
                return null;
            }

            ByteBuffer buf1 = buf.duplicate().asReadOnlyBuffer();
            buf1.position(pos);
            buf1.limit(buf.position());
            ReplicationTail rfe = new ReplicationTail();
            rfe.buf = buf1;
            rfe.nextTxId = getNextTxId();
            if (fileFull) {
                rfe.eof = true;
            }
            return rfe;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Change the limit inside the file tail to the current position in the file buffer. Update the nextTxId
     * <p>
     * Also update the eof flag if the file filled up since the last call.
     * 
     * @param rfe
     */
    public void getNewData(ReplicationTail rfe) {
        rwlock.readLock().lock();
        try {
            rfe.buf.limit(buf.position());
            if (fileFull) {
                rfe.eof = true;
            }
            rfe.nextTxId = getNextTxId();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Get the position of the txNum th transaction in the file
     * return -1 if the transaction is beyond the end of the file.
     */
    private int getPosition(int txNum) {
        int nfp = txNum / hdr1.pageSize;
        int m1 = txNum - nfp * hdr1.pageSize;

        if ((nfp > hdr2.numFullPages) || (nfp == hdr2.numFullPages && m1 > hdr2.lastPageNumTx)) {
            return -1;
        }

        // first jump to the right page
        int pos = hdr2.getIndex(nfp);

        long expectedTxId = hdr1.firstId + hdr1.pageSize * nfp;
        // then skip m1 transactions
        for (int i = 0; i < m1; i++) {
            pos = skipTransaction(pos, expectedTxId++);
        }
        return pos;
    }

    int skipTransaction(int pos, long expectedTxId) {
        int typeSize = buf.getInt(pos);
        long txId = buf.getLong(pos + 4);
        if (txId != expectedTxId) {// consistency check
            throw new CorruptedFileException(file, "at offset " + pos + " expected txId " + expectedTxId
                    + " but found " + txId + " instead");
        }

        return pos + 4 + (typeSize & 0xFFFFFF);
    }

    public boolean isFull() {
        return fileFull;
    }

    /**
     * Iterate through the metadata
     * 
     * @return
     */
    public Iterator<ByteBuffer> metadataIterator() {
        return new MetadataIterator();
    }

    /**
     * Force writing the content on disk.
     * <p>
     * The method will call first {@link FileChannel#force(boolean)}, write the number of transactions to the header and
     * then call again
     * {@link FileChannel#force(boolean)} to force also the header on the disk.
     * <p>
     * This way should guarantee that the transaction data is written on the disk before the header
     */
    public void forceWrite() {
        if (!readOnly) {
            rwlock.readLock().lock();
            try {
                doForceWrite();
            } finally {
                rwlock.readLock().unlock();
            }
        }
    }

    private void doForceWrite() {
        try {
            fc.force(true);
            hdr2.write();
            fc.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void close() {
        try {
            if (!readOnly) {
                hdr2.write();
                fc.truncate(buf.position());
            }
            fc.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    int numTx() {
        return hdr1.pageSize * hdr2.numFullPages + hdr2.lastPageNumTx;
    }

    /**
     * Returns the last tx id from this file + 1.
     * <p>
     * If there is no transaction in this file, return 0.
     * 
     * @return
     */
    public long getNextTxId() {
        return hdr1.firstId + numTx();
    }

    class MetadataIterator implements Iterator<ByteBuffer> {
        int nextPos;

        MetadataIterator() {
            nextPos = hdr2.firstMetadataPointer();
        }

        @Override
        public boolean hasNext() {
            return nextPos > 0;
        }

        /**
         * Returns a ByteBuffer with the position set to where the header begins and the limit sets to where it
         * ends (before the position of the next metadata record)
         * <p>
         * The header is 1 byte type and 3 bytes length followed by 8 bytes txId
         */
        @Override
        public ByteBuffer next() {
            ByteBuffer buf1 = buf.duplicate();
            buf1.position(nextPos);
            buf1 = buf1.slice();
            int typesize = buf1.getInt(0);
            nextPos = buf1.getInt(12);
            int limit = 4 + (typesize & 0xFFFFFF);
            buf1.limit(limit);
            return buf1.asReadOnlyBuffer();
        }
    }
}
