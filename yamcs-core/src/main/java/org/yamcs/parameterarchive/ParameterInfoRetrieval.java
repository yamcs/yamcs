package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.protobuf.ArchiveParameterSegmentInfo;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.TimeEncoding;

import static org.yamcs.parameterarchive.ParameterArchive.getIntervalStart;
import static org.yamcs.parameterarchive.ParameterArchive.getIntervalEnd;

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

        List<Partition> parts = parchive.getPartitions(getIntervalStart(start), getIntervalEnd(stop), true);
        for (Partition p : parts) {
            for (int pgid : pgids) {
                retrieveValuesFromPartitionSingleGroup(pgid, p, consumer);
            }
        }
    }

    // this is the easy case, one single parameter group -> no merging of segments necessary
    private void retrieveValuesFromPartitionSingleGroup(int parameterGroupId, Partition p,
            Consumer<ArchiveParameterSegmentInfo> consumer) throws RocksDBException, IOException {
        RocksIterator it = parchive.getIterator(p);

        try {
            PartitionIterator pit = new PartitionIterator(it, pid, parameterGroupId, start, stop,
                    true, false, false, false);

            while (pit.isValid()) {
                SegmentKey key = pit.key();
                SortedTimeSegment timeSegment = parchive.getTimeSegment(p, key.segmentStart, parameterGroupId);
                if (timeSegment == null) {
                    String msg = "Cannot find a time segment for parameterGroupId=" + parameterGroupId
                            + " segmentStart = " + key.segmentStart
                            + " despite having a value segment for parameterId: " + pid;
                    log.error(msg);
                    throw new DatabaseCorruptionException(msg);
                }
                ArchiveParameterSegmentInfo apsi = ArchiveParameterSegmentInfo.newBuilder()
                        .setGroupId(parameterGroupId)
                        .setCount(timeSegment.size())
                        .setStart(TimeEncoding.toProtobufTimestamp(timeSegment.getSegmentStart()))
                        .setEnd(TimeEncoding.toProtobufTimestamp(timeSegment.getSegmentEnd()))
                        .build();

                consumer.accept(apsi);
                pit.next();
            }
        } finally {
            it.close();
        }
    }

}
