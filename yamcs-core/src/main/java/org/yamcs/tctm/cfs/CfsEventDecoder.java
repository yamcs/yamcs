package org.yamcs.tctm.cfs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsService;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

import static org.yamcs.StandardTupleDefinitions.*;

/**
 * Generate Yamcs events out of cFS event packets
 * <p>
 * 
 * The packets are filtered by message id (first 2 bytes of the header)
 * 
 * @author nm
 *
 */
public class CfsEventDecoder extends AbstractService implements YamcsService, StreamSubscriber {
    List<Stream> streams = new ArrayList<>();
    List<String> streamNames = new ArrayList<>();
    private final String yamcsInstance;
    final Logger log;
    Set<Integer> msgIds = new HashSet<>();
    EventProducer eventProducer;
    TimeService timeService;

    public CfsEventDecoder(String yamcsInstance, YConfiguration config) {
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);
        this.yamcsInstance = yamcsInstance;

        if (config.containsKey("streams")) {
            streamNames = config.getList("streams");
        } else {
            StreamConfig sconf = StreamConfig.getInstance(yamcsInstance);

            for (StreamConfigEntry sce : sconf.getEntries()) {
                if (sce.getType() == StandardStreamType.tm) {
                    streamNames.add(sce.getName());
                }
            }
        }
        List<Integer> l = config.getList("msgIds");
        l.forEach(x -> msgIds.add(x));
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
    }

    @Override
    protected void doStart() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        for (String sn : streamNames) {
            Stream s = ydb.getStream(sn);
            if (s != null) {
                log.debug("Subscribing to stream {}", sn);
                s.addSubscriber(this);
                streams.add(s);
            }
        }
        timeService = YamcsServer.getTimeService(yamcsInstance);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (Stream s : streams) {
            s.addSubscriber(this);
        }
        notifyStopped();
    }

    @Override
    public void onTuple(Stream stream, Tuple t) {
        byte[] packet = (byte[]) t.getColumn("packet");

        int msgId = ByteArrayUtils.decodeShort(packet, 0);

        if (msgIds.contains(msgId)) {
            long rectime = (Long) t.getColumn(TM_RECTIME_COLUMN);
            // long gentime = (Long) t.getColumn(TM_GENTIME_COLUMN);
            long gentime = timeService.getMissionTime();

            try {
                processPacket(rectime, gentime, packet);
            } catch (Exception e) {
                log.warn("Failed to process event packet", e);
            }
        }
    }

    private void processPacket(long rectime, long gentime, byte[] packet) {
        int offset = 12;
        String app = decodeString(packet, offset, 20);
        offset += 20;
        int eventId = ByteArrayUtils.decodeShortLE(packet, offset);
        offset += 2;
        int eventType = ByteArrayUtils.decodeShortLE(packet, offset);
        offset += 2;
        // int spacecraftId = ByteArrayUtils.decodeInt(packet, offset);
        offset += 4;

        int processorId = ByteArrayUtils.decodeIntLE(packet, offset);
        offset += 4;
        String msg = decodeString(packet, offset, 122);

        EventSeverity evSev;

        switch (eventType) {
        case 3:
            evSev = EventSeverity.ERROR;
            break;
        case 4:
            evSev = EventSeverity.CRITICAL;
            break;
        default:
            evSev = EventSeverity.INFO;
        }

        Event ev = Event.newBuilder().setGenerationTime(gentime).setReceptionTime(rectime)
                .setSeqNumber(0).setSource("/CFS/CPU" + processorId + "/" + app).setSeverity(evSev)
                .setType("EVID" + eventId).setMessage(msg).build();

        System.out.println("Sending event " + ev);
        eventProducer.sendEvent(ev);
    }

    private String decodeString(byte[] packet, int offset, int maxLength) {
        int length;
        maxLength = Math.min(maxLength, packet.length - offset);
        for (length = 0; length < maxLength; length++) {
            if (packet[offset + length] == 0) {
                break;
            }
        }
        return new String(packet, offset, length);
    }

    @Override
    public void streamClosed(Stream stream) {
    }

}
