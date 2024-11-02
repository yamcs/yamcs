package org.yamcs.time;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.Spec.OptionType;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.external.SimpleRegression;
import org.yamcs.parameter.ParameterStatus;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.TcoConfig;
import org.yamcs.protobuf.TcoSample;
import org.yamcs.protobuf.TcoStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.xtce.SystemParameter;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

/**
 * On-board time correlation service (inspired from SCOS2K (ESA MCS)).
 * <p>
 * It receives samples {@code (obt, ert, tof)} where:
 * <ul>
 * <li>obt - onboard time considered to be a counter starting at 0 when an on-board computer starts. This service uses
 * an 64bits integer.</li>
 * <li>ert - Earth Reception Time - the timestamp when the signal has been received on the ground - it is typically
 * provided by a ground station. This service expects the time to be provided in the {@link Instant} format which has
 * picoseconds resolution.</li>
 * </ul>
 * 
 * In addition it takes two other parameters:
 * 
 * <ul>
 * <li>onboardDelay - configurable in the service configuration. It covers any delay happening on-board (sampling time,
 * radiation time)
 * <li>tof - time of flight - the time it takes for the signal to reach the ground.</li>
 * </ul>
 * 
 * The time of flight can be fixed or computed by the {@link TimeOfFlightEstimator} by dynamically interpolating from
 * data provided by a flight dynamics system.
 * <p>
 * Computes {@code m} = gradient and {@code c} = offset such that
 * <p>
 * {@code ob_time = m*obt + c}
 * <p>
 * The determination of the gradient and offset is done using the least squares method.
 * <p>
 * The number of samples used for computing the coefficients is configurable and has to be minimum 2.
 * <h2>Accuracy and validity</h2> Once the coefficients have been calculated, for each new sample received a deviation
 * is calculated as the delta between the OBT computed using the coefficients and the OBT which is part of the sample
 * (after adjusting for delays). The deviation is compared with the accuracy and validity parameters:
 *
 * <ul>
 * <li>If the deviation is greater than {@code accuracy} but smaller than {@code validity}, then a recalculation of the
 * coefficients is performed based on the last received samples.</li>
 *
 * <li>If the deviation is greater than {@code validity} then the coefficients are declared as invalid and all the
 * samples from the buffer except the last one are dropped. The time returned by {@link #getTime(long)} will be invalid
 * until the required number of new samples is received and the next recalculation is performed</li>
 * </ul>
 * 
 * <h2>Historical coefficients</h2> The service keeps track of multiple intervals corresponding to different on-board
 * time resets. At Yamcs startup the service loads a list of intervals from the tco table.
 * <p>
 * If using the historical recording to insert some old data into the Yamcs, in order to get the correct coefficients
 * one has to know the approximate time when the data has been generated.
 *
 * <h2>Verify Only Mode</h2> If the on-board clock is synchronized via a different method, this service can still be
 * used to verify the synchronization.
 *
 * <p>
 * The method {@link #verify} will check the difference between the packet generation time and the expected generation
 * time (using ert - delays) and in case the difference is greater than the validity, the packet will be changed with
 * the local computed time and the flag {@link TmPacket#setLocalGenTimeFlag()} will also be set.
 * 
 * <h2>Usage</h2>
 * 
 * <p>
 * To use this service there will be typically one component which adds samples using the
 * {@link #addSample(long, Instant)} each time it receives a correlation sample from on-board. How the on-board system
 * will send such samples is mission specific (for example the PUS protocol defines some specific time packets for this
 * purpose).
 * <p>
 * In addition there will be other components (preprocessors or other services) which can use the class to get a Yamcs
 * time associated to a specific OBT.
 * 
 * 
 * <p>
 * This class is thread safe: the synchronised methods {@link #addSample} and {@link #reset} are the only one where the
 * state is changed and thus the {@link #getTime(long)} can be used from multiple threads concurrently.
 * 
 */
public class TimeCorrelationService extends AbstractYamcsService implements SystemParametersProducer {
    static public final String TABLE_NAME = "tco_";
    static public final int MAX_HISTCOEF = 1000;

