package org.yamcs.management;

import java.util.List;

import org.yamcs.Processor;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.Statistics;
import org.yamcs.protobuf.TmStatistics;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimestampUtil;
import org.yamcs.xtceproc.ProcessingStatistics;

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
                .setTime(TimeEncoding.toProtobufTimestamp(processor.getCurrentTime()))
                .setReplay(processor.isReplay())
                .setCheckCommandClearance(processor.getConfig().checkCommandClearance());

        if (processor.isReplay()) {
            processorb.setReplayRequest(processor.getReplayRequest());
            processorb.setReplayState(processor.getReplayState());
        }
        return processorb.build();
    }
}
