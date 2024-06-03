package org.yamcs.parameterarchive;

import java.util.NoSuchElementException;

import org.yamcs.utils.PeekingIterator;

/**
 * For a given parameter id and group id, iterates over all parameters in the parameter archive (across all segments and
 * partitions).
 * <p>
 * Provides objects of type {@link TimedValue}.
 * <p>
 * It embeds an {@link SegmentIterator} object.
 */
public class SimpleParameterIterator implements ParameterIterator {
    final SegmentIterator segIt;
    final ParameterRequest req;
    final ParameterId parameterId;

    PeekingIterator<TimedValue> pvsIt;
    TimedValue currentValue = null;

    public SimpleParameterIterator(ParameterArchive parchive, ParameterId parameterId, int parameterGroupId,
            ParameterRequest req) {
        this.req = req;
        this.parameterId = parameterId;
        this.segIt = new SegmentIterator(parchive, parameterId, parameterGroupId, req);
        next();
    }

    @Override
    public boolean isValid() {
        return currentValue != null;
    }

    @Override
    public TimedValue value() {
        if (currentValue == null) {
            throw new NoSuchElementException();
        }
        return currentValue;
    }

    @Override
    public void next() {
        currentValue = null;

        if (pvsIt == null || !pvsIt.isValid()) {
            nextPvsIt();

            if (pvsIt == null || !pvsIt.isValid()) {
                return;
            }
        }

        currentValue = pvsIt.value();
        pvsIt.next();
        if ((req.ascending && currentValue.instant >= req.stop) ||
                (!req.ascending && currentValue.instant <= req.start)) {
            currentValue = null;
            pvsIt = null;
            close();
            return;
        }
    }

    private void nextPvsIt() {
        while (segIt.isValid()) {
            var pvs = segIt.value();
            segIt.next();

            pvsIt = req.ascending ? pvs.newAscendingIterator(req.getStart())
                    : pvs.newDescendingIterator(req.getStop());
            if (pvsIt.isValid()) {
                break;
            }
        }
    }

    @Override
    public void close() {
        segIt.close();
    }

    public ParameterId getParameterId() {
        return parameterId;
    }

    public int getParameterGroupId() {
        return segIt.getParameterGroupId();
    }
}
