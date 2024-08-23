package org.yamcs.parameterarchive;

import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;

/**
 * Interface for segments of columnar data.
 * <p>
 * Given that all data from one segment is of the same type, the implementors can make use of arrays to store data more
 * efficiently.
 * <p>
 * As of Yamcs 5.9.4 Yamcs supports sparse data in parameter archive. This interface and its implementors do not deal
 * with gaps, they store only the data. The mapping from the original data with gaps to this is done in the
 * {@link ParameterValueSegment}
 */
public interface ValueSegment {

    /**
     * returns Value at position index
     * 
     * @param index
     * @return the value at the index
     */
    public abstract Value getValue(int index);

    /**
     * Insert data at position pos. The data at the subsequent positions is shifted to the right.
     */
    public abstract void insert(int pos, Value engValue);

    /**
     * Add data at the end of the segment.
     */
    public abstract void add(Value engValue);

    /**
     * Optimise the segment data for writing to the archive
     * <p>
     * After this method is called, no more data can be added to the segment
     */
    public abstract void consolidate();

    public abstract int size();

    /**
     * In rare circumstances, a segment read from the archive has to be modified.
     * <p>
     * This method updates the object such that it can be modified
     */
    public default void makeWritable() {

    }
    /**
     * returns an array containing the values in the range [posStart, posStop) if ascending or [posStop, posStart) if
     * descending
     *
     * @param posStart
     * @param posStop
     * @param ascending
     * @return an array containing the values in the specified range
     */
    public abstract ValueArray getRange(int posStart, int posStop, boolean ascending);
}
