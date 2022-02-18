package org.yamcs.time;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.InitException;
import org.yamcs.YamcsServer;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

/**
 * Time of flight estimator.
 * <p>
 * It returns time of flight between a spacecraft and a ground antenna by interpolating using user defined spline
 * polynomials.
 * 
 * @author nm
 *
 */
public class TimeOfFlightEstimator {
    // currently we only support one antenna, in the future we may have one object of this class for each antenna
    final static String DEFAULT_ANTENNA = "gs1";
    final static String TABLE_NAME = "tof_";

    public static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("ertStart", DataType.HRES_TIMESTAMP);
        TDEF.addColumn("ertStop", DataType.HRES_TIMESTAMP);
        TDEF.addColumn("polCoef", DataType.BINARY);
    }

    CopyOnWriteArrayList<TofInterval> calibIntervals = new CopyOnWriteArrayList<>();
    final String clockName;
    final String antenna = DEFAULT_ANTENNA;
    final boolean savePolynomials;
    final String yamcsInstance;
    final Stream tofStream;
    String tableName;

    public TimeOfFlightEstimator(String yamcsInstance, String clockName, boolean savePolynomials) throws InitException {
        this.clockName = clockName;
        this.savePolynomials = savePolynomials;
        this.yamcsInstance = yamcsInstance;

        if (savePolynomials) {
            tableName = TABLE_NAME + clockName;

            String streamName = tableName + "_in";
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            try {
                if (ydb.getTable(tableName) == null) {
                    String query = "create table " + tableName + "(" + TDEF.getStringDefinition1()
                            + ", primary key(ertStart, ertStop))";
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
            tofStream = ydb.getStream(streamName);
        } else {
            tofStream = null;
        }

    }

    private void retrieveArchivedCoefficients(YarchDatabaseInstance ydb) throws InitException {
        TimeService timeService = YamcsServer.getTimeService(ydb.getName());
        long now = timeService.getMissionTime();
        try {
            List<TofInterval> tmpl = new ArrayList<>();
            StreamSqlResult res = ydb.execute(
                    "select * from " + tableName + " where ertStart>? order desc ", now);
            while (res.hasNext()) {
                tmpl.add(TofInterval.fromTuple(res.next()));
            }

            calibIntervals.addAll(tmpl);
        } catch (Exception e) {
            throw new InitException(e);
        }
    }

    /**
     * Returns time of flight of the signal received at ert at the ground station
     * 
     * @param ert
     * @return time of flight in seconds or NaN if it cannot be computed
     */
    public double getTof(Instant ert) {
        // this iteration assumes that it is more likely to find the wanted ert in front of the list
        for (TofInterval ti : calibIntervals) {
            if (ti.ertStart.compareTo(ert) <= 0 && ti.ertStop.compareTo(ert) > 0) {
                return ti.getTof(ert);
            }
        }

        return Double.NaN;
    }

    public void addInterval(Instant ertStart, Instant ertStop, double[] polCoefficients) {
        TofInterval ti = new TofInterval(ertStart, ertStop, polCoefficients);
        calibIntervals.add(ti);
        calibIntervals.sort(new IntervalComparator());
        if (tofStream != null) {
            tofStream.emitTuple(ti.toTuple());
        }
    }

    public void addIntervals(Collection<TofInterval> intervals) {
        calibIntervals.addAll(intervals);
        calibIntervals.sort(new IntervalComparator());
        if (tofStream != null) {
            for (TofInterval ti : intervals) {
                tofStream.emitTuple(ti.toTuple());
            }
        }
    }

    class IntervalComparator implements Comparator<TofInterval> {
        @Override
        public int compare(TofInterval p1, TofInterval p2) {
            int c = p2.ertStart.compareTo(p1.ertStart);
            if (c == 0) {// in case of intervals with the same start, we put the shorter one in front
                return p1.ertStop.compareTo(p2.ertStop);
            } else {
                return c;
            }
        }
    }

    public void deleteSplineIntervals(Instant start, Instant stop) {
        List<TofInterval> tiList = new ArrayList<>();
        for (TofInterval ti : calibIntervals) {
            Instant d = ti.ertStart;
            if (d.compareTo(start) >= 0 && d.compareTo(stop) <= 0) {
                tiList.add(ti);
            }
        }
        calibIntervals.removeAll(tiList);
    }

    /**
     * Used for polynomial interpolation of time of flight based on ERT.
     * <p>
     * Each interval has a start/stop and a set of polynomial coefficients.
     * <p>
     * The time of flight {@code tof} corresponding to a given earth reception time {@code ert} is given by the formula:
     * 
     * <pre>
     * delta = ert - ertStart
     * tof =c[0] + c[1]*delta + c[2]*delta^2 + ...
     * </pre>
     * 
     * where {@code ertStart} is the start of the interval and {@code c} are the polynomial coefficients. {@code delta}
     * is the duration of the given {@code ert} from the interval start.
     * <p>
     * {@code delta} as well as {@code tof} are expressed in seconds.
     *
     */
    static public class TofInterval {
        final Instant ertStart;
        final Instant ertStop;
        final double[] polCoef;

        public TofInterval(Instant ertStart, Instant ertStop, double[] polCoefficients) {
            this.ertStart = ertStart;
            this.ertStop = ertStop;
            this.polCoef = polCoefficients;
        }

        double getTof(Instant ert) {
            double d = ert.deltaFrom(ertStart);
            double r = 0;

            for (int i = polCoef.length - 1; i >= 0; i--) {
                r = d * r + polCoef[i];
            }
            return r;
        }

        public static TofInterval fromTuple(Tuple tuple) {
            return new TofInterval((Instant) tuple.getColumn("ertStart"),
                    (Instant) tuple.getColumn("ertStop"),
                    decodeCoefficients((byte[]) tuple.getColumn("polCoef")));
        }

        Tuple toTuple() {
            return new Tuple(TDEF, Arrays.asList(ertStart, ertStop, encodeCoefficients(polCoef)));
        }

    }

    static double[] decodeCoefficients(byte[] data) {
        double[] d = new double[data.length / 8];
        ByteBuffer bb = ByteBuffer.wrap(data);
        for (int i = 0; i < d.length; i++) {
            d[i] = bb.getDouble();
        }
        return d;
    }

    static byte[] encodeCoefficients(double[] polCoef) {
        ByteBuffer bb = ByteBuffer.allocate(polCoef.length * 8);
        for (double d : polCoef) {
            bb.putDouble(d);
        }
        return bb.array();
    }
}
