package org.yamcs.parameterarchive;

import java.util.NoSuchElementException;

import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterStatus;

/**
 * For a given parameter id and group id, iterates over all parameters in the parameter archive (across all segments and
 * partitions).
 * <p>
 * Provides objects of type {@link TimedValue}.
 * <p>
 * It embeds an {@link SegmentIterator} object.
 */
public class SimpleParameterIterator implements ParameterIterator {
    final SegmentIterator it;
    final ParameterRequest req;
    final ParameterId parameterId;
    ParameterValueSegment pvs;
    int pos;

    public SimpleParameterIterator(ParameterArchive parchive, ParameterId parameterId, int parameterGroupId,
            ParameterRequest req) {
        this.it = new SegmentIterator(parchive, parameterId, parameterGroupId, req);
        this.req = req;
        this.parameterId = parameterId;
        init();
    }

    private void init() {
        while (it.isValid()) {
            pvs = it.value();
            SortedTimeSegment timeSegment = pvs.timeSegment;

            if (req.isAscending()) {
                pos = timeSegment.search(req.getStart());
                if (pos < 0) {
                    pos = -pos - 1;
                }

            } else {
                pos = timeSegment.search(req.getStop());
                if (pos < 0) {
                    pos = -pos - 2;
                }
            }
            if (valid(pvs.timeSegment, pos)) {
                break;
            } else {
                it.next();
            }
        }
        if (!it.isValid()) {
            pvs = null;
            it.close();
        }
    }

    @Override
    public boolean isValid() {
        return pvs != null;
    }

    @Override
    public TimedValue value() {
        if (pvs == null) {
            throw new NoSuchElementException();
        }
        long t = pvs.timeSegment.getTime(pos);
        Value ev = (pvs.engValueSegment == null) ? null : pvs.engValueSegment.getValue(pos);
        Value rv = (pvs.rawValueSegment == null) ? null : pvs.rawValueSegment.getValue(pos);
        ParameterStatus ps = (pvs.parameterStatusSegment == null) ? null : pvs.parameterStatusSegment.get(pos);

        return new TimedValue(t, ev, rv, ps);
    }

    @Override
    public void next() {
        if (pvs == null) {
            throw new NoSuchElementException();
        }
        if (req.isAscending()) {
            pos++;
        } else {
            pos--;
        }

        if (!valid(pvs.timeSegment, pos)) {
            it.next();
            if (it.isValid()) {
                pvs = it.value();
                pos = req.isAscending() ? 0 : pvs.timeSegment.size() - 1;
            } else {
                it.close();
                pvs = null;
            }
        }
    }

    private boolean valid(SortedTimeSegment timeSegment, int pos) {
        if (req.isAscending()) {
            return pos < timeSegment.size() && timeSegment.getTime(pos) < req.getStop();
        } else {
            return pos >= 0 && timeSegment.getTime(pos) > req.getStart();
        }
    }

    @Override
    public void close() {
        it.close();
    }

    public ParameterId getParameterId() {
        return parameterId;
    }

    public int getParameterGroupId() {
        return it.getParameterGroupId();
    }

}
