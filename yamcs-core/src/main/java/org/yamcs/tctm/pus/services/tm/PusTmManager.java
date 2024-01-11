package org.yamcs.tctm.pus.services.tm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.tctm.pus.services.PusService;

import org.yamcs.tctm.pus.services.tm.one.ServiceOne;
import org.yamcs.tctm.pus.services.tm.two.ServiceTwo;
import org.yamcs.tctm.pus.services.tm.three.ServiceThree;
import org.yamcs.tctm.pus.services.tm.five.ServiceFive;


public class PusTmManager extends AbstractYamcsService implements StreamSubscriber {
    Log log;
    String yamcsInstance;

    // Static Members
    public static int PRIMARY_HEADER_LENGTH = 6;
    public static int DEFAULT_SECONDARY_HEADER_LENGTH = 64;
    public static int DEFAULT_ABSOLUTE_TIME_LENGTH = 4;
    public static int PUS_HEADER_LENGTH;

    public static int secondaryHeaderLength;
    public static int absoluteTimeLength;

    Map<Integer, PusService> pusServices = new HashMap<>();
    YConfiguration serviceConfig;
    PusTmSink tmSink;
    HashMap<Stream, Stream> streamMatrix = new HashMap<>();
    YarchDatabaseInstance ydb;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();

        Spec streamMatrixSpec = new Spec();
        streamMatrixSpec.addOption("inStream", OptionType.STRING);
        streamMatrixSpec.addOption("outStream", OptionType.STRING);

        spec.addOption("streamMatrix", OptionType.LIST).withSpec(streamMatrixSpec);
        // FIXME:
        // Add pus spec options
        return spec;
    }

    public PusTmManager(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;

        serviceConfig = config.getConfigOrEmpty("services");
        secondaryHeaderLength = config.getInt("secondaryHeaderLength", DEFAULT_SECONDARY_HEADER_LENGTH);
        absoluteTimeLength = config.getInt("absoluteTimeLength", DEFAULT_ABSOLUTE_TIME_LENGTH);
        PUS_HEADER_LENGTH = 7 + absoluteTimeLength;

        ydb = YarchDatabase.getInstance(yamcsInstance);

        if (!config.containsKey("streamMatrix"))
            throw new ConfigurationException(this.getClass() + ": streamMatrix needs to be defined to know the inputStream -> outStream mapping");

        List<Object> matrix = config.getList("streamMatrix");
        for(Object o : matrix) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, String> mp = (LinkedHashMap<String, String>) o;

            streamMatrix.put(
                Objects.requireNonNull(ydb.getStream(mp.get("inStream"))),
                Objects.requireNonNull(ydb.getStream(mp.get("outStream")))
            );
        }

        initializePUSServices();
        tmSink = new PusTmSink() {
            @Override
            public void processPusPacket(TmPacket tmPacket, Stream stream, String tmLinkName) {
                Long obt = tmPacket.getObt() == Long.MIN_VALUE ? null : tmPacket.getObt();

                Tuple t = new Tuple(StandardTupleDefinitions.TM,
                    new Object[] {
                        tmPacket.getGenerationTime(), tmPacket.getSeqCount(), tmPacket.getReceptionTime(), tmPacket.getStatus(), tmPacket.getPacket(), tmPacket.getEarthReceptionTime(), obt, tmLinkName, stream.getName()
                    }
                );
                stream.emitTuple(t);
            }   
        };
    }

    private void initializePUSServices() {
        pusServices.put(1, new ServiceOne(yamcsInstance, serviceConfig.getConfigOrEmpty("one")));
        pusServices.put(2, new ServiceTwo(yamcsInstance, serviceConfig.getConfigOrEmpty("two")));
        pusServices.put(3, new ServiceThree(yamcsInstance, serviceConfig.getConfigOrEmpty("three")));
        pusServices.put(5, new ServiceFive(yamcsInstance, serviceConfig.getConfigOrEmpty("five")));
    }

    public void acceptTmPacket(TmPacket tmPacket, String tmLinkName, Stream stream) {
        byte[] b = tmPacket.getPacket();
        ArrayList<TmPacket> pkts = pusServices.get(PusTmCcsdsPacket.getMessageType(b)).extractPusModifiers(tmPacket);

        if (pkts != null){
            for (TmPacket pkt: pkts) {
                tmSink.processPusPacket(pkt, stream, tmLinkName);
            }
        }
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        long rectime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
        long gentime = (Long) tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
        int seqCount = (Integer) tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
        byte[] pkt = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        String tmLinkName = (String) tuple.getColumn(StandardTupleDefinitions.TM_LINK_COLUMN);

        TmPacket tmPacket = new TmPacket(rectime, gentime, seqCount, pkt);
        Stream outStream = streamMatrix.get(stream);

        acceptTmPacket(tmPacket, tmLinkName, outStream);
    }

    @Override
    protected void doStart() {
        for (Map.Entry<Stream, Stream> streamMap : streamMatrix.entrySet()) {
            Stream inStream = streamMap.getKey();
            inStream.addSubscriber(this);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (Map.Entry<Stream, Stream> streamMap : streamMatrix.entrySet()) {
            Stream inStream = streamMap.getKey();
            inStream.removeSubscriber(this);
        }
        notifyStopped();
    }

}
