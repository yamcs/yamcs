package org.yamcs.replication;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32;

import org.yamcs.logging.Log;
import org.yamcs.utils.StringConverter;

import io.netty.util.internal.PlatformDependent;

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
 *  4 bytes page_size - number of transactions per page 
 *  4 bytes max_pages - max number of pages (and the size of the index)
 * 
 *  8 bytes last_mod = last modification time
 *  4 bytes n =  number of full pages. If n=max_pages, the file is full, cannot be written to it
 *  4 bytes m = number of transactions on page n
 *  4 bytes firstMetadataPos - position of the first metadata transaction
 *  (max_pages+1) x 4 bytes idx - transaction index 
 *      idx[i] (i=0..max_pages) - offset in the file where transaction with id id_first + i*page_size starts
 *      idx[i] = 0 -> no such transaction. this means num_tx < i*m
 *      idx[max_pages] -> pointer to the end of the file.
 * </pre>
 * 
 * transaction data:
 * 
 * <pre>
 * 
 * 1 byte type - the type can be DATA or STREAM_INFO with the constants defined in {@link Message}
 * 3 bytes - size of the data that follows including the CRC (or alternatively including the first 4 bytes type+size but excluding the crc)
 * 4 bytes instance_id
 * 8 bytes transaction_id
 * n bytes data
 * 4 bytes CRC32 calculated over the data including the type and length
 * 
 *  for metadata the first 4 bytes of the data is the position of the next metadata record
 * 
 * </pre>
 * 
 * <p>
 * The methods of this class throw {@link UncheckedIOException} instead of {@link IOException}. When working with memory
 * mapped files in java, an IO error will cause an unspecified unchecked exception or even crash of Java (because file
 * data is accessed using memory reads/writes). Therefore we prefer not to give a false sense of security by throwing
 * IOException only in some limited situations and converted all these to {@link UncheckedIOException}..
 * 
 * <p>
 * The one occasion when Java may crash while no hardware failure is present is when the disk is full.
 * <p>
 * TODO: add a checker and stop writing data if the disk usage is above a threshold.
 * 
 */
public class ReplicationFile implements Closeable {
    static final String RPL_FILENAME_PREFIX = "RPL";
    final static byte[] MAGIC = { 'Y', 'A', 'M', 'C', 'S', '_', 'S', 'T', 'R', 'E', 'A', 'M' };

    // the position inside the record where the metadata position pointer sits
    // it is after size, instanceId, txid
    final static int METADATA_POS_OFFSET = 16;
    final static int MIN_RECORD_SIZE = 20; // size, instanceId, txId, crc

    final Log log;

    ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private MappedByteBuffer buf;
    private int lastMetadataTxStart;
    private FileChannel fc;
    final private boolean readOnly;
    final private Header1 hdr1;
    final private Header2 hdr2;
    private boolean fileFull = false;
    final Path path;
    CRC32 crc32 = new CRC32();
    private boolean syncRequired;

    class Header1 { // this is the first part - fixed - of the header

        final static byte VERSION = 0;
        final static int LENGTH = 32;
        final long firstId; // first transaction id
        final int pageSize, maxPages;

        // new file
        Header1(long firstId, int pageSize, int maxPages) {
            this.firstId = firstId;
            this.pageSize = pageSize;
            this.maxPages = maxPages;
            buf.put(MAGIC);
            buf.putInt(VERSION << 24);
            buf.putLong(firstId);
            buf.putInt(pageSize);
            buf.putInt(maxPages);
        }

        // open existing file
        Header1(long firstTxId) {
            checkHdr1(firstTxId);
            firstId = firstTxId;
            pageSize = buf.getInt();
            maxPages = buf.getInt();
        }

