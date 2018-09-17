package org.yamcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Receives packets from yamcs streams and sends them to the Processor/TmProcessor for extraction of parameters.
 * 
 * Can read from multiple streams, each with its own root container used as start of XTCE packet processing
 * 
 * @author nm
 *
 */
public class StreamTmPacketProvider extends AbstractService implements TmPacketProvider {
    Stream stream;
    TmProcessor tmProcessor;
    volatile boolean disabled = false;
    volatile long lastPacketTime;

    List<StreamReader> readers = new ArrayList<>();

    public StreamTmPacketProvider(String yamcsInstance, Map<String, Object> config) throws ConfigurationException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);

        if (!config.containsKey("streams")) {
            throw new ConfigurationException("Cannot find key 'streams' in StreamTmPacketProvider");
        }

        StreamConfig streamConfig = StreamConfig.getInstance(yamcsInstance);

        List<String> streams = YConfiguration.getList(config, "streams");
        for (String streamName : streams) {
            StreamConfigEntry sce = streamConfig.getEntry(StandardStreamType.tm, streamName);
            SequenceContainer rootContainer;
            if (sce.getRootContainer() != null) {
                rootContainer = sce.getRootContainer();
            } else {
                rootContainer = xtcedb.getRootSequenceContainer();
                if (rootContainer == null) {
                    throw new ConfigurationException("XtceDb does not have a root sequence container");
                }
            }
            Stream s = ydb.getStream(streamName);
            if (s == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }
            StreamReader reader = new StreamReader(s, rootContainer);
            readers.add(reader);
        }
    }

    @Override
    public void init(Processor proc) {
        this.tmProcessor = proc.getTmProcessor();
        proc.setPacketProvider(this);
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
            long gentime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_GENTIME_COLUMN);
            int seqCount = (Integer) tuple.getColumn(StandardTupleDefinitions.TM_SEQNUM_COLUMN);
            byte[] packet = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
            PacketWithTime pwrt = new PacketWithTime(rectime, gentime, seqCount, packet);
            lastPacketTime = gentime;
            tmProcessor.processPacket(pwrt);
        }

        @Override
        public void streamClosed(Stream s) {
            notifyStopped();
        }
    }
}
