package org.yamcs.parameter;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.SingleParameterRetrieval;

/**
 * Combines retrieval from different sources:
 * <ul>
 * <li>Parameter Archive</li>
 * <li>Replays</li>
 * <li>Parameter cache</li>
 * <li>Realtime Parameter Archive filler</li>
 * </ul>
 * 
 */
public class ParameterRetrievalService extends AbstractYamcsService {
    String procName = "realtime";
    ParameterCache pcache;
    ParameterArchive parchive;

    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        this.procName = config.getString("processor", "realtime");
    }

    @Override
    protected void doStart() {
        var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
        var proc = ysi.getProcessor(procName);
        pcache = new ArrayParameterCache(yamcsInstance, null);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    // Called from the PRM
    public void update(List<ParameterValue> pvlist) {
        if (pcache != null) {
            pcache.update(pvlist);
        }
    }

    /**
     * Retrieves a single scalar parameter or aggregate/array member.
     */
    public void retrieveScalar(ParameterWithId pid, ParameterRequest request, Consumer<ParameterValueArray> consumer)
            throws IOException {
        long coverageEnd = parchive.coverageEnd();
        if (request.ascending()) {
            // ascending case -> retrieve max possible from the parameter archive
            var tc = retrieveScalarParameterArchive(pid, request, consumer);
            if (request.stop() > tc.time && request.stop() > coverageEnd) {
                var req1 = request.withUpdatedStart(tc.time);
                // then from cache or via replay
                retrieveScalarReplayOrCache(pid, req1, consumer);
            }
        } else {
            // descending case
            // if the request is beyond parameter archive coverage, retrieve first by cache or replay
            if (request.stop() > coverageEnd) {
                if (request.start() >= coverageEnd) {
                    // request does not overlap at all with the parameter archive coverage
                    retrieveScalarReplayOrCache(pid, request, consumer);
                } else {
                    // request overlaps with the parameter archive coverage
                    var req1 = request.withUpdatedStart(coverageEnd);
                    retrieveScalarReplayOrCache(pid, req1, consumer);
                    var req2 = request.withUpdatedStop(coverageEnd);
                    retrieveScalarParameterArchive(pid, req2, consumer);

                }
            } else {
                // request can be satisfied only by parameter archive
                retrieveScalarParameterArchive(pid, request, consumer);
            }
        }
    }

    TimeAndCount retrieveScalarReplayOrCache(ParameterWithId pid, ParameterRequest request,
            Consumer<ParameterValueArray> consumer) {
        return null;
    }

    public TimeAndCount retrieveScalarParameterArchive(ParameterWithId pid, ParameterRequest request,
            Consumer<ParameterValueArray> consumer) throws IOException {
        SingleParameterRetrieval spar = new SingleParameterRetrieval(parchive, pid.getQualifiedName(), request);
        TimeAndCount tc = new TimeAndCount(Long.MAX_VALUE, 0);
        try {
            spar.retrieve(pva -> {
                long[] timestamps = pva.getTimestamps();
                tc.time = timestamps[timestamps.length - 1];
                tc.count += timestamps.length;
                consumer.accept(pva);
            });
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        return tc;
    }

    static final class TimeAndCount {
        long time;
        int count;

        public TimeAndCount(long time, int count) {
            this.time = time;
            this.count = count;
        }
    };
}
