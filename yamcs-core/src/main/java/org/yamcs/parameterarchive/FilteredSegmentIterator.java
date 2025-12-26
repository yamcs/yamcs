package org.yamcs.parameterarchive;

import java.util.NoSuchElementException;

import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameter.Value;
import org.yamcs.utils.SortedIntArray;

/**
 * A SegmentIterator wrapper that filters parameter values based on another parameter's value.
 * <p>
 * This iterator uses a MultiSegmentIterator internally to fetch both the main parameter and the filter parameter
 * simultaneously from the same parameter group. It then filters out points where the filter parameter doesn't match the
 * specified value.
 * <p>
 * Example: Query Temperature filtered by vcid=1 - Fetches both Temperature and vcid using MultiSegmentIterator -
 * Returns only Temperature points where corresponding vcid value equals "1"
 * <p>
 * The filtering is done point-by-point at the segment level, matching by timestamp index. Both parameters must be in
 * the same parameter group for efficient fetching.
 */
public class FilteredSegmentIterator implements ParchiveIterator<ParameterValueSegment> {

    private final MultiSegmentIterator multiIterator;
    private final String filterValue;
    private final int mainParameterIndex = 0;
    private final int filterParameterIndex = 1;

    private ParameterValueSegment currentFilteredSegment;

    /**
     * Create a FilteredSegmentIterator that filters the main parameter by the filter parameter value.
     *
     * @param parchive
     *            the parameter archive
     * @param mainPid
     *            the ID of the parameter to retrieve (e.g., Temperature)
     * @param filterPid
     *            the ID of the parameter to filter by (e.g., vcid)
     * @param parameterGroupId
     *            the parameter group ID (both parameters must be in this group)
     * @param filterValue
     *            the value to match (as a string, e.g., "1")
     * @param req
     *            the retrieval options (time range, ascending/descending, etc.)
     */
    public FilteredSegmentIterator(
            ParameterArchive parchive,
            ParameterId mainPid,
            ParameterId filterPid,
            int parameterGroupId,
            String filterValue,
            ParameterRetrievalOptions req) {

        this.filterValue = filterValue;

        // Create array with both parameters: [main, filter]
        ParameterId[] pids = new ParameterId[] { mainPid, filterPid };

        // Use MultiSegmentIterator to fetch both parameters efficiently
        this.multiIterator = new MultiSegmentIterator(parchive, pids, parameterGroupId, req);

        // Advance to first valid segment
        next();
    }

    @Override
    public boolean isValid() {
        return currentFilteredSegment != null;
    }

    @Override
    public ParameterValueSegment value() {
        if (currentFilteredSegment == null) {
            throw new NoSuchElementException();
        }
        return currentFilteredSegment;
    }

    @Override
    public void next() {
        currentFilteredSegment = null;

        while (multiIterator.isValid()) {
            MultiParameterValueSegment multiSegment = multiIterator.value();
            multiIterator.next();

            // Extract the two parameter segments
            ParameterValueSegment mainSegment = multiSegment.getPvs(mainParameterIndex);
            ParameterValueSegment filterSegment = multiSegment.getPvs(filterParameterIndex);

            // Skip if either segment is null (sparse data case)
            if (mainSegment == null || filterSegment == null) {
                continue;
            }

            // Filter the main segment based on filter parameter values
            ParameterValueSegment filtered = filterSegment(mainSegment, filterSegment);

            // Only return non-empty segments
            if (filtered != null && filtered.size() > 0) {
                currentFilteredSegment = filtered;
                return;
            }
        }

        // No more valid segments
        currentFilteredSegment = null;
    }

