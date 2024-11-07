package org.yamcs.utils;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.time.Instant;
import org.yamcs.utils.TaiUtcConverter.ValidityLine;

import com.google.protobuf.Timestamp;

/**
 *
 * This class provides times in terms of milliseconds since 1970TAI
 * 
 * @author nm
 *
 */
public class TimeEncoding {
    public static final long INVALID_INSTANT = Long.MIN_VALUE;
    public static long MAX_INSTANT = 185539080470435999L;
    public static final long MIN_INSTANT = Long.MIN_VALUE + 1;

    // these two are used for open intervals
    public static final long NEGATIVE_INFINITY = MIN_INSTANT - 1;
    public static final long POSITIVE_INFINITY = MAX_INSTANT + 1;

    static final long GPS_EPOCH_YAMCS_EPOCH_DELTA = 315964819000L;
    static final long TAI_EPOCH_YAMCS_EPOCH_DELTA = -378691200000L;
    static final long J2000_EPOCH_YAMCS_EPOCH_DELTA = 946727967816L;

    static final long GPS_TAI_DELTA = 19000;

    static TaiUtcConverter taiUtcConverter;
    static Pattern iso8601Pattern = Pattern
            .compile("(\\d+)\\-(\\d{2})\\-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.(\\d{3})\\d{0,9})?Z?");
    static Pattern doyPattern = Pattern.compile("(\\d+)\\/(\\d+)T(\\d{2}):(\\d{2}):(\\d{2})(\\.(\\d{3})\\d{0,9})?Z?");

    static Pattern iso8601PatternHres = Pattern
            .compile("(\\d+)\\-(\\d{2})\\-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.(\\d{3})(\\d{0,9}))?Z?");
    static Pattern doyPatternHres = Pattern
            .compile("(\\d+)\\/(\\d+)T(\\d{2}):(\\d{2}):(\\d{2})(\\.(\\d{3})(\\d{0,9}))?Z?");

