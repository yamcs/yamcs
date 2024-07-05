package org.yamcs.parameterarchive;

import java.util.NoSuchElementException;

import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterStatus;

/**
 * Iterates over parameter archive segments storing components of an aggregate or array value and reconstructs the
 * aggregate/array value from those components.
 * <p>
 * All the values belong to a single parameter group whose id is passed in the constructor.
 */
public class AggrrayIterator implements ParameterIterator {
    final MultiSegmentIterator it;
    final ParameterRequest req;
    final ParameterId parameterId;
    MultiParameterValueSegment currentSegment;
    int pos;

    AggrrayBuilder engBuilder;
    AggrrayBuilder rawBuilder;
    ParameterId[] members;
    TimedValue currentValue;

    public AggrrayIterator(ParameterArchive parchive, ParameterId parameterId, int parameterGroupId,
            ParameterRequest req) {
        ParameterIdDb pidDb = parchive.getParameterIdDb();

        members = pidDb.getAggarrayComponents(parameterId.getPid(), parameterGroupId);
        this.it = new MultiSegmentIterator(parchive, members, parameterGroupId, req);
        this.req = req;
        this.parameterId = parameterId;
        if (req.isRetrieveEngineeringValues()) {
            engBuilder = new AggrrayBuilder(members);
        }
        if (req.isRetrieveRawValues() && parameterId.hasRawValue()) {
            rawBuilder = new AggrrayBuilder(members);
        }

        if (it.isValid()) {
            init();
        }
    }

    private void init() {
        currentSegment = it.value();
        SortedTimeSegment timeSegment = currentSegment.timeSegment;

        if (req.isAscending()) {
            pos = timeSegment.lowerBound(req.getStart());
        } else {
            pos = timeSegment.higherBound(req.getStop());
        }
        if (valid(timeSegment, pos) || advancePos()) {
            while (true) {
                if (readCurrentValue()) {
                    break;
                }
                if (!advancePos()) {
                    break;
                }
            }
        } else {
            finished();
        }
    }

    @Override
    public void close() {
        it.close();
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

    // this must be called when the currentSegment and pos are valid (i.e. currentSegment!=null and pos is inside the
    // segment)
    // it builds the currentValue if there is at least a value for any component and returns true
    // otherwise (i.e. the values at the current position are all null, may happen because the parameter archive has
    // gaps) it returns false
    private boolean readCurrentValue() {

        long t = currentSegment.timeSegment.getTime(pos);

        ParameterStatus paramStatus = null;
        boolean foundOne = false;
        if (engBuilder != null) {
            engBuilder.clear();
        }
        if (rawBuilder != null) {
            rawBuilder.clear();
        }

        for (int i = 0; i < currentSegment.numParameters(); i++) {
            var pvs = currentSegment.getPvs(i);
            if (pvs != null) {
                if (engBuilder != null) {
                    Value v = pvs.getEngValue(pos);
                    if (v != null) {
                        foundOne = true;
                        engBuilder.setValue(members[i], v);
                    }
                }
                if (rawBuilder != null) {
                    Value v = pvs.getRawValue(pos);
                    if (v != null) {
                        foundOne = true;
                        rawBuilder.setValue(members[i], v);
                    }
                }
            }
        }
        if (foundOne) {
            Value engValue = null;
            Value rawValue = null;

            if (engBuilder != null) {
                engValue = engBuilder.build();
            }
            if (rawBuilder != null) {
                rawValue = rawBuilder.build();
            }
            currentValue = new TimedValue(t, engValue, rawValue, paramStatus);
        }
        return foundOne;
    }

    // advance the pos/it and return true if the position is valid
    private boolean advancePos() {
        boolean validPosition = false;
        var timeSegment = currentSegment.timeSegment;
        if (req.isAscending()) {
            pos++;
            if (pos >= timeSegment.size()) {
                it.next();
                if (it.isValid()) {
                    currentSegment = it.value();
                    pos = 0;
                    validPosition = true;
                } else {
                    finished();
                }
            } else if (timeSegment.getTime(pos) >= req.getStop()) {
                finished();
            } else {
                validPosition = true;
            }
        } else {
            pos--;
            if (pos < 0) {
                it.next();
                if (it.isValid()) {
                    currentSegment = it.value();
                    pos = currentSegment.timeSegment.size() - 1;
                    validPosition = true;
                } else {
                    finished();
                }
            } else if (timeSegment.getTime(pos) <= req.getStart()) {
                finished();
            } else {
                validPosition = true;
            }
        }
        return validPosition;
    }

    private void finished() {
        it.close();
        currentSegment = null;
        currentValue = null;
    }

    @Override
    public void next() {
        if (currentSegment == null) {
            throw new NoSuchElementException();
        }
        while (true) {
            if (!advancePos()) {
                break;
            }
            if (readCurrentValue()) {
                break;
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
    public ParameterId getParameterId() {
        return parameterId;
    }

    @Override
    public int getParameterGroupId() {
        return it.getParameterGroupId();
    }
}
