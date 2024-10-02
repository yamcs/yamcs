package org.yamcs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.StreamConfig.TmStreamConfigEntry;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Receives packets from yamcs streams and sends them to the Processor/TmProcessor for extraction of parameters.
 * 
 * Can read from multiple streams, each with its own root container used as start of XTCE packet processing
 * 
 * @author nm
 *
 */
public class StreamTmPacketProvider extends AbstractProcessorService implements TmPacketProvider {
    Stream stream;
    TmProcessor tmProcessor;
    Mdb mdb;
    volatile boolean disabled = false;
    volatile long lastPacketTime;

    List<StreamReader> readers = new ArrayList<>();

    @Override
    public void init(Processor proc, YConfiguration config, Object spec) {
        super.init(proc, config, spec);
        this.tmProcessor = proc.getTmProcessor();
        this.mdb = MdbFactory.getInstance(proc.getInstance());
        readStreamConfig(proc.getName());
        proc.setPacketProvider(this);
    }

    /**
     * add to readers all the streams specifically specified in the streams config (in processor.yaml) or those that
     * have configured the processor to this processor in yamcs.instance.yaml
     */
    private void readStreamConfig(String procName) {
        String yamcsInstance = getYamcsInstance();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        StreamConfig streamConfig = StreamConfig.getInstance(yamcsInstance);

        Set<String> streams = new HashSet<>();
        if (config.containsKey("streams")) {
            streams.addAll(config.getList("streams"));
        }

        for (StreamConfigEntry sce : streamConfig.getEntries(StandardStreamType.TM)) {
            if (procName.equals(sce.getProcessor())) {
                streams.add(sce.getName());
            }
        }

        for (String streamName : streams) {
            TmStreamConfigEntry sce = streamConfig.getTmEntry(streamName);
            if (sce == null)
                throw new ConfigurationException("Cannot find TM stream configuration for '" + streamName + "'");

            SequenceContainer rootContainer;
            rootContainer = sce.getRootContainer();
            if (rootContainer == null) {
                rootContainer = mdb.getRootSequenceContainer();
            }
            if (rootContainer == null) {
                throw new ConfigurationException(
                        "MDB does not have a root sequence container and none was defined under streamConfig -> tm");
            }

            log.debug("Processing packets from stream {} starting with root container {}", streamName,
                    rootContainer.getQualifiedName());
            Stream s = ydb.getStream(streamName);
            if (s == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }
            StreamReader reader = new StreamReader(s, rootContainer);
            readers.add(reader);
        }
        if (readers.isEmpty()) {
            throw new ConfigurationException(
                    "Processor " + procName
                            + " found no tm_stream to process data from. Please configure the processor: under streamConfig->tm;"
                            + " If tm processing has to be excluded from this processor, please configure the entry in processors.yaml appropiately");
        }
    }

    @Override
    protected void doStart() {
        for (StreamReader sr : readers) {
            sr.stream.addSubscriber(sr);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (StreamReader sr : readers) {
            sr.stream.removeSubscriber(sr);
        }
        notifyStopped();
    }

    @Override
    public boolean isArchiveReplay() {
        return false;
    }

    class StreamReader implements StreamSubscriber {
        Stream stream;
        SequenceContainer rootContainer;

        public StreamReader(Stream stream, SequenceContainer sc) {
            this.stream = stream;
            this.rootContainer = sc;
        }

        @Override
        public void onTuple(Stream s, Tuple tuple) {
            long rectime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
            long gentime = (Long) tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
            int seqCount = (Integer) tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
            byte[] packet = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
            TmPacket pwrt = new TmPacket(rectime, gentime, seqCount, packet);
            lastPacketTime = gentime;

            String preferredRootContainerName = tuple.getColumn(StandardTupleDefinitions.TM_ROOT_CONTAINER_COLUMN);
            if (preferredRootContainerName != null) {
                var preferredRootContainer = mdb.getSequenceContainer(preferredRootContainerName);
                pwrt.setRootContainer(preferredRootContainer);
            }

            tmProcessor.processPacket(pwrt, rootContainer);
        }

        @Override
        public void streamClosed(Stream s) {
            notifyStopped();
        }
    }
}
