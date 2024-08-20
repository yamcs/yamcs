package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.TimeEncoding;

/**
 * Retrieves multiple parameters from the Parameter Archive.
 * 
 * <p>
 * The Parameter Archive stores parameters in segments - one segment contains multiple values for the same parameter.
 * Even more, the values of one parameter may be split into multiple groups.
 * <p>
 * This class will merge (interleave) the segments such as the output is a list of parameters at each timestamp.
 * <p>
 * If we imagine the parameter values as a matrix where one line corresponds to all parameters timestamped at one
 * specific time, the purpose of this class is to transform from columns (Parameter Archive representation) to rows
 * (user requested representation)
 *
 */
public class MultiParameterRetrieval {
    final ParameterArchive parchive;
    final MultipleParameterRequest mpvr;
    final AggrrayBuilder[] aggarrayBuilders;

    SegmentEncoderDecoder vsEncoder = new SegmentEncoderDecoder();
    private final Log log;

    public MultiParameterRetrieval(ParameterArchive parchive, MultipleParameterRequest mpvr) {
        this.parchive = parchive;
        this.mpvr = mpvr;
        this.aggarrayBuilders = new AggrrayBuilder[0];
        this.log = new Log(this.getClass(), parchive.getYamcsInstance());
    }

    public void retrieve(Consumer<ParameterIdValueList> consumer) throws RocksDBException, IOException {
        log.trace("Starting a parameter retrieval: {}", mpvr);

        ParameterGroupIdDb pgDb = parchive.getParameterGroupIdDb();
        PriorityQueue<ParameterIterator> queue = new PriorityQueue<>(new IteratorComparator(mpvr.ascending));
        int[] parameterGroupIds = mpvr.parameterGroupIds;

        for (int i = 0; i < mpvr.parameterIds.length; i++) {
            ParameterId paraId = mpvr.parameterIds[i];
            ParameterRequest req = new ParameterRequest(mpvr.start, mpvr.stop, mpvr.ascending, mpvr.retrieveEngValues,
                    mpvr.retrieveRawValues && paraId.hasRawValue(), mpvr.retrieveParamStatus);

            if (parameterGroupIds != null) {
                queueIterator(queue, paraId, parameterGroupIds[i], req);
            } else {
                int pid0 = paraId.isSimple() ? paraId.getPid() : paraId.getComponents().get(0);
                for (int pgid : pgDb.getAllGroups(pid0)) {
                    queueIterator(queue, paraId, pgid, req);
                }
            }
        }
        log.trace("Got {} parallel iterators", queue.size());

        Merger merger = new Merger(mpvr, consumer);

        ParameterIterator it = null;
        try {
            while (!queue.isEmpty()) {
                it = queue.poll();
                merger.process(it.getParameterId(), it.getParameterGroupId(), it.value());

                if (merger.sentEnough())
                    return;

                it.next();
                if (it.isValid()) {
                    queue.add(it);
                }
            }
            merger.flush();
        } catch (ConsumerAbortException e) {
            log.debug("Stopped early due to receiving ConsumerAbortException");
        } finally {
            if (it != null) {
                it.close();
            }
            queue.forEach(it1 -> it1.close());
        }
        log.trace("Retrieval finished");
    }

    private void queueIterator(PriorityQueue<ParameterIterator> queue,
            ParameterId paraId, int pgid, ParameterRequest req) {
        ParameterIterator it;
        if (paraId.isSimple()) {
            it = new SimpleParameterIterator(parchive, paraId, pgid, req);
        } else {
            it = new AggrrayIterator(parchive, paraId, pgid, req);
        }
        if (it.isValid()) {
            queue.add(it);
        } else {
            it.close();
        }
    }

    /**
     * Merge values from the parallel iterators taking care that parameters from the same group end up in the same list
     *
     */
    static class Merger {
        int count = 0;
        // group id -> parameter list
        Map<Integer, ParameterIdValueList> values = new HashMap<>();
        long curTime = TimeEncoding.INVALID_INSTANT;

        final MultipleParameterRequest mpvr;
        final Consumer<ParameterIdValueList> consumer;

        public Merger(MultipleParameterRequest mpvr, Consumer<ParameterIdValueList> consumer) {
            this.mpvr = mpvr;
            this.consumer = consumer;
        }

        void process(ParameterId paraId, int pgid, TimedValue tv) {
            long t = tv.instant;
            if (t != curTime) {
                flush();
                curTime = t;
            }
            ParameterIdValueList vlist = values.computeIfAbsent(pgid, k1 -> new ParameterIdValueList(tv.instant));

            ParameterValue pv = new ParameterValue(paraId.getParamFqn());
            pv.setGenerationTime(tv.instant);

            if (tv.engValue != null) {
                pv.setEngValue(tv.engValue);
            }
            if (tv.rawValue != null) {
                pv.setRawValue(tv.rawValue);
            }
            if (tv.paramStatus != null) {
                ParameterStatus ps = tv.paramStatus;
                if (ps.hasAcquisitionStatus()) {
                    pv.setAcquisitionStatus(ps.getAcquisitionStatus());
                }
                if (ps.hasMonitoringResult()) {
                    pv.setMonitoringResult(ps.getMonitoringResult());
                }
                if (ps.getAlarmRangeCount() > 0) {
                    pv.addAlarmRanges(ps.getAlarmRangeList());
                }
                if (ps.hasExpireMillis()) {
                    pv.setExpireMillis(ps.getExpireMillis());
                }
                if (ps.hasRangeCondition()) {
                    pv.setRangeCondition(ps.getRangeCondition());
                }
            }

            vlist.add(paraId.getPid(), pv);
        }

        public void flush() {
            for (ParameterIdValueList pvlist : values.values()) {
                consumer.accept(pvlist);
                count++;
                if (sentEnough()) {
                    break;
                }
            }
            values.clear();
        }

        boolean sentEnough() {
            return mpvr.limit > 0 && count >= mpvr.limit;
        }

    }

    static class IteratorComparator implements Comparator<ParameterIterator> {
        final boolean ascending;

        public IteratorComparator(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public int compare(ParameterIterator it1, ParameterIterator it2) {
            TimedValue pvs1 = it1.value();
            TimedValue pvs2 = it2.value();

            int c = ascending ? Long.compare(pvs1.instant, pvs2.instant)
                    : Long.compare(pvs2.instant, pvs1.instant);

            if (c != 0) {
                return c;
            }
            //
            // make sure the parameters are extracted in the order of their id
            // (rather than some random order from PriorityQueue)
            return Integer.compare(it1.getParameterId().getPid(), it2.getParameterId().getPid());
        }
    }
}