    public static void setUp() {
        try {
            taiUtcConverter = new TaiUtcConverter();
            MAX_INSTANT = 185539080470399999L + taiUtcConverter.diffTaiUtc * 1000;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setUp(InputStream in) {
        try {
            taiUtcConverter = new TaiUtcConverter(in);
            MAX_INSTANT = 185539080470399999L + taiUtcConverter.diffTaiUtc * 1000;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the current operating system time but converted to Yamcs instant.
     * 
     * @return
     */
    public static long getWallclockTime() {
        return taiUtcConverter.unixToInstant(System.currentTimeMillis());
    }

    /**
     * Sane as {@link #getWallclockTime()} but returns a high resolution instant.
     * <p>
     * Currently java does not make it easy to get a high resolution time so the returned object has always the
     * picosecond field set to 0.
     * 
     * @return
     */
    public static Instant getWallclockHresTime() {
        long millis = taiUtcConverter.unixToInstant(System.currentTimeMillis());
        return Instant.get(millis);
    }

    private static void formatOn2Digits(int x, StringBuilder sb) {
        if (x < 10) {
            sb.append("0").append(x);
        } else {
            sb.append(x);
        }
    }

    private static void formatOn3Digits(int x, StringBuilder sb) {
        if (x < 10) {
            sb.append("00").append(x);
        } else if (x < 100) {
            sb.append("0").append(x);
        } else {
            sb.append(x);
        }
    }

    private static void formatOn4Digits(int x, StringBuilder sb) {
        if (x < 10) {
            sb.append("000").append(x);
        } else if (x < 100) {
            sb.append("00").append(x);
        } else if (x < 1000) {
            sb.append("0").append(x);
        } else {
            sb.append(x);
        }
    }

    /**
     * Returns the instant formatted as UTC yyyy-MM-DDTHH:mm:ss.SSSZ
     * <p>
     * If the value is smalle than {@link #MIN_INSTANT} it returns -inf
     *
     * If the value is larger than {@link #MAX_INSTANT} it returns +inf
     * 
     * @param instant
     * @return
     */
    public static String toString(long instant) {
        if (instant < MIN_INSTANT) {
            return "-inf";
        }
        if (instant > MAX_INSTANT) {
            return "+inf";
        }

        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb = new StringBuilder();
        formatOn4Digits(dtc.year, sb);
        sb.append("-");
        formatOn2Digits(dtc.month, sb);
        sb.append("-");
        formatOn2Digits(dtc.day, sb);
        sb.append("T");
        formatOn2Digits(dtc.hour, sb);
        sb.append(":");
        formatOn2Digits(dtc.minute, sb);
        sb.append(":");
        formatOn2Digits(dtc.second, sb);
        sb.append(".");
        formatOn3Digits(dtc.millisec, sb);
        return sb.append("Z").toString();
    }

    /**
     * Returns the instant formatted as UTC yyyy-DDDTHH:mm:ss.SSS
     * 
     * @param instant
     * @return
     */
    public static String toOrdinalDateTime(long instant) {
        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb = new StringBuilder();
        formatOn4Digits(dtc.year, sb);
        sb.append("-");
        formatOn3Digits(dtc.doy, sb);
        sb.append("T");
        formatOn2Digits(dtc.hour, sb);
        sb.append(":");
        formatOn2Digits(dtc.minute, sb);
        sb.append(":");
        formatOn2Digits(dtc.second, sb);
        sb.append(".");
        formatOn3Digits(dtc.millisec, sb);
        return sb.toString();
    }

    /**
     * Returns the instant in UTC time scale formatted as YYYY-DDDTHHhMMmSSsSSS so that is leads to an MS Windows
     * compatible filename
     * 
     * @param instant
     * @return
     */
    public static String toWinCompatibleDateTime(long instant) {
        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb = new StringBuilder();
        formatOn4Digits(dtc.year, sb);
        sb.append("-");
        formatOn3Digits(dtc.doy, sb);
        sb.append("T");
        formatOn2Digits(dtc.hour, sb);
        sb.append("h");
        formatOn2Digits(dtc.minute, sb);
        sb.append("m");
        formatOn2Digits(dtc.second, sb);
        sb.append("s");
        formatOn3Digits(dtc.millisec, sb);
        return sb.toString();
    }

    public static String toCombinedFormat(long instant) {
        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb = new StringBuilder();
        formatOn4Digits(dtc.year, sb);
        sb.append("-");
        formatOn2Digits(dtc.month, sb);
        sb.append("-");
        formatOn2Digits(dtc.day, sb);
        sb.append("/");
        formatOn3Digits(dtc.doy, sb);
        sb.append("T");
        formatOn2Digits(dtc.hour, sb);
        sb.append(":");
        formatOn2Digits(dtc.minute, sb);
        sb.append(":");
        formatOn2Digits(dtc.second, sb);
        sb.append(".");
        formatOn3Digits(dtc.millisec, sb);
        return sb.toString();
    }

    /**
     * we assume coarseTime to be always positive (corresponding to uint32_t in C)
     * 
     * @param coarseTime
     *            number of seconds from GPS epoch
     * @param fineTime
     *            number of 1/256 seconds
     * @return
     */
    public static long fromGpsCcsdsTime(int coarseTime, byte fineTime) {
        long c = ((long) coarseTime) & 0xFFFFFFFFL;
        return GPS_EPOCH_YAMCS_EPOCH_DELTA + c * 1000 + 1000 * (0xFF & fineTime) / 256;
    }

    /**
     * Conversion from instant to GPS time.
     * 
     * @param instant
     *            yamcs time
     * @return GPS time
     */
    public static GpsCcsdsTime toGpsTime(final long instant) {
        long shiftedMillis = instant - GPS_EPOCH_YAMCS_EPOCH_DELTA;
        int coarseTime = (int) (shiftedMillis / 1000);
        byte fineTime = (byte) (((shiftedMillis % 1000) * 256 / 1000));
        return new GpsCcsdsTime(coarseTime, fineTime);
    }

    /**
     * Conversion from current instant to GPS time. Current time is the *nix time this function is called.
     * 
     * @return GPS time
     */
    public static GpsCcsdsTime getCurrentGpsTime() {
        return toGpsTime(TimeEncoding.getWallclockTime());
    }

    /**
     * Conversion from instant to GPS time (milliseconds since the GPS epoch).
     * 
     * @param instant
     *            TimeEncoding instant
     *
     * @return GPS time
     */
    public static long toGpsTimeMillisec(final long instant) {
        return instant - GPS_EPOCH_YAMCS_EPOCH_DELTA;
    }

    public static long fromGpsYearSecMillis(int year, int secOfYear, int millis) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(Calendar.YEAR, year);
        cal.add(Calendar.SECOND, secOfYear);
        cal.add(Calendar.MILLISECOND, millis);

        return GPS_TAI_DELTA + cal.getTimeInMillis();
    }

    public static TaiUtcConverter.DateTimeComponents toUtc(long instant) {
        return taiUtcConverter.instantToUtc(instant);
    }

    public static long fromUtc(TaiUtcConverter.DateTimeComponents dtc) {
        return taiUtcConverter.utcToInstant(dtc);
    }

    public static List<ValidityLine> getTaiUtcConversionTable() {
        return taiUtcConverter.lines;
    }

    /**
     * parses an ISO 8601 UTC date into an instant
     * 
     * @param s
     *            - string to be parsed
     * @return - internal Yamcs timestamp
     * @throws IllegalArgumentException
     *             if the time cannot be parsed
     */
    public static long parse(String s) {
        TaiUtcConverter.DateTimeComponents dtc;
        Matcher m = iso8601Pattern.matcher(s);

        if (m.matches()) {

            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(4));
            int minute = Integer.parseInt(m.group(5));
            int second = Integer.parseInt(m.group(6));
            int millisec = 0;
            if (m.group(7) != null) {
                millisec = Integer.parseInt(m.group(8));
            }
            dtc = new TaiUtcConverter.DateTimeComponents(year, month, day, hour, minute, second, millisec);
        } else {
            m = doyPattern.matcher(s);
            if (m.matches()) {
                int year = Integer.parseInt(m.group(1));
                int doy = Integer.parseInt(m.group(2));
                int hour = Integer.parseInt(m.group(3));
                int minute = Integer.parseInt(m.group(4));
                int second = Integer.parseInt(m.group(5));
                int millisec = 0;
                if (m.group(6) != null) {
                    millisec = Integer.parseInt(m.group(7));
                }

                dtc = new TaiUtcConverter.DateTimeComponents(year, doy, hour, minute, second, millisec);

            } else {
                throw new IllegalArgumentException(
                        "Cannot parse '" + s + "' with the pattern '" + iso8601Pattern + " or " + doyPattern);
            }
        }
        return taiUtcConverter.utcToInstant(dtc);
    }

    public static Instant parseHres(String s) {

        TaiUtcConverter.DateTimeComponents dtc;
        Matcher m = iso8601PatternHres.matcher(s);
        int picos = 0;

        if (m.matches()) {

            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(4));
            int minute = Integer.parseInt(m.group(5));
            int second = Integer.parseInt(m.group(6));
            int millisec = 0;

            if (m.group(7) != null) {
                millisec = Integer.parseInt(m.group(8));
                picos = getPicos(m.group(9));
            }

            dtc = new TaiUtcConverter.DateTimeComponents(year, month, day, hour, minute, second, millisec);
        } else {
            m = doyPatternHres.matcher(s);
            if (m.matches()) {
                int year = Integer.parseInt(m.group(1));
                int doy = Integer.parseInt(m.group(2));
                int hour = Integer.parseInt(m.group(3));
                int minute = Integer.parseInt(m.group(4));
                int second = Integer.parseInt(m.group(5));
                int millisec = 0;
                if (m.group(6) != null) {
                    millisec = Integer.parseInt(m.group(7));
                    picos = getPicos(m.group(9));
                }

                dtc = new TaiUtcConverter.DateTimeComponents(year, doy, hour, minute, second, millisec);

            } else {
                throw new IllegalArgumentException(
                        "Cannot parse '" + s + "' with the pattern '" + iso8601Pattern + " or " + doyPattern);
            }
        }
        long millis = taiUtcConverter.utcToInstant(dtc);
        return Instant.get(millis, picos);
    }

    // get the number of picoseconds from a max to 9 digits number aligned at left
    static private int getPicos(String ps) {
        if (ps.length() == 0) {
            return 0;
        }
        int r = Integer.parseInt(ps);

        for (int i = ps.length(); i < 9; i++) {
            r *= 10;
        }
        return r;
    }

    /**
     * Transforms UNIX time (milliseconds since 1970, picos in millisecond) to high resolution instant
     * 
     * @param millis
     *            milliseconds since 1970 (without leap seconds)
     * @param picos
     *            picoseconds in milliseconds - can be negative or larger than 10^9 (but has to fit into a 32 bit signed
     *            integer).
     * 
     * @return
     */
    public static Instant fromUnixPicos(long millis, int picos) {
        return Instant.get(taiUtcConverter.unixToInstant(millis), picos);
    }

    /**
     * Transforms UNIX time (milliseconds since 1970) to instant
     * 
     * @param milliseconds
     * @return
     */
    public static long fromUnixMillisec(long milliseconds) {
        return taiUtcConverter.unixToInstant(milliseconds);
    }

    /**
     * Transforms UNIX time expressed in seconds and microseconds since 1970 to instant WARNING: this conversion will
     * lose precision (microsecond to millisecond)
     *
     * @param seconds
     * @param microseconds
     * @return
     */
    public static long fromUnixTime(long seconds, int microseconds) {
        long millisec = seconds * 1000 + microseconds / 1000;
        return taiUtcConverter.unixToInstant(millisec);
    }

    /**
     * Transforms instant to UNIX time expressed in milliseconds since 1970
     * 
     * @param instant
     * @return
     */
    public static long toUnixMillisec(long instant) {
        return taiUtcConverter.instantToUnix(instant);
    }

    /**
     * Transforms a {@link java.util.Calendar} from UNIX (millisec since 1970) to instant
     */
    public static long fromCalendar(Calendar cal) {
        return fromUnixMillisec(cal.getTimeInMillis());
    }

    /**
     * Transforms a {@link java.util.Date} from UNIX (millisec since 1970) to instant
     */
    public static long fromDate(Date date) {
        return fromUnixMillisec(date.getTime());
    }

    /**
     * Transforms a {@link java.time.Instant} from UNIX (millisec since 1970) to instant
     */
    public static long fromJavaInstant(java.time.Instant instant) {
        return fromUnixMillisec(instant.toEpochMilli());
    }

    /**
     * Transforms instant into a {@link java.util.Calendar} containing milliseconds since 1970
     * 
     * @param instant
     *            Yamcs instant
     */
    public static Calendar toCalendar(long instant) {
        if (instant == TimeEncoding.INVALID_INSTANT) {
            return null;
        }
        long t = taiUtcConverter.instantToUnix(instant);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        return cal;
    }

    /**
     * Transforms instant into a {@link java.time.Instant} containing milliseconds since 1970
     * 
     * @param instant
     *            Yamcs instant
     */
    public static java.time.Instant toJavaInstant(long instant) {
        if (instant == TimeEncoding.INVALID_INSTANT) {
            return null;
        }
        long t = taiUtcConverter.instantToUnix(instant);
        return java.time.Instant.ofEpochMilli(t);
    }

    /**
     * JavaGps is number of milliseconds since 1970 that assumes no leap seconds from 1970 to GPS Epoch, and then
     * continues with the leap seconds.
     * 
     * @param instant
     * @return
     */
    public static long getJavaGpsFromInstant(long instant) {
        return instant - 19000;
    }

    public static long getInstantfromJavaGps(long javagps) {
        return javagps + 19000;
    }

    /**
     *
     * @param gpstime
     *            number of millisec from GPS epoch
     * @return
     */
    public static long fromGpsMillisec(long gpstime) {
        return gpstime + GPS_EPOCH_YAMCS_EPOCH_DELTA;
    }

    public static long fromTaiMillisec(long taitime) {
        return taitime + TAI_EPOCH_YAMCS_EPOCH_DELTA;
    }

    public static long toTaiMillisec(long instant) {
        return instant - TAI_EPOCH_YAMCS_EPOCH_DELTA;
    }

    public static long fromJ2000Millisec(long j2000time) {
        return j2000time + J2000_EPOCH_YAMCS_EPOCH_DELTA;
    }

    public static long toJ2000Millisec(long instant) {
        return instant - J2000_EPOCH_YAMCS_EPOCH_DELTA;
    }

    /**
     * Transforms protobuf Timestamp to instant. The conversion will do the "unsmearing" around the leap seconds and
     * will also lose precision (nanoseconds to milliseconds).
     *
     * @see <a href="https://developers.google.com/time/smear">https://developers.google.com/time/smear</a>
     * 
     * @param ts
     *            - the timestamp to be converted
     * @return
     */
    public static long fromProtobufTimestamp(Timestamp ts) {
        return taiUtcConverter.protobufToInstant(ts);
    }

    /**
     * Transforms protobuf Timestamp to high resolution instant. The conversion will do the "unsmearing" around the leap
     * seconds.
     *
     * @see <a href="https://developers.google.com/time/smear">https://developers.google.com/time/smear</a>
     *
     * @param ts
     *            - the timestamp to be converted
     * @return
     */
    public static Instant fromProtobufHresTimestamp(Timestamp ts) {
        return taiUtcConverter.protobufToHresInstant(ts);
    }

    /**
     * Transforms the instant to protobuf timestamp performing the smearing around the leap seconds.
     * 
     * @see <a href="https://developers.google.com/time/smear">https://developers.google.com/time/smear</a>
     *
     * @param instant
     *            - the instant to be converted
     * @return
     */
    public static Timestamp toProtobufTimestamp(long instant) {
        return taiUtcConverter.instantToProtobuf(Instant.get(instant));
    }

    /**
     * Transforms the instant to protobuf timestamp performing the smearing around the leap seconds.
     *
     * @see <a href="https://developers.google.com/time/smear">https://developers.google.com/time/smear</a>
     *
     * @param instant
     *            - the instant to be converted
     * @return
     */
    public static Timestamp toProtobufTimestamp(Instant instant) {
        return taiUtcConverter.instantToProtobuf(instant);
    }

    /**
     * returns true if the {@link #setUp()} method has been called to load the leap second table.
     * <p>
     * If this method returns false, any call to the UTC conversion functions will throw a NullPointerException
     */
    public static boolean isSetUp() {
        return taiUtcConverter != null;
    }
}
