package org.yamcs.tctm.cfs;

import static org.yamcs.StandardTupleDefinitions.TM_RECTIME_COLUMN;
import static org.yamcs.StandardTupleDefinitions.GENTIME_COLUMN;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Generate Yamcs events out of cFS event packets
 * <p>
 * 
 * The packets are filtered by message id (first 2 bytes of the header)
 * 
 * @author nm
 *
 */
public class CfsEventDecoder extends AbstractYamcsService implements StreamSubscriber {

    List<Stream> streams = new ArrayList<>();
    List<String> streamNames = new ArrayList<>();
    Set<Integer> msgIds = new HashSet<>();
    EventProducer eventProducer;
    TimeService timeService;
    ByteOrder byteOrder;
    Charset charset;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("streams", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("msgIds", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.addOption("byteOrder", OptionType.STRING);
        spec.addOption("charset", OptionType.STRING);

        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

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
        String order = config.getString("byteOrder", ByteOrder.BIG_ENDIAN.toString());
        if ("BIG_ENDIAN".equalsIgnoreCase(order)) {
            byteOrder = ByteOrder.BIG_ENDIAN;
        } else if ("LITTLE_ENDIAN".equalsIgnoreCase(order)) {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        } else {
            throw new ConfigurationException(
                    "Invalid '" + order + "' byte order specified. Use one of BIG_ENDIAN or LITTLE_ENDIAN");
        }
        String chrname = config.getString("charset", "US-ASCII");
        try {
            charset = Charset.forName(chrname);
        } catch (UnsupportedCharsetException e) {
            throw new ConfigurationException(
                    "Unsupported charset '" + chrname + "'. Please use one of " + Charset.availableCharsets().keySet());
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
            long gentime = (Long) t.getColumn(GENTIME_COLUMN);

            try {
                processPacket(rectime, gentime, packet);
            } catch (Exception e) {
                log.warn("Failed to process event packet", e);
            }
        }
    }

    private void processPacket(long rectime, long gentime, byte[] packet) {
        ByteBuffer buf = ByteBuffer.wrap(packet);
        buf.order(byteOrder);
        buf.position(12);
        String app = decodeString(buf, 20);
        int eventId = buf.getShort();
        int eventType = buf.getShort();
        buf.getInt();// int spacecraftId = */
        int processorId = buf.getInt();
        String msg = decodeString(buf, 122);

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

        eventProducer.sendEvent(ev);
    }

    private String decodeString(ByteBuffer buf, int maxLength) {
        maxLength = Math.min(maxLength, buf.remaining());
        ByteBuffer buf1 = buf.slice();
        buf1.limit(maxLength);
        int k =0;
        while (k<maxLength) {
            if (buf1.get(k) == 0) {
                break;
            }
            k++;
        }
        buf1.limit(k);

        String r = charset.decode(buf1).toString();
        buf.position(buf.position() + maxLength);

        return r;
    }

    @Override
    public void streamClosed(Stream stream) {
    }

}