    public static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("obi0", DataType.HRES_TIMESTAMP);
        TDEF.addColumn("obt0", DataType.LONG);
        TDEF.addColumn("gradient", DataType.DOUBLE);
        TDEF.addColumn("offset", DataType.DOUBLE);
    }
    volatile TcoCoefficients curCoefficients;

    TimeOfFlightEstimator tofEstimator;
    List<TcoCoefficients> coefHist = new CopyOnWriteArrayList<>();

    SimpleRegression sg;

    ArrayDeque<Sample> sampleQueue;
    TimeService timeService;
    ParameterStatus nominalStatus, watchStatus, warningStatus;

    /**
     * how long (in seconds) it takes to sample the clock on-board, pack the data in the frame, etc.
     */
    double onboardDelay;

    double accuracy;
    double validity;
    /**
     * how many samples are used for the regression
     */
    int numSamples;

    /**
     * default time of flight, used if cannot be obtained from the tofEstimator
     */
    double defaultTof;

    /**
     * last computed deviation
     */
    volatile double lastDeviation = Double.NaN;

    /**
     * Last time when the coefficients have been computed
     */
    long coefficientsTime = TimeEncoding.INVALID_INSTANT;

    Stream tcoStream;
    EventProducer eventProducer;
    private SystemParameter spDeviation;
    private ParameterValue deviationPv;
    String tableName;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("onboardDelay", OptionType.FLOAT).withDefault(0.0);
        spec.addOption("numSamples", OptionType.INTEGER).withDefault(3);
        spec.addOption("accuracy", OptionType.FLOAT).withDefault(0.1);
        spec.addOption("validity", OptionType.FLOAT).withDefault(0.2);
        spec.addOption("saveCoefficients", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("saveTofPolynomials", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("defaultTof", OptionType.FLOAT).withDefault(0.0);
        spec.addOption("useTofEstimator", OptionType.BOOLEAN).withDefault(false);

        return spec;
    }

    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        onboardDelay = config.getDouble("onboardDelay", 0);
        numSamples = config.getInt("numSamples", 3);
        sampleQueue = new ArrayDeque<>(numSamples);
        accuracy = config.getDouble("accuracy", 0.1);
        validity = config.getDouble("validity", 0.2);
        defaultTof = config.getDouble("defaultTof", 0.0);

        boolean saveCoefficients = config.getBoolean("saveCoefficients", true);
        boolean saveTofPolynomials = config.getBoolean("saveTofPolynomials", true);
        boolean useTofEstimator = config.getBoolean("useTofEstimator", false);

        if (useTofEstimator) {
            tofEstimator = new TimeOfFlightEstimator(yamcsInstance, serviceName, saveTofPolynomials);
        } else {
            tofEstimator = null;
        }

        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        if (saveCoefficients) {
            tableName = TABLE_NAME + serviceName;

            String streamName = tableName + "_in";
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            try {
                if (ydb.getTable(tableName) == null) {
                    String query = "create table " + tableName + "(" + TDEF.getStringDefinition1()
                            + ", primary key(obi0))";
                    ydb.execute(query);
                } else {
                    retrieveArchivedCoefficients(ydb);
                }
                if (ydb.getStream(streamName) == null) {
                    ydb.execute("create stream " + streamName + TDEF.getStringDefinition());
                }
                ydb.execute("insert into " + tableName + " select * from " + streamName);
            } catch (ParseException | StreamSqlException e) {
                throw new InitException(e);
            }
            tcoStream = ydb.getStream(streamName);
        } else {
            tcoStream = null;
        }
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getName(), 10000);
    }

    private void retrieveArchivedCoefficients(YarchDatabaseInstance ydb) throws InitException {
        try {
            // we add them to a temporary list because CopyOnWriteArrayList is not very efficient at adding one by one
            List<TcoCoefficients> tmpl = new ArrayList<>();
            StreamSqlStatement stmt = ydb.createStatement(
                    "select * from " + tableName + " order desc limit " + MAX_HISTCOEF,
                    serviceName);

            StreamSqlResult res = ydb.execute(stmt);
            while (res.hasNext()) {
                tmpl.add(TcoCoefficients.fromTuple(res.next()));
            }
            res.close();
            if (!tmpl.isEmpty()) {
                curCoefficients = tmpl.get(0);
            }
            coefHist.addAll(tmpl);
        } catch (Exception e) {
            throw new InitException(e);
        }

    }

    /**
     * 
     * Add a time synchronisation sample.
     * <p>
     * If the coefficients are already computed, the sample will be used to asses the accuracy of the computation. If
     * the accuracy is lower than {@link #accuracy}, the coefficients will be recomputed based on the latest samples.
     * <p>
     * If the coefficients are not yet computed, the sample will be simply added to the sample buffer. If there are
     * enough samples in the buffer, the coefficients will be calculated.
     * 
     * 
     * @param obt
     *            - On-Board Time
     * @param ert
     *            - Earth Reception Time
     * @throws IllegalArgumentException
     *             if the obt or ert are smaller than the ones in the previous sample
     */
    public synchronized void addSample(long obt, Instant ert) {
        double delay = getTof(ert) + onboardDelay;
        Instant obi = ert.plus(-delay);

        if (!sampleQueue.isEmpty()) {
            Sample last = sampleQueue.getLast();
            if (obt < last.obt || obi.compareTo(last.obi) < 0) {
                throw new IllegalArgumentException("Sample (" + obt + ", " + obi + ") is preceeding the previous one ("
                        + last.obt + ", " + last.obi + ")");
            }
        }
        Sample s = new Sample(obt, obi);

        if (curCoefficients == null) {
            sampleQueue.addLast(s);
            if (sampleQueue.size() == numSamples) {
                computeCoefficients();
            }
        } else {// state=SYNC
            if (sampleQueue.size() >= numSamples) {
                sampleQueue.removeFirst();
            }
            sampleQueue.addLast(s);
            // verify accuracy
            Instant obi1 = curCoefficients.getInstant(obt);
            double deviation = obi1.deltaFrom(obi);
            double dev = Math.abs(deviation);

            if (dev > validity) {
                eventProducer.sendWarning(String.format("Deviation %f sec "
                        + "greater than the allowed validity %f sec, reseting correlation", dev,
                        validity));
                curCoefficients = null;
                sampleQueue.clear();
                sampleQueue.addLast(s);
            } else if (dev > accuracy) {
                eventProducer.sendInfo(String.format("Deviation %f sec "
                        + "greater than the allowed accuracy %f sec, recomputing coefficients", dev,
                        accuracy));
                computeCoefficients();
            }
            publishDeviation(deviation);
        }
    }

    /**
     * Forgets about the computed coefficients and the stored tuples.
     * <p>
     * Should be called when the on-board clock resets.
     */
    public synchronized void reset() {
        curCoefficients = null;
        sampleQueue.clear();
        lastDeviation = Double.NaN;
    }

    /**
     * 
     * Returns the time when the on-board clock had the given value. If the coefficients are not computed yet, it will
     * return {@link Instant#INVALID_INSTANT}
     * 
     * @param obt
     * @return the time instant corresponding to the on-board obt tick.
     * 
     */
    public Instant getTime(long obt) {
        TcoCoefficients c = curCoefficients;

        if (c == null) {
            return Instant.INVALID_INSTANT;
        } else {
            return c.getInstant(obt);
        }
    }

    /**
     * Returns the on-board time corresponding to the given on-board clock
     * <p>
     * If the coefficients are not computed yet, it will return Long.MIN_VALUE
     * 
     * @param scheduleTime
     * @return
     */
    public long getObt(long scheduleTime) {
        TcoCoefficients c = curCoefficients;

        if (c == null) {
            return Long.MIN_VALUE;
        } else {
            return c.getObt(Instant.get(scheduleTime));
        }
    }

    /**
     * Set the generation time of the packet based on the computed coefficients.
     * <p>
     * If the coefficients are not valid, set the generation time to gentime = ert-delays and also set the flag
     * {@link TmPacket#setLocalGenTimeFlag()}
     * 
     * <p>
     * The packet has to have the ert set, otherwise an exception is thrown
     * 
     * @param obt
     * @param pkt
     * @throws IllegalArgumentException
     *             if the packet has no ert set
     */
    public void timestamp(long obt, TmPacket pkt) {
        Instant ert = pkt.getEarthReceptionTime();

        if (ert == null) {
            throw new IllegalArgumentException("no ert available");
        }

        TcoCoefficients c = curCoefficients;
        if (c == null) {
            double delay = getTof(ert) + onboardDelay;
            Instant genTime = ert.plus(-delay);
            pkt.setGenerationTime(genTime.getMillis());
            pkt.setLocalGenTimeFlag();
        } else {
            Instant genTime = c.getInstant(obt);
            pkt.setGenerationTime(genTime.getMillis());
        }
    }

    double getTof(Instant ert) {
        if (tofEstimator == null) {
            return defaultTof;
        }
        double d = tofEstimator.getTof(ert);
        if (Double.isNaN(d)) {
            return defaultTof;
        } else {
            return d;
        }
    }

    /**
     * Verify the time synchronization of the packet. This assumes that the packet generation time has already been
     * computed (by a packet pre-processor).
     * <p>
     * If the deviation between the provided generation time and the expected generation time (computed based on the ert
     * - delays) is greater than the validity threshold, the generation time is changed to the expected time and the
     * {@link TmPacket#setLocalGenTimeFlag()} is also set.
     * <p>
     * The computed deviation is also published as a processed parameter.
     * 
     * @param pkt
     * @throws IllegalArgumentException
     *             if the packet has no ert set
     */
    public void verify(TmPacket pkt) {

        Instant ert = pkt.getEarthReceptionTime();

        if (ert == null || ert == Instant.INVALID_INSTANT) {
            throw new IllegalArgumentException("no ert available");
        }
        double delay = getTof(ert) + onboardDelay;
        Instant expectedGenTime = ert.plus(-delay);

        double deviation = expectedGenTime.deltaFrom(Instant.get(pkt.getGenerationTime()));
        if (Math.abs(deviation) > validity) {
            pkt.setGenerationTime(expectedGenTime.getMillis());
            pkt.setLocalGenTimeFlag();
        }

        publishDeviation(deviation);
    }

    /**
     * Returns the time when the on-board clock had the given value. obi is an approximative time used to search in
     * history for the coefficients applicable at that time.
     * <p>
     * Returns {@link Instant#INVALID_INSTANT} if no historical coefficients are found.
     * 
     * @param obi
     * @param obt
     * @return
     */
    public Instant getHistoricalTime(Instant obi, long obt) {
        for (TcoCoefficients tc : coefHist) {
            if (tc.obi0.compareTo(obi) <= 0) {
                return tc.getInstant(obt);
            }
        }
        return Instant.INVALID_INSTANT;
    }

    private void publishDeviation(double deviation) {
        lastDeviation = deviation;

        if (spDeviation == null) {
            return;// no system parameter collector configured
        }
        long time = timeService.getMissionTime();
        double dabs = Math.abs(deviation);
        ParameterValue pv = SystemParametersService.getPV(spDeviation, time, deviation);
        if (dabs > validity) {
            pv.setStatus(warningStatus);
        } else if (dabs > accuracy) {
            pv.setStatus(watchStatus);
        } else {
            pv.setStatus(nominalStatus);
        }
        deviationPv = pv;
    }

    @Override
    protected void doStart() {
        setupSystemParameters();
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    public TimeOfFlightEstimator getTofEstimator() {
        return tofEstimator;
    }

    private void computeCoefficients() {
        TcoCoefficients c = new TcoCoefficients();
        Sample s0 = sampleQueue.getFirst();
        c.obi0 = s0.obi;
        c.obt0 = s0.obt;
        SimpleRegression sr = new SimpleRegression();
        for (Sample s : sampleQueue) {
            sr.addData(s.obt - c.obt0, s.obi.deltaFrom(c.obi0));
        }
        c.gradient = sr.getSlope();
        c.offset = sr.getIntercept();
        if (tcoStream != null) {
            tcoStream.emitTuple(c.toTuple());
        }
        curCoefficients = c;
        coefHist.add(0, c);
        if (coefHist.size() > MAX_HISTCOEF) {
            coefHist.remove(coefHist.size() - 1);
        }

        eventProducer.sendInfo("Computed new coefficients: " + c);
    }

    void setupSystemParameters() {
        SystemParametersService collector = SystemParametersService.getInstance(yamcsInstance);
        if (collector != null) {
            makeParameterStatus();
            spDeviation = collector.createSystemParameter(serviceName + "/deviation", Type.DOUBLE, new UnitType("sec"),
                    "delta between the OBT computed using the coefficients and the OBT which is part of a time sample"
                            + " (after adjusting for delays)");
            collector.registerProducer(this);
        }
    }

    @Override
    public List<ParameterValue> getSystemParameters(long gentime) {
        if (deviationPv != null) {
            return Arrays.asList(deviationPv);
        } else {
            return Collections.emptyList();
        }
    }

    private void makeParameterStatus() {
        nominalStatus = getParaStatus();
        nominalStatus.setMonitoringResult(MonitoringResult.IN_LIMITS);
        watchStatus = getParaStatus();
        watchStatus.setMonitoringResult(MonitoringResult.WATCH);
        warningStatus = getParaStatus();
        warningStatus.setMonitoringResult(MonitoringResult.WARNING);
    }

    private ParameterStatus getParaStatus() {
        ParameterStatus status = new ParameterStatus();
        status.setWatchRange(new DoubleRange(-accuracy, accuracy));
        status.setWarningRange(new DoubleRange(-validity, validity));

        return status;
    }

    public synchronized void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public synchronized void setValidity(double validity) {
        this.validity = validity;
    }

    public synchronized void setOnboardDelay(double onboardDelay) {
        this.onboardDelay = onboardDelay;
    }

    public synchronized void setDefaultTof(double defaultTof) {
        this.defaultTof = defaultTof;
    }

    public synchronized void forceCoefficients(Instant obi, long obt, double offset, double gradient) {
        TcoCoefficients tcoef = new TcoCoefficients();
        tcoef.obt0 = obt;
        tcoef.obi0 = obi;
        tcoef.offset = offset;
        tcoef.gradient = gradient;
        curCoefficients = tcoef;
        coefficientsTime = timeService.getMissionTime();
    }

    static class TcoCoefficients {
        Instant obi0;
        long obt0;
        double gradient;
        double offset;

        /**
         * Returns the Yamcs Instant corresponding to the given obt
         * 
         * @param obt
         * @return
         */
        Instant getInstant(long obt) {
            return obi0.plus(gradient * (obt - obt0) + offset);
        }

        /**
         * Returns the obt corresponding to the given Yamcs instant
         */
        long getObt(Instant instant) {
            return (long) ((instant.deltaFrom(obi0) - offset) / gradient) + obt0;
        }

        Tuple toTuple() {
            Tuple t = new Tuple(TDEF, Arrays.asList(obi0, obt0, gradient, offset));
            return t;
        }

        static TcoCoefficients fromTuple(Tuple t) {
            TcoCoefficients c = new TcoCoefficients();
            c.obi0 = (Instant) t.getColumn("obi0");
            c.obt0 = (Long) t.getColumn("obt0");
            c.gradient = (Double) t.getColumn("gradient");
            c.offset = (Double) t.getColumn("offset");

            return c;
        }

        @Override
        public String toString() {
            return "TcoCoefficients [obi0=" + obi0 + ", obt0=" + obt0 + ", gradient=" + gradient + ", offset=" + offset
                    + "]";
        }

        public double getOffset() {
            return offset;
        }
    }

    static class Sample {
        final long obt;
        final Instant obi;

        public Sample(long obt, Instant obi) {
            this.obt = obt;
            this.obi = obi;
        }
    }

    public synchronized TcoConfig getTcoConfig() {
        TcoConfig.Builder tcb = TcoConfig.newBuilder();
        tcb.setAccuracy(accuracy).setValidity(validity).setOnboardDelay(onboardDelay).setDefaultTof(defaultTof);
        return tcb.build();
    }

    public synchronized TcoStatus getStatus() {
        TcoStatus.Builder status = TcoStatus.newBuilder();
        double d = lastDeviation;
        if (!Double.isNaN(d)) {
            status.setDeviation(d);
        }

        TcoCoefficients c = curCoefficients;
        if (c != null) {
            status.setCoefficients(toProto(c));
        }

        long ct = coefficientsTime;
        if (ct != TimeEncoding.INVALID_INSTANT) {
            status.setCoefficientsTime(TimeEncoding.toProtobufTimestamp(ct));
        }

        for (Sample sample : sampleQueue) {
            status.addSamples(toProto(sample));
        }
        return status.build();
    }

    private TcoSample toProto(Sample sample) {
        return TcoSample.newBuilder().setObt(sample.obt).setUtc(TimeEncoding.toProtobufTimestamp(sample.obi)).build();
    }

    private org.yamcs.protobuf.TcoCoefficients toProto(TcoCoefficients c) {
        org.yamcs.protobuf.TcoCoefficients.Builder tcb = org.yamcs.protobuf.TcoCoefficients.newBuilder();
        tcb.setOffset(c.offset).setGradient(c.gradient)
                .setUtc(TimeEncoding.toProtobufTimestamp(c.obi0)).setObt(c.obt0);

        return tcb.build();
    }


}
