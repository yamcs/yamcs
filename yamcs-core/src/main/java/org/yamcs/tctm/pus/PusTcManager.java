package org.yamcs.tctm.pus;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSink;
import org.yamcs.tctm.pus.services.tc.fifteen.ServiceFifteen;
import org.yamcs.tctm.pus.services.tc.five.ServiceFive;
import org.yamcs.tctm.pus.services.tc.fourteen.ServiceFourteen;
import org.yamcs.tctm.pus.services.tc.six.ServiceSix;
import org.yamcs.tctm.pus.services.tc.two.ServiceTwo;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;
import org.yamcs.tctm.pus.services.tc.eleven.ServiceEleven;
import org.yamcs.tctm.pus.services.tc.nine.ServiceNine;
import org.yamcs.tctm.pus.services.tc.seventeen.ServiceSeventeen;
import org.yamcs.tctm.pus.services.tc.thirteen.ServiceThirteen;
import org.yamcs.tctm.pus.services.tc.three.ServiceThree;
import org.yamcs.tctm.pus.services.tc.twenty.ServiceTwenty;


public class PusTcManager extends AbstractYamcsService implements StreamSubscriber  {
    enum TimetagResolution {
        SECOND, MILLISECOND
    }

    Log log = new Log(PusTcManager.class);
    String yamcsInstance;

    // Static Members
    public static int DEFAULT_PRIMARY_HEADER_LENGTH = 6;
    public static int DEFAULT_SECONDARY_HEADER_LENGTH = 32;
    public static int DEFAULT_PUS_HEADER_LENGTH = 5;
    public static int DEFAULT_TIMETAG_BUFFER = 5; // Seconds
    public static int DEFAULT_TIMETAG_INDEX = DEFAULT_PRIMARY_HEADER_LENGTH + DEFAULT_PUS_HEADER_LENGTH;
    public static int DEFAULT_TIMETAG_LENGTH = 8;

    public static int secondaryHeaderLength;
    public static int sourceId;
    public static int secondaryHeaderSpareLength;
    public static int timetagBuffer;
    public static int timetagLength;

    Map<Integer, PusService> pusServices = new HashMap<>();
    YConfiguration pusConfig;
    HashMap<Stream, Stream> streamMatrix = new HashMap<>();
    YarchDatabaseInstance ydb;
    PusSink tcSink;
    XtceDb xtcedb;
    TimeService timeService;
    static TimetagResolution timetagResolution;

    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();
    protected ErrorDetectionWordCalculator errorDetectionCalculator;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();

        Spec streamMatrixSpec = new Spec();
        streamMatrixSpec.addOption("inStream", OptionType.STRING);
        streamMatrixSpec.addOption("outStream", OptionType.STRING);

        Spec crcType = new Spec();
        crcType.addOption("type", OptionType.STRING);

        spec.addOption("streamMatrix", OptionType.LIST).withElementType(OptionType.MAP).withSpec(streamMatrixSpec);
        spec.addOption("secondaryHeaderLength", OptionType.INTEGER);
        spec.addOption("sourceId", OptionType.INTEGER);
        spec.addOption("errorDetection", OptionType.MAP).withSpec(crcType);
        spec.addOption("timetagLength", OptionType.INTEGER);
        spec.addOption("timetagBuffer", OptionType.INTEGER);
        spec.addOption("timetagResolution", OptionType.STRING);
        spec.addOption("services", OptionType.MAP).withSpec(Spec.ANY);
        // FIXME:
        // Add pus spec options
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        this.yamcsInstance = yamcsInstance;

        pusConfig = config.getConfigOrEmpty("services");
        secondaryHeaderLength = config.getInt("secondaryHeaderLength", DEFAULT_SECONDARY_HEADER_LENGTH);
        sourceId = config.getInt("sourceId");
        timetagBuffer = config.getInt("timetagBuffer", DEFAULT_TIMETAG_BUFFER);
        timetagResolution = config.getEnum("timetagResolution", TimetagResolution.class, TimetagResolution.SECOND);
        timetagLength = config.getInt("timetagLength", DEFAULT_TIMETAG_LENGTH);
        secondaryHeaderSpareLength = secondaryHeaderLength - DEFAULT_PUS_HEADER_LENGTH;

