package org.yamcs.management;

import java.util.Collection;

import org.yamcs.Processor;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.TmStatistics;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtceproc.ProcessingStatistics;

import com.google.protobuf.ByteString;

/**
 * Provides common functionality to assemble and disassemble GPB messages
 */
public final class ManagementGpbHelper {

    public static Statistics buildStats(Processor processor) {
        ProcessingStatistics ps = processor.getTmProcessor().getStatistics();
        Statistics.Builder statsb = Statistics.newBuilder()
                .setLastUpdated(ps.getLastUpdated())
                .setLastUpdatedUTC(TimeEncoding.toString(ps.getLastUpdated()))
                .setInstance(processor.getInstance())
                .setYProcessorName(processor.getName());

        Collection<ProcessingStatistics.TmStats> tmstats = ps.stats.values();
        if (tmstats == null) {
            return ManagementService.STATS_NULL;
        }

        for (ProcessingStatistics.TmStats t : tmstats) {
            TmStatistics ts = TmStatistics.newBuilder()
                    .setPacketName(t.packetName)
                    .setLastPacketTime(t.lastPacketTime)
                    .setLastReceived(t.lastReceived)
                    .setReceivedPackets(t.receivedPackets)
                    .setLastPacketTimeUTC(TimeEncoding.toString(t.lastPacketTime))
                    .setLastReceivedUTC(TimeEncoding.toString(t.lastReceived))
                    .setSubscribedParameterCount(t.subscribedParameterCount).build();
            statsb.addTmstats(ts);
        }
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
                .setTime(TimeEncoding.toString(processor.getCurrentTime()))
                .setReplay(processor.isReplay());

        if (processor.isReplay()) {
            processorb.setReplayRequest(processor.getReplayRequest());
            processorb.setReplayState(processor.getReplayState());
        }
        return processorb.build();
    }

    public static CommandQueueEntry toCommandQueueEntry(CommandQueue q, PreparedCommand pc) {
        Processor c = q.getChannel();
        return CommandQueueEntry.newBuilder()
                .setInstance(q.getChannel().getInstance())
                .setProcessorName(c.getName())
                .setQueueName(q.getName())
                .setCmdId(pc.getCommandId())
                .setSource(pc.getSource())
                .setBinary(ByteString.copyFrom(pc.getBinary()))
                .setUuid(pc.getUUID().toString())
                .setGenerationTime(pc.getGenerationTime())
                .setGenerationTimeUTC(TimeEncoding.toString(pc.getGenerationTime()))
                .setUsername(pc.getUsername())
                .build();
    }

    public static CommandQueueInfo toCommandQueueInfo(CommandQueue queue, boolean detail) {
        Processor c = queue.getChannel();
        CommandQueueInfo.Builder b = CommandQueueInfo.newBuilder()
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
