package org.yamcs.parameterarchive;

import java.util.List;

import org.yamcs.utils.IntArray;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

/**
 * Storage of the parameter archive of one Yamcs instance, provided by a {@link org.yamcs.yarch.StorageEngine}.
 * <p>
 * The storage knows nothing about the content of the segments: they are encoded and decoded by the
 * {@link ParameterArchive}. The storage keeps:
 * <ul>
 * <li>the metadata records of the parameter archive (parameter ids, aggregate/array definitions)</li>
 * <li>the parameter groups</li>
 * <li>the segments, stored under keys encoded with {@link SegmentKey}; the segments of one interval written with
 * {@link ParchiveWriteBatch#merge(byte[], byte[])} are merged by the storage with the Yamcs parameter archive merge
 * operator</li>
 * <li>the partitioning of the data by time, if any</li>
 * </ul>
 * All the methods throw {@link ParameterArchiveException} in case of error.
 */
public interface ParameterArchiveStorage {

    /**
     * Returns the metadata records of the given type (PARCHIVE_DATA or PARCHIVE_AGGARR_INFO) of the instance.
     */
    List<TablespaceRecord> listRecords(TablespaceRecord.Type type);

    /**
     * Creates a new metadata record, allocating a new tbsIndex for it.
     *
     * @return the record with the tbsIndex set
     */
    TablespaceRecord createRecord(TablespaceRecord.Builder trb);

    /**
     * Updates an existing metadata record (identified by its type and tbsIndex).
     */
    TablespaceRecord updateRecord(TablespaceRecord.Builder trb);

    /**
     * Passes all the stored parameter groups to the consumer, in increasing order of the group id.
     */
    void loadParameterGroups(ParameterGroupConsumer consumer);

    /**
     * Stores the parameter ids (sorted) of a parameter group.
     */
    void saveParameterGroup(int pgid, IntArray pids);

    /**
     * Returns the version (see {@link ParameterArchive#VERSION}) of the data stored for the interval. If nothing is
     * stored yet for the interval, returns the version that would be used for writing.
     * <p>
     * Version 0 keys are encoded with {@link SegmentKey#encodeV0()} and contain the segment start instead of the
     * interval start; the merge operator cannot be used.
     */
    int getVersion(long intervalStart);

    /**
     * Creates a write batch for one interval. The operations are applied atomically when the batch is committed.
     */
    ParchiveWriteBatch newWriteBatch(long intervalStart);

    /**
     * Scans the segments, see {@link ScanRequest}.
     * <p>
     * The scanner has to be closed if it is not used until the end.
     */
    SegmentScanner scan(ScanRequest request);

    /**
     * Removes all the data and metadata of the parameter archive of the instance. The storage remains usable, as for
     * a new database.
     */
    void purge();

    /**
     * Compacts the stored data (if applicable)
     */
    void compact();

    @FunctionalInterface
    interface ParameterGroupConsumer {
        void accept(int pgid, IntArray pids);
    }

    /**
     * Write operations for one interval, see {@link ParameterArchiveStorage#newWriteBatch(long)}
     */
    interface ParchiveWriteBatch extends AutoCloseable {
        /**
         * the version of the data of the interval, as returned by {@link ParameterArchiveStorage#getVersion(long)}
         */
        int version();

        void put(byte[] key, byte[] value);

        /**
         * Merges the value with the existing one using the parameter archive merge operator. Only for version 1
         * and later.
         */
        void merge(byte[] key, byte[] value);

        void delete(byte[] key);

        /**
         * Same as {@link #delete(byte[])} but the delete is only performed if the key exists at the moment of the
         * call (to avoid unnecessary tombstones).
         */
        void deleteIfExists(byte[] key);

        /**
         * Deletes the keys in the range [start, end). Only used for version 0.
         */
        void deleteRange(byte[] start, byte[] end);

        /**
         * Applies the operations atomically
         */
        void commit();

        @Override
        void close();
    }

    /**
     * A request to scan the segments of a list of (parameter id, parameter group id) pairs, across all the partitions.
     * <p>
     * For each pair, the records with the segment start in the range [intervalStart(start), stop] are returned, in
     * order of the segment start (ascending or descending). The records with the same segment start are returned in
     * the order of the pairs in the request.
     * <p>
     * For version 1 the segment start is the interval start, for version 0 it is the start of the segment.
     * <p>
     * Each record contains also the time segment of the parameter group with the same segment start. The time
     * parameter id can be one of the pids; its records contain the time segment (as timeSegment, the other segments
     * are null).
     *
     * @param timePid
     *            the parameter id used for the time segments
     * @param pids
     *            parameter ids
     * @param pgids
     *            parameter group ids, same length as pids
     * @param start
     *            start of the range
     * @param stop
     *            end of the range (inclusive)
     * @param ascending
     *            the order
     * @param retrieveEngValues
     *            if true, the engineering values segments are returned
     * @param retrieveRawValues
     *            if true, the raw values segments are returned
     * @param retrieveParameterStatus
     *            if true, the parameter status segments are returned
     */
    record ScanRequest(int timePid, int[] pids, int[] pgids, long start, long stop, boolean ascending,
            boolean retrieveEngValues, boolean retrieveRawValues, boolean retrieveParameterStatus) {
        public ScanRequest {
            if (pids.length != pgids.length) {
                throw new IllegalArgumentException("pids and pgids must have the same length");
            }
        }

        /**
         * the number of pairs
         */
        public int size() {
            return pids.length;
        }
    }

    /**
     * The stored segments of one (parameter id, parameter group id, segment start).
     * <p>
     * Any of the segments can be null, if not stored or not requested. The gaps are always returned if they exist.
     *
     * @param pairIndex
     *            the index of the (pid, pgid) pair in the request
     * @param timeSegment
     *            the time segment of the parameter group for the same segment start (null if it does not exist)
     */
    record SegmentRecord(int pairIndex, int pid, int pgid, long segmentStart, byte[] timeSegment,
            byte[] engValueSegment, byte[] rawValueSegment, byte[] parameterStatusSegment, byte[] gaps) {
    }

    /**
     * Iterates over the result of a scan, like a RocksDB iterator (isValid/value/next).
     */
    interface SegmentScanner extends AutoCloseable {
        boolean isValid();

        SegmentRecord value();

        void next();

        @Override
        void close();
    }
}
