package org.yamcs.yarch;

import java.util.List;

/**
 * Interface for encoding and decoding histogram segments in different formats.
 * 
 * There are two encodings:
 * <p>
 * V1 encoding uses 16-bit numbers for record counts and has timestamp sorting issues where negative timestamps are
 * sorted after positive ones.
 * <p>
 * V2 encoding uses 32-bit numbers for record counts and fixes the timestamp sorting issues by using proper unsigned
 * sorting of segment indices.
 */
public interface HistogramEncoder {
    
    /**
     * Split timestamp into segment index and delta time.
     * Each encoder uses its own segmentation logic.
     * 
     * @param timestamp the timestamp to split
     * @return result containing segment index and delta time within the segment
     */
    SplitTimeResult splitTime(long timestamp);
    
    /**
     * Get the segment start timestamp for a given timestamp.
     * Used for partition selection to ensure histogram segments are stored
     * in the correct time-based partitions.
     * 
     * @param timestamp any timestamp within the segment
     * @return the start timestamp of the segment containing the given timestamp
     */
    long segmentStart(long timestamp);

    /**
     * Get segment index for a timestamp.
     * The segment index is a monotonically increasing number starting from 0
     * for the oldest possible segment.
     * 
     * @param timestamp the timestamp to get segment index for
     * @return the segment index
     */
    long segIndex(long timestamp);
    
    /**
     * Reconstruct full timestamp from segment index and delta time.
     * This is the inverse operation of splitTime.
     * 
     * @param segIndex the segment index
     * @param deltaTime the delta time within the segment
     * @return the reconstructed timestamp
     */
    long joinTime(long segIndex, int deltaTime);
    
    /**
     * Encode the database key for a histogram segment.
     * 
     * @param tbsIndex the tablespace index
     * @param segIndex the segment index
     * @param columnValue the column value being histogrammed
     * @return the encoded database key
     */
    byte[] encodeKey(int tbsIndex, long segIndex, byte[] columnValue);
    
    /**
     * Encode the database value for a histogram segment.
     * 
     * @param segment the histogram segment to encode
     * @return the encoded database value
     */
    byte[] encodeValue(HistogramSegment segment);
    
    /**
     * Decode histogram records from database value.
     * 
     * @param data the encoded database value
     * @param segIndex the segment index (may be needed for V1 delta time adjustment)
     * @return list of decoded histogram records
     */
    List<HistogramSegment.SegRecord> decodeValue(byte[] data, long segIndex);
    
    /**
     * Decode a complete histogram segment from database key and value.
     * 
     * @param key the database key
     * @param value the database value
     * @return the decoded histogram segment
     */
    HistogramSegment decodeSegment(byte[] key, byte[] value);
    
    /**
     * Result of splitting a timestamp into segment index and delta time.
     */
    public static record SplitTimeResult(long segIndex, int deltaTime) {}
    
    static HistogramEncoder createEncoder(TableDefinition tableDefinition) {
        if (tableDefinition.getHistogramVersion() == 1) {
            return new HistogramEncoderV1();
        } else {
            return new HistogramEncoderV2();
        }
    }
}