    /**
     * Filter a parameter segment based on filter parameter values.
     *
     * @param mainSegment
     *            the segment containing values to filter (e.g., Temperature)
     * @param filterSegment
     *            the segment containing filter values (e.g., vcid)
     * @return a new ParameterValueSegment containing only matching points, or null if no points match
     */
    private ParameterValueSegment filterSegment(ParameterValueSegment mainSegment,
            ParameterValueSegment filterSegment) {

        // Both segments share the same time segment
        SortedTimeSegment originalTimeSegment = mainSegment.timeSegment;
        int size = originalTimeSegment.size();

        if (size == 0) {
            return null;
        }

        // Build lists of matching indices
        SortedIntArray matchingIndices = new SortedIntArray();

        // Handle gaps in the data
        SortedIntArray mainGaps = mainSegment.gaps;
        SortedIntArray filterGaps = filterSegment.gaps;

        for (int i = 0; i < size; i++) {
            // Skip if either parameter has a gap at this position
            if (isGap(mainGaps, i) || isGap(filterGaps, i)) {
                continue;
            }

            // Get the filter parameter value at this position
            Value filterVal = filterSegment.engValueSegment.getValue(getDataIndex(i, filterGaps));

            // Compare as string (support for numeric and string parameters)
            String filterValStr = filterVal.toString();

            if (filterValue.equals(filterValStr)) {
                matchingIndices.insert(i);
            }
        }

        // If no matches, return null
        if (matchingIndices.size() == 0) {
            return null;
        }

        // Build filtered segments
        long interval = originalTimeSegment.getInterval();
        SortedTimeSegment newTimeSegment = new SortedTimeSegment(interval);

        // Create new value segments (same type as original)
        ValueSegment newEngValueSegment = createEmptyValueSegment(mainSegment.engValueSegment);
        ValueSegment newRawValueSegment = mainSegment.rawValueSegment != null
                ? createEmptyValueSegment(mainSegment.rawValueSegment)
                : null;
        ParameterStatusSegment newStatusSegment = mainSegment.parameterStatusSegment != null
                ? new ParameterStatusSegment(false)
                : null;

        // Copy matching points to new segments
        for (int i = 0; i < matchingIndices.size(); i++) {
            int origIdx = matchingIndices.get(i);

            // Add timestamp
            long timestamp = originalTimeSegment.getTime(origIdx);
            newTimeSegment.add(timestamp);

            // Add engineering value
            int dataIdx = getDataIndex(origIdx, mainGaps);
            Value engValue = mainSegment.engValueSegment.getValue(dataIdx);
            newEngValueSegment.add(engValue);

            // Add raw value if present
            if (newRawValueSegment != null && mainSegment.rawValueSegment != null) {
                Value rawValue = mainSegment.rawValueSegment.getValue(dataIdx);
                newRawValueSegment.add(rawValue);
            }

            // Add parameter status if present
            if (newStatusSegment != null && mainSegment.parameterStatusSegment != null) {
                var status = mainSegment.parameterStatusSegment.get(dataIdx);
                newStatusSegment.add(status);
            }
        }

        // Consolidate segments for efficiency
        if (newEngValueSegment != null) {
            newEngValueSegment.consolidate();
        }
        if (newRawValueSegment != null) {
            newRawValueSegment.consolidate();
        }

        // Create filtered segment (no gaps since we're building from scratch)
        return new ParameterValueSegment(
                mainSegment.pid,
                newTimeSegment,
                newEngValueSegment,
                newRawValueSegment,
                newStatusSegment,
                null // no gaps in filtered data
        );
    }

    /**
     * Check if a given position is a gap in the data.
     */
    private boolean isGap(SortedIntArray gaps, int position) {
        if (gaps == null || gaps.size() == 0) {
            return false;
        }
        return gaps.search(position) >= 0;
    }

    /**
     * Convert a timestamp index to a data index, accounting for gaps. For segments without gaps, these are the same.
     * For segments with gaps, we need to subtract the number of gaps before this position.
     */
    private int getDataIndex(int timestampIndex, SortedIntArray gaps) {
        if (gaps == null || gaps.size() == 0) {
            return timestampIndex;
        }

        // Count how many gaps are before this index
        int gapCount = 0;
        for (int i = 0; i < gaps.size(); i++) {
            if (gaps.get(i) < timestampIndex) {
                gapCount++;
            } else {
                break;
            }
        }

        return timestampIndex - gapCount;
    }

    /**
     * Create an empty value segment of the same type as the given segment. This uses instanceof checks and copies type
     * information from the template segment.
     */
    private ValueSegment createEmptyValueSegment(ValueSegment template) {
        if (template instanceof IntValueSegment ivs) {
            return new IntValueSegment(ivs.isSigned());
        } else if (template instanceof LongValueSegment lvs) {
            return new LongValueSegment(lvs.getType());
        } else if (template instanceof FloatValueSegment) {
            return new FloatValueSegment();
        } else if (template instanceof DoubleValueSegment) {
            return new DoubleValueSegment();
        } else if (template instanceof StringValueSegment) {
            return new StringValueSegment(false);
        } else if (template instanceof BinaryValueSegment) {
            return new BinaryValueSegment(false);
        } else if (template instanceof BooleanValueSegment) {
            return new BooleanValueSegment();
        } else {
            throw new UnsupportedOperationException(
                    "Unknown ValueSegment type: " + template.getClass().getName());
        }
    }

    @Override
    public void close() {
        if (multiIterator != null) {
            multiIterator.close();
        }
    }
}
