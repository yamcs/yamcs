package org.yamcs.management;

import java.util.List;

import org.yamcs.Processor;
import org.yamcs.mdb.ProcessingStatistics;
import org.yamcs.protobuf.AcknowledgmentInfo;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.Statistics;
import org.yamcs.protobuf.TmStatistics;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimestampUtil;

/**
 * Provides common functionality to assemble and disassemble GPB messages
 */
public final class ManagementGpbHelper {

    public static Statistics buildStats(Processor processor, List<TmStatistics> statistics) {
        ProcessingStatistics ps = processor.getTmProcessor().getStatistics();
        Statistics.Builder statsb = Statistics.newBuilder()
                .setInstance(processor.getInstance())
                .setProcessor(processor.getName())
                .setLastUpdated(TimestampUtil.java2Timestamp(ps.getLastUpdated()));
        statsb.addAllTmstats(statistics);
        return statsb.build();
    }

    public static ProcessorInfo toProcessorInfo(Processor processor) {
        ProcessorInfo.Builder processorb = ProcessorInfo.newBuilder().setInstance(processor.getInstance())
                .setName(processor.getName()).setType(processor.getType())
                .setCreator(processor.getCreator())
                .setHasCommanding(processor.hasCommanding())
                .setHasAlarms(processor.hasAlarmServer())
                .setState(processor.getState())
                .setPersistent(processor.isPersistent())
                .setProtected(processor.isProtected())
                .setTime(TimeEncoding.toProtobufTimestamp(processor.getCurrentTime()))
                .setReplay(processor.isReplay())
                .setCheckCommandClearance(processor.getConfig().checkCommandClearance());

        if (processor.isReplay()) {
            ReplayRequest request = processor.getCurrentReplayRequest();
            processorb.setReplayRequest(request);
            processorb.setReplayState(processor.getReplayState());
        }

        for (var ack : processor.getAcknowledgments()) {
            var ackInfo = AcknowledgmentInfo.newBuilder()
                    .setName(ack.getName());
            if (ack.getDescription() != null) {
                ackInfo.setDescription(ack.getDescription());
            }
            processorb.addAcknowledgments(ackInfo);
        }
        return processorb.build();
    }
}
