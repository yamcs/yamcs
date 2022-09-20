package org.yamcs.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.time.Instant;

import com.google.protobuf.Timestamp;

/**
 * Utility class to convert between TAI and UTC. It reads the UTC-TAI.history (available at
 * http://hpiers.obspm.fr/eoppc/bul/bulc/UTC-TAI.history)
 * <p>
 * It only supports added leap seconds not removed ones (negative leap seconds have never happened and probably never
 * will).
 * <p>
 * It only works correctly with the times after 1972 when the difference between TAI and UTC is an integer number of
 * seconds.
 * <p>
 * Most of the code is copied or inspired from the TAI C library http://cr.yp.to/libtai.html
 *
 *
 */
public class TaiUtcConverter {

    static String UTC_TAI_HISTORY_FN = "UTC-TAI.history";

    static final int[] times365 = new int[] { 0, 365, 730, 1095 };
    static final int[] times36524 = new int[] { 0, 36524, 73048, 109572 };
    static final int[] montab = { 0, 31, 61, 92, 122, 153, 184, 214, 245, 275, 306, 337 };
    /* month length after february is (306 * m + 5) / 10 */

    static final int[] PREVIOUS_MONTH_END_DAY = { 0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334 };
    static final int[] PREVIOUS_MONTH_END_DAY_LS = { 0, 0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335 };

    static final long OE9 = 1_000_000_000;
    static final long OE6 = 1_000_000;

    // Timestamp for "0001-01-01T00:00:00Z"
    static final long PROTOBUF_SECONDS_MIN = -62135596800L;

    // Timestamp for "9999-12-31T23:59:59Z"
    static final long PROTOBUF_SECONDS_MAX = 253402300799L;

    long[] timesecs; // TAI time in seconds when leap seconds are added
    int diffTaiUtc; // the difference between the TAI and UTC at the last interval

    // Unprocessed input parsed from UTC-TAI.history file.
    List<ValidityLine> lines = new ArrayList<>();

    public TaiUtcConverter() throws IOException, ParseException {
        this(TaiUtcConverter.class.getResourceAsStream("/" + UTC_TAI_HISTORY_FN));
    }

