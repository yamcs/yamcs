package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.ArchiveParameterSegmentInfo;
import org.yamcs.utils.TimeEncoding;


public class ParameterInfoRetrieval {
    final private ParameterArchive parchive;
    private final Logger log = LoggerFactory.getLogger(ParameterInfoRetrieval.class);

    int pid;
    long start, stop;

    public ParameterInfoRetrieval(ParameterArchive parchive, int pid, long start, long stop) {
        this.parchive = parchive;
        this.pid = pid;
        this.start = start;
        this.stop = stop;
    }

    public void retrieve(Consumer<ArchiveParameterSegmentInfo> consumer) throws RocksDBException, IOException {

        int[] pgids = parchive.getParameterGroupIdDb().getAllGroups(pid);

        if (pgids.length == 0) {
            log.error("Found no parameter group for parameter Id {}", pid);
            return;
        }

        for (int pgid : pgids) {
            retrieveInfo(pgid, consumer);
        }
    }

    private void retrieveInfo(int parameterGroupId, Consumer<ArchiveParameterSegmentInfo> consumer)
            throws RocksDBException, IOException {

        ParameterRequest req = new ParameterRequest(start, stop, true, false, false, false);
        SegmentIterator it = new SegmentIterator(parchive, pid, parameterGroupId, req);

        try {
            
            while (it.isValid()) {
                ParameterValueSegment pvs = it.value();
                SortedTimeSegment timeSegment = pvs.timeSegment;
                ArchiveParameterSegmentInfo apsi = ArchiveParameterSegmentInfo.newBuilder()
                        .setGroupId(parameterGroupId)
                        .setCount(timeSegment.size())
                        .setStart(TimeEncoding.toProtobufTimestamp(timeSegment.getSegmentStart()))
                        .setEnd(TimeEncoding.toProtobufTimestamp(timeSegment.getSegmentEnd()))
                        .build();
                consumer.accept(apsi);
                it.next();
            }
        } finally {
            it.close();
        }
    }

}
