package org.yamcs.time;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.YConfiguration;

/**
 * Time of flight estimator. Can return a fixed value {@link #defaultTof} or an interpolated value based on a table.
 * <p>
 * 
 * @author nm
 *
 */
public class TimeOfFlightEstimator {
    double defaultTof;
    List<TofInterval> calibPoints = new CopyOnWriteArrayList<>();

    public TimeOfFlightEstimator(YConfiguration config) {
        defaultTof = config.getDouble("defaultTof", 0.0);
    }

    /**
     * Returns time of flight of the signal received at ert at the ground station
     * 
     * @param ert
     * @return time of flight in seconds
     */
    double getTof(Instant ert) {
        // this iteration assumes that it is more likely to find the wanted ert in front of the list
        for (TofInterval tdp : calibPoints) {
            if (tdp.ertStart.compareTo(ert) <= 0 && tdp.ertStop.compareTo(ert) > 0) {
                return tdp.getTof(ert);
            }
        }

        return defaultTof;
    }

    void addDataPoint(Instant ertStart, Instant ertStop, double[] polCoefficients) {
        calibPoints.add(0, new TofInterval(ertStart, ertStop, polCoefficients));
    }

    void addDataPoints(Collection<TofInterval> points) {
        // sort the list in reverse order on ertStart
        List<TofInterval> l = new ArrayList<>(points);
        Collections.sort(l, (p1, p2) -> p2.ertStart.compareTo(p1.ertStart));

        calibPoints.addAll(0, l);
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
    }
}
