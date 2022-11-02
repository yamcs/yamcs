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

    ParameterId parameterId;
    long start, stop;

    public ParameterInfoRetrieval(ParameterArchive parchive, ParameterId parameterId, long start, long stop) {
        this.parchive = parchive;
        this.parameterId = parameterId;
        this.start = start;
        this.stop = stop;
    }

    public void retrieve(Consumer<ArchiveParameterSegmentInfo> consumer) throws RocksDBException, IOException {

        int[] pgids = parchive.getParameterGroupIdDb().getAllGroups(parameterId.getPid());

        if (pgids.length == 0) {
            log.error("Found no parameter group for parameter Id {}", parameterId);
            return;
        }

        for (int pgid : pgids) {
            retrieveInfo(pgid, consumer);
        }
    }

    private void retrieveInfo(int parameterGroupId, Consumer<ArchiveParameterSegmentInfo> consumer)
            throws RocksDBException, IOException {

        ParameterRequest req = new ParameterRequest(start, stop, true, false, false, false);


        try (SegmentIterator it = new SegmentIterator(parchive, parameterId, parameterGroupId, req)) {
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
        }
    }

}