    public TaiUtcConverter(InputStream utcTaiHistory) throws IOException, ParseException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(utcTaiHistory));
        String line = null;
        // 1974 Jan. 1 - 1975 Jan. 1 13s
        String dp = "\\s?(\\d+)?\\s+(\\w{3})\\.?\\s+(\\d+)";

        Pattern p = Pattern.compile(dp + "\\s*\\.?\\-\\s*(" + dp + ")?\\s*(\\d+)s\\s*");

        ArrayList<Long> tmp1 = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy MMM dd", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        diffTaiUtc = -1;
        int lineNum = 0;
        String prevYear = null;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            Matcher m = p.matcher(line);
            if (m.matches()) {

                String year = m.group(1);
                if (year == null) {
                    year = prevYear;
                }
                Date d = sdf.parse(year + " " + m.group(2) + " " + m.group(3));

                int ls = Integer.valueOf(m.group(8));
                if (diffTaiUtc == -1) {
                    // In 1972 there were 10 leap seconds added at once,
                    // we distribute them over them over the previous 10 days
                    // UTC before 1972 is not defined with leap seconds so it's all approximative
                    for (int i = 1; i < ls; i++) {
                        tmp1.add(d.getTime() - 86400_000 * (ls - i));
                    }
                } else if (ls != diffTaiUtc + 1) {
                    throw new RuntimeException("Error reading line " + lineNum
                            + " of UTC-TAI.history: only positive leap seconds are supported");
                }

                tmp1.add(d.getTime());

                diffTaiUtc = ls;
                prevYear = year;
                lines.add(new ValidityLine(d.getTime(), ls));
            }
        }
        timesecs = new long[tmp1.size()];
        for (int i = 0; i < timesecs.length; i++) {
            timesecs[i] = tmp1.get(i) / 1000 + diffTaiUtc - timesecs.length + i;
        }
    }

    // converts Modified Julian Day to calendar day
    private void caldateFromMjd(DateTimeComponents cd, long day) {
        int year;
        int month;
        int yday;

        year = (int) (day / 146097);
        day %= 146097L;
        day += 678881L;
        while (day >= 146097L) {
            day -= 146097L;
            ++year;
        }

        /* year * 146097 + day - 678881 is MJD; 0 <= day < 146097 */
        /* 2000-03-01, MJD 51604, is year 5, day 0 */

        year *= 4;
        if (day == 146096L) {
            year += 3;
            day = 36524;
        } else {
            year += day / 36524L;
            day %= 36524L;
        }
        year *= 25;
        year += day / 1461;
        day %= 1461;
        year *= 4;

        yday = (day < 306) ? 1 : 0;
        if (day == 1460) {
            year += 3;
            day = 365;
        } else {
            year += day / 365;
            day %= 365;
        }
        yday += day;

        day *= 10;
        month = (int) ((day + 5) / 306);
        day = (day + 5) % 306;
        day /= 10;
        if (month >= 10) {
            yday -= 306;
            ++year;
            month -= 10;
        } else {
            yday += 59;
            month += 2;
        }

        cd.year = year;
        cd.month = month + 1;
        cd.day = (int) (day + 1);

        cd.doy = yday + 1;
    }

    // converts calendar date to Modified Julian Day
    // doy is ignored
    private long caldateToMjd(DateTimeComponents dtc) {
        int y;
        int m;
        int d;

        d = dtc.day - 678882;
        m = dtc.month - 1;
        y = dtc.year;

        d += 146097L * (y / 400);
        y %= 400;

        if (m >= 2) {
            m -= 2;
        } else {
            m += 10;
            --y;
        }

        y += (m / 12);
        m %= 12;
        if (m < 0) {
            m += 12;
            --y;
        }

        d += montab[m];

        d += 146097L * (y / 400);
        y %= 400;
        if (y < 0) {
            y += 400;
            d -= 146097L;
        }

        d += times365[y & 3];
        y >>= 2;

        d += 1461L * (y % 25);
        y /= 25;

        d += times36524[y & 3];

        return d;
    }

    Timestamp instantToProtobuf(long t) {
        return instantToProtobuf(Instant.get(t));
    }

    /**
     * Converts instant to protobuf timestamp by smearing 24 hours around leap seconds.
     * 
     * This class limits the range of the passed timestamp to the max valid ranges of the protobuf timestamps:
     * 0001-01-01T00:00:00Z - 9999-12-31T23:59:59Z
     * <p>
     * A value larger that the max value is changed to the max value and a value smaller than that the minimum is set to
     * the minimum.
     * 
     * @param t
     * @return
     */
    Timestamp instantToProtobuf(Instant instant) {
        Timestamp.Builder timestamp = Timestamp.newBuilder();
        long millis = instant.getMillis();
        int picos = instant.getPicos();

        long u = millis / 1000;
        int ls = diffTaiUtc;
        int nanosec = (int) (1_000_000 * (millis - 1000 * u) + picos / 1000);

        int i;
        for (i = timesecs.length - 1; i >= 0; i--) {
            if (u >= timesecs[i]) {
                break;
            }
            ls--;
        }
        if ((i >= 0) && (u - timesecs[i] < 43200)) {
            ls--;
            long d = 43200 + u - timesecs[i]; // number of seconds since the smearing starts
            nanosec -= (d * OE9 + nanosec) / 86401;
        } else if ((i + 1 < timesecs.length) && (timesecs[i + 1] - u < 43200)) {
            long d = 43200 + u - timesecs[i + 1]; // number of seconds since the smearing starts
            nanosec -= (d * OE9 + nanosec) / 86401;
        }
        if (nanosec < 0) {
            nanosec += OE9;
            ls++;
        }
        long sec = u - ls;
        if (sec > PROTOBUF_SECONDS_MAX) {
            sec = PROTOBUF_SECONDS_MAX;
        } else if (sec < PROTOBUF_SECONDS_MIN) {
            sec = PROTOBUF_SECONDS_MIN;
        }
        timestamp.setSeconds(sec);
        timestamp.setNanos(nanosec);

        return timestamp.build();
    }

    long protobufToInstant(Timestamp ts) {
        return protobufToHresInstant(ts).getMillis();
    }

    Instant protobufToHresInstant(Timestamp ts) {
        int nanosec = ts.getNanos();
        long u = ts.getSeconds() + diffTaiUtc;
        int i;
        for (i = timesecs.length - 1; i >= 0; i--) {
            if (u > timesecs[i]) {
                break;
            }
            u--;
        }
        if ((i >= 0) && (u - timesecs[i] <= 43200)) {
            u--;
            long d = 43200 + u - timesecs[i];
            nanosec += (d * OE9 + nanosec) / 86400;
        } else if ((i + 1 < timesecs.length) && (timesecs[i + 1] - u <= 43200)) {
            long d = 43200 + u - timesecs[i + 1];
            nanosec += (d * OE9 + nanosec) / 86400;
        }

        return Instant.get(u * 1000, nanosec * 1000l);
    }

    DateTimeComponents instantToUtc(long t) {
        DateTimeComponents dtc = new DateTimeComponents();
        long u;
        int leap;
        long s;

        u = t / 1000;
        int millisec = (int) (t % 1000);

        if (millisec < 0) {
            millisec += 1000;
            u -= 1;
        }

        dtc.millisec = millisec;

        // leap = leapsecs_sub(&t2);
        leap = 0;
        int ls = diffTaiUtc;

        for (int i = timesecs.length - 1; i >= 0; i--) {
            if (u > timesecs[i]) {
                break;
            }
            if (u == timesecs[i]) {
                leap = 1;
                break;
            }
            ls--;
        }
        u -= ls;

        s = u % 86400L;

        if (s < 0) {
            s += 86400L;
            u -= 86400L;
        }
        dtc.second = (int) ((s % 60) + leap);

        s /= 60;
        dtc.minute = (int) (s % 60);
        s /= 60;
        dtc.hour = (int) s;

        u /= 86400L;
        long mjd = 40587 + u;

        caldateFromMjd(dtc, mjd);

        return dtc;
    }

    /**
     * transforms Instant to Unix time expressed in milliseconds since 1970
     * 
     * @param t
     * @return
     */
    long instantToUnix(long t) {
        long u = t / 1000;
        int ls = diffTaiUtc;
        for (int i = timesecs.length - 1; i >= 0; i--) {
            if (u >= timesecs[i]) {
                break;
            }
            ls--;
        }
        return t - ls * 1000;
    }

    /**
     * Converts UTC to instant. WARNING: DOY is ignored.
     * 
     * @param dtc
     * @return
     */
    long utcToInstant(DateTimeComponents dtc) {
        long day = caldateToMjd(dtc);

        long s = (long) (dtc.hour * 60 + dtc.minute);
        s = s * 60 + dtc.second + (day - 40587) * 86400L;

        int ls = diffTaiUtc;
        for (int i = timesecs.length - 1; i >= 0; i--) {
            long u = timesecs[i] - ls + 1;
            if (s > u) {
                break;
            }
            if ((s < u) || (dtc.second == 60)) {
                ls--;
            }
        }
        s += ls;

        return dtc.millisec + 1000 * s;
    }

    /**
     * transforms UNIX time expressed in milliseconds since 1970 to instant
     * 
     * @param t
     * @return
     */
    long unixToInstant(long t) {
        long u = t / 1000;
        int ls = diffTaiUtc;
        for (int i = timesecs.length - 1; i >= 0; i--) {
            if (u >= timesecs[i] - ls + 1) {
                break;
            }
            ls--;
        }
        return t + ls * 1000;
    }

    public static boolean isLeap(final int year) {
        return ((year % 4) == 0) && (((year % 400) == 0) || ((year % 100) != 0));
    }

    public static class DateTimeComponents {
        int year;
        int month; // month starting with 1
        int day;
        int hour;
        int minute;
        int second;
        int millisec;
        int doy;

        /**
         * Constructs a new DateTimeComponents
         * 
         * @param year
         * @param month
         * @param day
         * @param hour
         * @param minute
         * @param second
         * @param millisec
         */
        public DateTimeComponents(int year, int month, int day, int hour,
                int minute, int second, int millisec) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.millisec = millisec;
        }

        private DateTimeComponents() {
        }

        public DateTimeComponents(int year, int doy, int hour, int minute,
                int second, int millisec) {
            this.year = year;
            this.doy = doy;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.millisec = millisec;

            if (isLeap(year)) {
                this.month = (doy < 32) ? 1 : (10 * doy + 313) / 306;
                this.day = doy - PREVIOUS_MONTH_END_DAY_LS[this.month];
            } else {
                this.month = (doy < 32) ? 1 : (10 * doy + 323) / 306;
                this.day = doy - PREVIOUS_MONTH_END_DAY[this.month];
            }

        }

        public int getYear() {
            return year;
        }

        public int getDoy() {
            return doy;
        }

        public int getMonth() {
            return month;
        }

        public int getDay() {
            return day;
        }

        public int getHour() {
            return hour;
        }

        public int getMinute() {
            return minute;
        }

        public int getSecond() {
            return second;
        }

        public int getMillisec() {
            return millisec;
        }

        @Override
        public String toString() {
            return "DateTimeComponents [year=" + year + ", month=" + month
                    + ", day=" + day + ", hour=" + hour + ", minute=" + minute
                    + ", second=" + second + ", millisec=" + millisec
                    + ", doy=" + doy + "]";
        }

        public String toIso8860String() {
            return String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03d", year, month, day, hour, minute, second,
                    millisec);
        }
    }

    public static final class ValidityLine {
        public final long unixMillis;
        public final int seconds; // TAI - UTC seconds

        private ValidityLine(long unixMillis, int seconds) {
            this.unixMillis = unixMillis;
            this.seconds = seconds;
        }
    }
}