        ydb = YarchDatabase.getInstance(yamcsInstance);
        xtcedb = MdbFactory.getInstance(yamcsInstance);
        timeService = YamcsServer.getTimeService(yamcsInstance);
        errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);

        if (!config.containsKey("streamMatrix"))
            throw new ConfigurationException(this.getClass() + ": streamMatrix needs to be defined to know the inputStream -> outStream mapping");

        for(YConfiguration c: config.getConfigList("streamMatrix")) {
            String inStream = c.getString("inStream");
            String outStream = c.getString("outStream");

            streamMatrix.put(
                Objects.requireNonNull(ydb.getStream(inStream)),
                Objects.requireNonNull(ydb.getStream(outStream))
            );
        }
        tcSink = new PusSink() {
            @Override
            public void emitTmTuple(TmPacket tmPacket, Stream stream, String tmLinkName) {
                throw new UnsupportedOperationException("Unimplemented method 'emitTmTuple'");
            }
            @Override
            public void emitTcTuple(PreparedCommand pc, Stream stream) {
                stream.emitTuple(pc.toTuple());
            }
        };

        initializePUSServices();
    }

    public void insertCrcAndCcsdsSeqCount(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        boolean hasCrc = hasCrc(pc);

        if (hasCrc) { // 2 extra bytes for the checkword
            // Fixme: Does the spare field (the one outside the secondary header) need to be added?
            binary = Arrays.copyOf(binary, binary.length + 2);
        }

        ByteBuffer bb = ByteBuffer.wrap(binary);
        bb.putShort(4, (short) (binary.length - 7)); // write packet length
        seqFiller.fill(binary); //write sequence count
        pc.setBinary(binary);

        if (hasCrc) {
            int pos = binary.length - 2;
            try {
                int checkword = errorDetectionCalculator.compute(binary, 0, pos);
                log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
                bb.putShort(pos, (short) checkword);
                pc.setBinary(bb.array());

            } catch (IllegalArgumentException e) {
                log.warn("Error when computing checkword: " + e.getMessage());
            }
        }
    }

    private boolean hasCrc(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        boolean secHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(binary);
        if (secHeaderFlag) {
            return (errorDetectionCalculator != null);
        } else {
            return false;
        }
    }

    private void initializePUSServices() {
        pusServices.put(2, new ServiceTwo(yamcsInstance, pusConfig.getConfigOrEmpty("two")));
        pusServices.put(3, new ServiceThree(yamcsInstance, pusConfig.getConfigOrEmpty("three")));
        pusServices.put(5, new ServiceFive(yamcsInstance, pusConfig.getConfigOrEmpty("five")));
        pusServices.put(6, new ServiceSix(yamcsInstance, pusConfig.getConfigOrEmpty("six")));
        pusServices.put(9, new ServiceNine(yamcsInstance, pusConfig.getConfigOrEmpty("nine")));
        pusServices.put(11, new ServiceEleven(yamcsInstance, pusConfig.getConfigOrEmpty("eleven")));
        pusServices.put(13, new ServiceThirteen(yamcsInstance, pusConfig.getConfigOrEmpty("thirteen")));
        pusServices.put(14, new ServiceFourteen(yamcsInstance, pusConfig.getConfigOrEmpty("fourteen")));
        pusServices.put(15, new ServiceFifteen(yamcsInstance, pusConfig.getConfigOrEmpty("fifteen")));
        pusServices.put(17, new ServiceSeventeen(yamcsInstance, pusConfig.getConfigOrEmpty("seventeen")));
        pusServices.put(20, new ServiceTwenty(yamcsInstance, pusConfig.getConfigOrEmpty("twenty")));
    }

    public static boolean timetagSanityCheck(long timetag) {
        if (timetag == 0)
            return true;

        if (timetag < 0)
            return false;

        // If timetagResolution is in seconds, convert to milliseconds
        if (timetagResolution == TimetagResolution.SECOND)
            timetag *= 1000;

        return !Instant.now()
                .plusSeconds(timetagBuffer)
                .atZone(ZoneId.of("GMT"))
                .isAfter(
                    Instant.ofEpochMilli(timetag)
                            .atZone(ZoneId.of("GMT"))
                );
    }

    public void addPusModifiers(PreparedCommand telecommand, Stream stream) {
        PreparedCommand pc = pusServices.get(PusTcCcsdsPacket.getMessageType(telecommand)).addPusModifiers(telecommand);
        insertCrcAndCcsdsSeqCount(pc);

        long timetag = pc.getTimestampAttribute(CommandHistoryPublisher.Timetag_KEY);
        if (PusTcManager.timetagSanityCheck(timetag) && timetag != 0) {
            byte[] pbinary = pc.getBinary();

            pc.setAttribute(CommandHistoryPublisher.Timetagged_CommandApid_KEY, seqFiller.getApid(pbinary));
            pc.setAttribute(CommandHistoryPublisher.Timetagged_CommandCcsdsSeq_KEY, seqFiller.getCcsdsSeqCount(pbinary));
            pc.setAttribute(CommandHistoryPublisher.Timetagged_Command_KEY, pbinary);

            pc = addTimetagModifiers(pc);
            insertCrcAndCcsdsSeqCount(pc);
        }

        if (pc != null) {
            tcSink.emitTcTuple(pc, stream);
        }
    }

    public PreparedCommand addTimetagModifiers(PreparedCommand telecommand) {
        ServiceEleven serviceEleven = (ServiceEleven) pusServices.get(11);
        return serviceEleven.addTimetagModifiers(telecommand);
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        PreparedCommand pc = PreparedCommand.fromTuple(tuple, xtcedb);
        Stream outStream = streamMatrix.get(stream);

        addPusModifiers(pc, outStream);
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
