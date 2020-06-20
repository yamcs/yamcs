package org.yamcs.management;

import java.util.List;

import org.yamcs.Processor;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.Statistics;
import org.yamcs.protobuf.TmStatistics;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimestampUtil;
import org.yamcs.xtceproc.ProcessingStatistics;

import com.google.protobuf.ByteString;

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

    public static CommandQueueEntry toCommandQueueEntry(CommandQueue q, PreparedCommand pc) {
        Processor c = q.getProcessor();
        CommandQueueEntry.Builder entryb = CommandQueueEntry.newBuilder()
                .setInstance(q.getProcessor().getInstance())
                .setProcessorName(c.getName())
                .setQueueName(q.getName())
                .setId(pc.getId())
                .setOrigin(pc.getOrigin())
                .setSequenceNumber(pc.getSequenceNumber())
                .setCommandName(pc.getCommandName())
                .setSource(pc.getSource())
                .setPendingTransmissionConstraints(pc.isPendingTransmissionConstraints())
                .setUuid(pc.getUUID().toString())
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(pc.getGenerationTime()))
                .setUsername(pc.getUsername());

        if (pc.getBinary() != null) {
            entryb.setBinary(ByteString.copyFrom(pc.getBinary()));
        }

        if (pc.getComment() != null) {
            entryb.setComment(pc.getComment());
        }

        return entryb.build();
    }

    public static CommandQueueInfo toCommandQueueInfo(CommandQueue queue, int order, boolean detail) {
        Processor c = queue.getProcessor();
        CommandQueueInfo.Builder b = CommandQueueInfo.newBuilder()
                .setOrder(order)
                .setInstance(c.getInstance())
                .setProcessorName(c.getName())
                .setName(queue.getName())
                .setState(queue.getState())
                .setNbRejectedCommands(queue.getNbRejectedCommands())
                .setNbSentCommands(queue.getNbSentCommands());

        if (queue.getStateExpirationRemainingS() != -1) {
            b.setStateExpirationTimeS(queue.getStateExpirationRemainingS());
        }
        if (detail) {
            for (PreparedCommand pc : queue.getCommands()) {
                CommandQueueEntry qEntry = ManagementGpbHelper.toCommandQueueEntry(queue, pc);
                b.addEntry(qEntry);
            }
        }
        return b.build();
    }
}
