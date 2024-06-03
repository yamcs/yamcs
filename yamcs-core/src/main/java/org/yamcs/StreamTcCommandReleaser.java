package org.yamcs;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.StreamConfig.TcStreamConfigEntry;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Sends commands to yamcs streams
 * 
 * @author nm
 *
 */
public class StreamTcCommandReleaser extends AbstractProcessorService implements CommandReleaser {
    List<StreamWriter> writers = new ArrayList<>();
    private CommandHistoryPublisher commandHistoryPublisher;

    @Override
    public void init(Processor proc, YConfiguration config, Object spec) {
        super.init(proc, config, spec);
        readStreamConfig();
        this.processor = proc;
        this.commandHistoryPublisher = proc.getCommandHistoryPublisher();
    }

    private void readStreamConfig() {
        String yamcsInstance = getYamcsInstance();
        String procName = processor.getName();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        StreamConfig streamConfig = StreamConfig.getInstance(yamcsInstance);

        Set<String> streams = new LinkedHashSet<>();

        for (StreamConfigEntry sce : streamConfig.getEntries(StandardStreamType.TC)) {
            if (procName.equals(sce.getProcessor())) {
                streams.add(sce.getName());
            }
        }
        if (config.containsKey("stream")) {
            String streamName = config.getString("stream");

            if (!streams.isEmpty()) {
                log.warn(
                        "Configuration contains streams for processor {} both in instance config yamcs.{}.yaml (under streamConfig -> tc)"
                                + " and processor.yaml. The stream {} from processor.yaml will only be used if no pattern matches "
                                + " the streams ({}) specified in the instance config. To avoid confusion, please use just the instance config.",
                        procName, yamcsInstance, streamName, streams);
            }
            streams.add(streamName);
        }

        for (String streamName : streams) {
            TcStreamConfigEntry sce = streamConfig.getTcEntry(streamName);
            if (sce.getTcPatterns() != null) {
                log.debug("Sending TCs matching {} to stream {} ", sce.getTcPatterns(), streamName);
            } else {
                log.debug("Sending all TCs to stream {}", streamName);
            }
            Stream s = ydb.getStream(streamName);
            if (s == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }
            StreamWriter reader = new StreamWriter(s, sce.getTcPatterns());
            writers.add(reader);
        }
        if (writers.isEmpty()) {
            throw new ConfigurationException(
                    "Processor " + procName
                            + " found no TC streams to send data to. Please configure the processor: under streamConfig->tc;"
                            + " If tc processing has to be excluded from this processor, please configure the entry in processors.yaml appropiately");
        }
    }

    @Override
    public void releaseCommand(PreparedCommand pc) {
        for (StreamWriter w : writers) {
            if (w.releaseCommand(pc)) {
                return;
            }
        }
        commandHistoryPublisher.publishAck(pc.getCommandId(), AcknowledgeSent_KEY, processor.getCurrentTime(),
                AckStatus.NOK, "No stream available");
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    public void setCommandHistory(CommandHistoryPublisher commandHistoryPublisher) {
        // not interested in publishing anything to the command history
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    class StreamWriter {
        Stream stream;
        List<Pattern> tcPatterns;

        public StreamWriter(Stream stream, List<Pattern> tcPatterns) {
            this.stream = stream;
            this.tcPatterns = tcPatterns;
        }

        public boolean releaseCommand(PreparedCommand pc) {
            if (pc.getTcStream() == null || pc.getTcStream() == stream) { // Stream matches
                if (tcPatterns == null
                        || tcPatterns.stream().anyMatch(p -> p.matcher(pc.getCommandName()).matches())) {
                    log.trace("Releasing command {} on stream {}", pc.getLoggingId(), stream.getName());
                    stream.emitTuple(pc.toTuple());
                    return true;
                }
            }
            return false;
        }
    }
}
