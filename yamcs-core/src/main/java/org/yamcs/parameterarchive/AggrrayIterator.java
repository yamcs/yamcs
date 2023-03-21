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
    AggrrayBuilder engBuilder;
    AggrrayBuilder rawBuilder;
    ParameterId[] members;

    int pos;

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
        init();
    }

    private void init() {
        while (it.isValid()) {
            currentSegment = it.value();
            SortedTimeSegment timeSegment = currentSegment.timeSegment;

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
            if (valid(timeSegment, pos)) {
                break;
            } else {
                it.next();
            }
        }
        if (!it.isValid()) {
            currentSegment = null;
            it.close();
        }
    }

    @Override
    public void close() {
        it.close();
    }

    @Override
    public boolean isValid() {
        return currentSegment != null;
    }

    @Override
    public TimedValue value() {
        if (currentSegment == null) {
            throw new NoSuchElementException();
        }
        long t = currentSegment.timeSegment.getTime(pos);
        Value engValue = null;
        Value rawValue = null;
        ParameterStatus paramStatus = null;
        if (req.isRetrieveEngineeringValues()) {
            engBuilder.clear();
            ValueSegment[] engv = currentSegment.engValueSegments;
            for (int i = 0; i < engv.length; i++) {
                engBuilder.setValue(members[i], engv[i].getValue(pos));
            }
            engValue = engBuilder.build();
        }
        if (rawBuilder != null) {
            rawBuilder.clear();
            ValueSegment[] rawv = currentSegment.rawValueSegments;
            if (rawv[0] == null) {
                // workaround if the aggregate/array numeric type was not stored in the database
                // TODO: remove this case in the future
                rawBuilder = null;
            } else {
                for (int i = 0; i < rawv.length; i++) {
                    rawBuilder.setValue(members[i], rawv[i].getValue(pos));
                }
                rawValue = rawBuilder.build();
            }
        }

        return new TimedValue(t, engValue, rawValue, paramStatus);
    }

    @Override
    public void next() {
        if (currentSegment == null) {
            throw new NoSuchElementException();
        }
        if (req.isAscending()) {
            pos++;
        } else {
            pos--;
        }

        if (!valid(currentSegment.timeSegment, pos)) {
            it.next();
            if (it.isValid()) {
                currentSegment = it.value();
                pos = req.isAscending() ? 0 : currentSegment.timeSegment.size() - 1;
            } else {
                it.close();
                currentSegment = null;
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