        private void checkHdr1(long firstTxId) {
            byte[] magic = new byte[MAGIC.length];
            buf.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new CorruptedFileException(path,
                        "bad file, magic entry does not match: " + StringConverter.arrayToHexString(magic)
                                + ". Expected " + StringConverter.arrayToHexString(MAGIC));
            }
            int version = buf.getInt() >> 24;
            if (version != VERSION) {
                throw new CorruptedFileException(path, "bad version: " + version + ". Expected " + VERSION);
            }
            long id = buf.getLong();
            if (id != firstTxId) {
                throw new CorruptedFileException(path, "bad firstId " + id + " expected " + firstTxId);
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

        Header2(boolean newFile) {
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

        /**
         * returns the offset of the end of hdr2 - where data begins
         */
        public int endOffset() {
            return HDR_IDX_OFFSET + 4 * (hdr1.maxPages + 1);
        }

        public int getIndex(int n) {
            return buf.getInt(HDR_IDX_OFFSET + n * 4);
        }

        void writeIndex(int n, int txPos) {
            buf.putInt(HDR_IDX_OFFSET + n * 4, txPos);
        }

        void incrNumTx() {
            hdr2.lastPageNumTx++;
            if (hdr2.lastPageNumTx == hdr1.pageSize) {
                hdr2.numFullPages++;
                hdr2.lastPageNumTx = 0;
                hdr2.writeIndex(hdr2.numFullPages, buf.position());
            }
        }

        int numTx() {
            return hdr1.pageSize * hdr2.numFullPages + hdr2.lastPageNumTx;
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
    private ReplicationFile(String yamcsInstance, Path path, long id, int pageSize, int maxPages, int maxFileSize) {
        log = new Log(this.getClass(), yamcsInstance);
        this.path = path;
        if (Files.exists(path)) {
            throw new IllegalArgumentException("File " + path + " exists. Refusing to overwrite");
        }
        try {
            fc = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.READ);
            buf = fc.map(MapMode.READ_WRITE, 0, maxFileSize);
            hdr1 = new Header1(id, pageSize, maxPages);
            hdr2 = new Header2(true);
            this.readOnly = false;
            this.lastMetadataTxStart = Header2.HDR_IDX_OFFSET - METADATA_POS_OFFSET - 4;
            buf.position(hdr2.endOffset());
            log.info("Created new replication file {} pageSize: {}, maxPages:{}, maxFileSize: {}", path, hdr1.pageSize,
                    hdr1.maxPages, maxFileSize);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Open an existing file for append
     */
    private ReplicationFile(String yamcsInstance, Path path, long firstTxId, int maxFileSize) {
        log = new Log(this.getClass(), yamcsInstance);
        this.path = path;
        this.readOnly = false;
        try {
            fc = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
            buf = fc.map(MapMode.READ_WRITE, 0, maxFileSize);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        hdr1 = new Header1(firstTxId);
        hdr2 = new Header2(false);

        log.debug("{}, {}", hdr1, hdr2);
        // recover any non indexed good transactions at the end of the file and set the position at the end
        recover();

        long endOffset = buf.position();
        // find the offset where the next metadata has to be written
        lastMetadataTxStart = Header2.HDR_IDX_OFFSET - METADATA_POS_OFFSET - 4;
        while (true) {
            int nextMetadataTxStart = buf.getInt(lastMetadataTxStart + METADATA_POS_OFFSET);
            if (nextMetadataTxStart == 0 || nextMetadataTxStart + METADATA_POS_OFFSET > endOffset) {
                break;
            }
            if (nextMetadataTxStart <= lastMetadataTxStart) {
                throw new UncheckedIOException(
                        new IOException("Corrupted file " + path + " at position " + lastMetadataTxStart
                                + " the metadata pointer points in the past"));
            }
            lastMetadataTxStart = nextMetadataTxStart;
        }
        log.info("Opened for append {} pageSize: {}, maxPages:{}, num_tx: {}", path, hdr1.pageSize, hdr1.maxPages,
                hdr2.numTx());
    }

    /**
     * Open an existing file read only
     */
    private ReplicationFile(String yamcsInstance, Path path, long firstTxId) {
        log = new Log(this.getClass(), yamcsInstance);
        this.readOnly = true;
        this.path = path;

        try {
            fc = FileChannel.open(path, StandardOpenOption.READ);
            buf = fc.map(MapMode.READ_ONLY, 0, fc.size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        hdr1 = new Header1(firstTxId);
        hdr2 = new Header2(false);
        log.debug("hdr1: {}, hdr2: {}", hdr1, hdr2);

        this.lastMetadataTxStart = Header2.HDR_IDX_OFFSET - 4;

        // set the filefull
        this.fileFull = true;
        recover();

        log.info("Opened read-only {} pageSize: {}, maxPages:{}, num_tx: {}", path, hdr1.pageSize, hdr1.maxPages,
                hdr2.numTx());
    }

    public static ReplicationFile newFile(String yamcsInstance, Path path, long firstTxId, int pageSize, int maxPages,
            int maxFileSize) {
        checkSize(pageSize, maxPages, maxFileSize);
        return new ReplicationFile(yamcsInstance, path, firstTxId, pageSize, maxPages, maxFileSize);
    }

    private static void checkSize(int pageSize, int maxPages, int maxFileSize) {
        int minSize = headerSize(pageSize, maxPages) + MIN_RECORD_SIZE;
        if (maxFileSize < minSize) {
            throw new IllegalArgumentException(
                    "maxFileSize=" + maxFileSize + " too small; " + minSize
                            + " bytes required for storing an empty transaction");
        }

    }

    public static ReplicationFile openReadOnly(String yamcsInstance, Path path, long firstTxId) {
        return new ReplicationFile(yamcsInstance, path, firstTxId);
    }

    public static ReplicationFile openReadWrite(String yamcsInstance, Path path, long firstTxId, int maxFileSize) {
        return new ReplicationFile(yamcsInstance, path, firstTxId, maxFileSize);
    }

    /**
     * Write transaction to the file and returns the transaction id.
     * <p>
     * returns -1 if the transaction could not be written because the file is full.
     * 
     * @param tx
     * @return
     */
    public long writeData(Transaction tx) {
        if (readOnly) {
            throw new IllegalStateException("Read only file");
        } else if (!fc.isOpen()) {
            // this may happen if the thread writing to the replication file is interrupted when writing
            log.warn("Attempting to write to a closed file");
            return -1;
        }

        rwlock.writeLock().lock();
        final int txStartPos = buf.position();

        try {
            if (fileFull) {
                return -1;
            } else if (hdr2.numFullPages == hdr1.maxPages) {
                return abortWriteFileFull(txStartPos);
            } else if (buf.remaining() < MIN_RECORD_SIZE) {
                return abortWriteFileFull(txStartPos);
            }

            long txid = hdr1.firstId + hdr2.numTx();
            log.trace("Writing transaction {} at position {}", txid, buf.position());

            buf.putInt(0);// this is where the the type and size is written below
            buf.putInt(tx.getInstanceId());
            buf.putLong(txid);

            byte type = tx.getType();

            if (Transaction.isMetadata(type)) {
                buf.putInt(0);// next metadata position
            }

            try {
                tx.marshall(buf);
            } catch (BufferOverflowException | IndexOutOfBoundsException e) {// end of file
                return abortWriteFileFull(txStartPos);
            }

            if (buf.remaining() < 4) {// no space left for CRC
                return abortWriteFileFull(txStartPos);
            }

            int size = buf.position() - txStartPos;
            buf.putInt(txStartPos, (type << 24) | (size));
            int crc = compute_crc(buf, txStartPos);
            buf.putInt(crc);

            if (Transaction.isMetadata(type)) {
                buf.putInt(lastMetadataTxStart + METADATA_POS_OFFSET, txStartPos);

                // update crc of the modified metadata record
                if (lastMetadataTxStart >= hdr2.endOffset()) {
                    updateCrc(lastMetadataTxStart);
                }
                if (log.isTraceEnabled()) {
                    log.trace("Wrote at offset {} the pointer to the next metadata at {}",
                            lastMetadataTxStart + METADATA_POS_OFFSET, txStartPos);
                }

                lastMetadataTxStart = txStartPos;
            }

            hdr2.lastMod = System.currentTimeMillis();
            log.trace("Wrote transaction {} of type {} at position {}, total size: {}", txid, type, txStartPos,
                    size + 4);
            hdr2.incrNumTx();
            return txid;
        } catch (Throwable e) {
            buf.position(txStartPos);
            log.error("Caught exception when writing the replication file ", e);
            throw e;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    // starts from latest known transaction (according to the header) and checks for new ones
    private void recover() {
        int n = hdr2.numTx();

        int startTxPos = getPosition(n);
        buf.position(startTxPos);
        int k = 0;
        while (buf.remaining() > MIN_RECORD_SIZE) {
            n = hdr2.numTx();
            startTxPos = buf.position();

            int size = buf.getInt() & 0xFFFFFF;

            if (size > buf.remaining() || size < 12) {
                break;
            }

            buf.getInt();// serverId
            long txId = buf.getLong();
            if (txId != hdr1.firstId + n) {
                break;
            }
            buf.position(startTxPos + size);
            int crc = compute_crc(buf, startTxPos);
            if (crc != buf.getInt()) {
                log.debug("Trying to recover TX{}: CRC does not match", txId);
                break;
            }
            log.debug("Recovered TX{}", txId);
            hdr2.incrNumTx();
            k++;
        }
        log.debug("Found {} transactions more than indicated in the header", k);
        buf.position(startTxPos);
    }

    private int abortWriteFileFull(int txStartPos) {
        fileFull = true;
        buf.position(txStartPos);
        log.debug("File {} full, numTx: {}", path, hdr2.numTx());
        return -1;
    }

    // update the CRC of the record starting at the position
    private void updateCrc(int pos) {
        ByteBuffer buf1 = buf.duplicate();
        buf1.position(pos);
        int size = buf1.getInt() & 0xFFFFFF;
        buf1.position(pos);
        buf1.limit(pos + size);
        crc32.reset();
        crc32.update(buf1);
        buf1.limit(buf1.limit() + 4);
        buf1.putInt((int) crc32.getValue());
    }

    // compute checksum from start to the current position
    private int compute_crc(ByteBuffer buf, int start) {
        int prevLimit = buf.limit();
        buf.limit(buf.position());
        buf.position(start);
        crc32.reset();
        crc32.update(buf);
        buf.limit(prevLimit);

        return (int) crc32.getValue();
    }

    /**
     * Returns a {@link ReplicationTail} containing a read only {@link ByteBuffer} having the position on given txId and
     * with the limit set to the current end of tx data.
     * 
     * <p>
     * The tail can be sent back in {@link #getNewData(ReplicationTail)} to obtain more data if available.
     * <p>
     * {@link ReplicationTail#eof} = true means the file is full so no more data will be available in the future.
     *
     * <p>
     * if the txId is smaller than the first transaction of this file, an {@link IllegalArgumentException} is thrown.
     * <p>
     * If the txId is greater than the highest transaction in this file plus 1, null is returned
     * <p>
     * If the txId is the highest transaction in this file plus one, a tail with 0 transactions (i.e. position=limit in
     * the buffer) is returned; it can be used later to get more data.
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
     * Get the position of the txNum th transaction in the file according to the index
     * <p>
     * return -1 if the transaction is beyond the end of the file.
     */
    private int getPosition(int txNum) {
        // number of full pages
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

    private int skipTransaction(int pos, long expectedTxId) {
        int typeSize = buf.getInt(pos);
        long txId = buf.getLong(pos + 8);
        if (txId != expectedTxId) {// consistency check
            throw new CorruptedFileException(path, "at offset " + pos + " expected txId " + expectedTxId
                    + " but found " + txId + " instead");
        }
        return pos + 4 + (typeSize & 0xFFFFFF);
    }

    public boolean isFull() {
        return fileFull;
    }

    /**
     * Iterate through the metadata
     */
    public Iterator<ByteBuffer> metadataIterator() {
        return new MetadataIterator();
    }

    /**
     * Iterate through the data
     */
    public Iterator<ByteBuffer> iterator() {
        return new TxIterator();
    }

    public void close() {
        try {
            if (!readOnly) {
                hdr2.write();

                // Required to unmap on Windows, else truncate fails
                PlatformDependent.freeDirectBuffer(buf);

                fc.truncate(buf.position());
            } else {
                // Required on Windows
                PlatformDependent.freeDirectBuffer(buf);
            }
            fc.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Force writing the content on disk.
     * <p>
     * The method will call first {@link FileChannel#force(boolean)}, write the number of transactions to the header and
     * then call again {@link FileChannel#force(boolean)} to force also the header on the disk.
     * <p>
     * This way should guarantee that the transaction data is written on the disk before the header
     */
    public void sync() throws IOException {
        if (!readOnly) {
            rwlock.readLock().lock();
            try {
                fc.force(true);
                hdr2.write();
                fc.force(true);
            } finally {
                rwlock.readLock().unlock();
            }
        }
    }

    public static int headerSize(int pageSize, int maxPages) {
        return Header2.HDR_IDX_OFFSET + 4 * (maxPages + 1);
    }

    public int numTx() {
        return hdr2.numTx();
    }

    public boolean isSyncRequired() {
        return syncRequired;
    }

    /**
     * Set the sync required flag such that the file is synchronized by the ReplicationMaster
     * 
     * @param syncRequired
     */
    public void setSyncRequired(boolean syncRequired) {
        this.syncRequired = syncRequired;
    }

    /**
     * Returns the last tx id from this file + 1.
     * <p>
     * If there is no transaction in this file, return 0.
     */
    public long getNextTxId() {
        return hdr1.firstId + hdr2.numTx();
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
         * Returns a ByteBuffer with the position set to where the record begins and the limit sets to where it ends
         * <p>
         * The structure of the metadata is
         * 
         * <pre>
         *  1 byte type
         *  3 bytes size -size of data that follows( i.e. without the size itself) = n + 20
         *  4 bytes serverId
         *  8 bytes txId
         *  4 bytes metadata next position (to be ignored, only relevant inside the file)
         *   n bytes data
         *  4 bytes crc
         * </pre>
         */
        @Override
        public ByteBuffer next() {
            ByteBuffer buf1 = buf.asReadOnlyBuffer();
            buf1.position(nextPos);
            int typesize = buf1.getInt(nextPos);
            int limit = nextPos + 4 + (typesize & 0xFFFFFF);
            buf1.limit(limit);
            nextPos = buf1.getInt(nextPos + METADATA_POS_OFFSET);

            return buf1;
        }
    }

    class TxIterator implements Iterator<ByteBuffer> {
        int nextPos;

        TxIterator() {
            nextPos = hdr2.endOffset();
        }

        @Override
        public boolean hasNext() {
            return nextPos > 0;
        }

        /**
         * Returns a ByteBuffer with the position set to where the record begins and the limit sets to where it ends
         * <p>
         * The structure of the metadata is
         * 
         * <pre>
         *  1 byte type
         *  3 bytes size -size of data that follows( i.e. without the size itself) = n + 20
         *  4 bytes serverId
         *  8 bytes txId
         *  4 bytes metadata next position (to be ignored, only relevant inside the file)
         *   n bytes data
         *  4 bytes crc
         * </pre>
         */
        @Override
        public ByteBuffer next() {
            if (nextPos < 0) {
                throw new NoSuchElementException();
            }
            ByteBuffer buf1 = buf.asReadOnlyBuffer();
            buf1.position(nextPos);
            int typesize = buf1.getInt(nextPos);

            nextPos += 4 + (typesize & 0xFFFFFF);

            if (nextPos >= buf.limit()) {
                nextPos = -1;
            } else {
                buf1.limit(nextPos);
            }

            return buf1;
        }
    }

}
