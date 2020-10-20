package org.yamcs.time;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.external.SimpleRegression;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.ResultListener;
import org.yamcs.yarch.streamsql.StreamSqlException;
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
 * In addition it takes two other parameters:
 * <ul>
 * <li>onboardDelay - configurable in the service configuration. It covers any delay happening on-board (sampling time,
 * radiation time)
 * <li>tof - time of flight - the time it takes for the signal to reach the ground. This is computed by the
 * {@link TimeOfFlightEstimator} and can be fixed value or dynamically interpolated from data provided by a flight
 * dynamics system.</li>
 * </ul>
 * <p>
 * Computes {@code m} - gradient and {@code c} - offset such that
 * <p>
 * {@code ob_time = m*obt + c}
 * <p>
 * The determination of the gradient and offset is done using the least squares method.
 * <p>
 * The number of samples used for computing the coefficients is configurable and has to be minimum 2.
 * <p>
 * <b>accuracy and validity</b>
 * Once the coefficients have been calculated, for each new sample received a deviation is calculated as the delta
 * between the OBT calculated using the coefficients and the OBT which is part of the sample (after adjusting for
 * delays). The deviation is compared with the accuracy and validity parameters:
 * <p>
 * If the deviation is greater than {@code accuracy} but smaller than {@code validity}, then a recalculation of the
 * coefficients is performed based on the last received samples.
 * <p>
 * If the deviation is greater than {@code validity} then the coefficients are declared as invalid and all the samples
 * from the buffer except the last one are dropped. The time returned by {@link #getTime(long)} will be invalid until
 * the required number of new samples is received and the next recalculation is performed
 * 
 * <p>
 * The service keeps track of multiple such intervals corresponding to different on-board time resets. At Yamcs startup
 * the service loads a list of intervals from the tco table.
 *
 * <p>
 * To use this service there will be typically one component which adds samples using the
 * {@link #addSample(long, Instant)} each time it
 * receives a correlation sample from on-board. How the on-board system will send such samples is mission specific (for
 * example the PUS protocol defines some specific time packets for this purpose).
 * <p>
 * In addition there will be other components (preprocessors or other services) which can use the class to get a Yamcs
 * time associated to a specific OBT.
 * 
 * 
 * <p>
 * This class is thread safe: the synchronised methods {@link #addSample} and {@link #reset} are the only one where the
 * state is changed and thus the {@link #getTime(long)} can be used
 * from multiple threads concurrently.
 * 
 * @author nm
 *
 */
public class TimeCorrelationService extends AbstractYamcsService {
    static public final String TABLE_NAME = "tco";
    static public final String DEFAULT_CLOCK_NAME = "clk0";
    static public final int MAX_HISTCOEF = 1000;

    public static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("obclk", DataType.ENUM);
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
     * Name of the on-board clock.
     * <p>
     * In case there are multiple clocks, we can have multiple instances of this service,
     * one for each clock.
     */
    String clockName;

    Stream tcoStream;
    EventProducer eventProducer;

    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);
        onboardDelay = config.getDouble("onboardDelay", 0) * 0.001;
        numSamples = config.getInt("numSamples", 5);
        sampleQueue = new ArrayDeque<>(numSamples);
        clockName = config.getString("clockName", DEFAULT_CLOCK_NAME);
        accuracy = config.getDouble("accuracy", 100) * 0.001;
        validity = config.getDouble("validity", 200) * 0.001;

        YConfiguration tofConfig = config.getConfigOrEmpty("tof");
        tofEstimator = new TimeOfFlightEstimator(tofConfig);
        boolean saveCoefficients = config.getBoolean("saveCoefficients", true);

        
        if (saveCoefficients) {
            String streamName = TABLE_NAME + "_" + clockName;
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            try {
                if (ydb.getTable(TABLE_NAME) == null) {
                    String query = "create table " + TABLE_NAME + "(" + TDEF.getStringDefinition1()
                            + ", primary key(obclk, obi0))";
                    ydb.execute(query);
                } else {
                    retrieveArchivedCoefficients(ydb);
                }
                if (ydb.getStream(streamName) == null) {
                    ydb.execute("create stream " + streamName + TDEF.getStringDefinition());
                }
                ydb.execute("insert into " + TABLE_NAME + " select * from " + streamName);
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
            Semaphore sema = new Semaphore(0);
            StreamSqlStatement stmt = ydb.createStatement(
                    "select * from " + TABLE_NAME + " where obclk=? order desc limit " + MAX_HISTCOEF,
                    clockName);
            ydb.execute(stmt, new ResultListener() {

                @Override
                public void next(Tuple tuple) {
                    tmpl.add(TcoCoefficients.fromTuple(tuple));
                }

                @Override
                public void completeExceptionally(Throwable t) {
                }

                @Override
                public void complete() {
                    sema.release();
                }
            });
            sema.acquire();
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
        double delay = tofEstimator.getTof(ert) + onboardDelay;
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
            sampleQueue.removeFirst();
            sampleQueue.addLast(s);
            // verify accuracy
            Instant obi1 = curCoefficients.getInstant(obt);
            double dev = Math.abs(obi1.deltaFrom(obi));
            if (dev > validity) {
                eventProducer.sendWarning(String.format("Deviation %f (ms) "
                        + "greater than the allowed validity %f (ms), reseting correlation", dev * 1000,
                        validity * 1000));
                curCoefficients = null;
                sampleQueue.clear();
                sampleQueue.addLast(s);
            } else if (dev > accuracy) {
                eventProducer.sendInfo(String.format("Deviation %f (ms) "
                        + "greater than the allowed accuracy %f, recomputing coefficients", dev * 1000,
                        accuracy * 1000));
                computeCoefficients();
            }
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

    /**
     * Returns the name of the clock.
     * <p>
     * In case there are multiple on-board clocks, there can be different instances of this service, each with its own
     * clock.
     * 
     * @return
     */
    public String getClockName() {
        return clockName;
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
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
            tcoStream.emitTuple(c.toTuple(clockName));
        }
        curCoefficients = c;
        coefHist.add(0, c);
        if (coefHist.size() > MAX_HISTCOEF) {
            coefHist.remove(coefHist.size() - 1);
        }

        eventProducer.sendInfo("Computed new coefficients: " + c);
    }

    static class TcoCoefficients {
        @Override
        public String toString() {
            return "TcoCoefficients [obi0=" + obi0 + ", obt0=" + obt0 + ", gradient=" + gradient + ", offset=" + offset
                    + "]";
        }

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

        Tuple toTuple(String clockName) {
            Tuple t = new Tuple(TDEF, Arrays.asList(clockName, obi0, obt0, gradient, offset));
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
    }

    static class Sample {
        final long obt;
        final Instant obi;

        public Sample(long obt, Instant obi) {
            this.obt = obt;
            this.obi = obi;
        }
    }
}
