package org.yamcs.utils;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 
 * This class provides times in terms of milliseconds since 1970TAI
 * @author nm
 * 
 */
public class TimeEncoding {
    public static final long INVALID_INSTANT       = Long.MIN_VALUE;
    //these two are used for open intervals
    public static final long MIN_INSTANT       = Long.MIN_VALUE;
    public static long MAX_INSTANT = 185539080470435999L;

    static final long GPS_EPOCH_YAMCS_EPOCH_DELTA = 315964819000L;
    static final long GPS_TAI_DELTA = 19000;
 
    static TaiUtcConverter taiUtcConverter;
    static Pattern iso8860Pattern = Pattern.compile("(\\d+)\\-(\\d{2})\\-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.(\\d{3}))?");  
    static Pattern doyPattern = Pattern.compile("(\\d+)\\/(\\d+)T(\\d{2}):(\\d{2}):(\\d{2})(\\.(\\d{3}))?");  
    
    public static void setUp() throws RuntimeException {
        try {
            taiUtcConverter = new TaiUtcConverter();
            MAX_INSTANT = 185539080470399999L + taiUtcConverter.diffTaiUtc * 1000;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Returns the current wall clock time. Is the same with getWallclockTime
     * 
     * Should use instead timeService.getMissionTime()
     * 
     * @return
     */
    @Deprecated
    public static long currentInstant() {
        return taiUtcConverter.unixToInstant(System.currentTimeMillis());
    }

    public static long getWallclockTime() {
        return taiUtcConverter.unixToInstant(System.currentTimeMillis());
    }

    
    private static void formatOn2Digits(int x, StringBuilder sb) {
    	if(x<10) sb.append("0").append(x);
    	else sb.append(x);
    }
    
    private static void formatOn3Digits(int x, StringBuilder sb) {
    	if(x<10) sb.append("00").append(x);
    	else if(x<100) sb.append("0").append(x);
    	else sb.append(x);
    }
    
    private static void formatOn4Digits(int x, StringBuilder sb) {
    	if(x<10) sb.append("000").append(x);
    	else if(x<100) sb.append("00").append(x);
    	else if(x<1000) sb.append("0").append(x);
    	else sb.append(x);
    }
    
    /**
     * Returns the instant formatted as utc
     * yyyy-MM-DDTHH:mm:ss.SSS
     * @param instant
     * @return
     */
    public static String toString(long instant) {
        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb=new StringBuilder();
        formatOn4Digits(dtc.year, sb);sb.append("-");
        formatOn2Digits(dtc.month, sb); sb.append("-");
        formatOn2Digits(dtc.day, sb); sb.append("T");
        formatOn2Digits(dtc.hour, sb); sb.append(":");
        formatOn2Digits(dtc.minute, sb); sb.append(":");
        formatOn2Digits(dtc.second, sb); sb.append(".");
        formatOn3Digits(dtc.millisec, sb);
        return sb.toString();
    }

    /**
     * Returns the instant formatted as UTC
     * yyyy-DDDTHH:mm:ss.SSS
     * @param instant
     * @return
     */
    public static String toOrdinalDateTime(long instant) {
        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb=new StringBuilder();
        formatOn4Digits(dtc.year, sb);sb.append("-");
        formatOn3Digits(dtc.doy, sb); sb.append("T");
        formatOn2Digits(dtc.hour, sb); sb.append(":");
        formatOn2Digits(dtc.minute, sb); sb.append(":");
        formatOn2Digits(dtc.second, sb); sb.append(".");
        formatOn3Digits(dtc.millisec, sb);
        return sb.toString();
    }
   
    /**
     * Returns the instant in UTC time scale formatted as 
     * YYYY-DDDTHHhMMmSSsSSS
     * so that is leads to an MS Windows compatible filename
     * @param instant
     * @return 
     */
    public static String toWinCompatibleDateTime(long instant) {
        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb = new StringBuilder();
        formatOn4Digits(dtc.year, sb); sb.append("-");
        formatOn3Digits(dtc.doy, sb); sb.append("T");
        formatOn2Digits(dtc.hour, sb); sb.append("h");
        formatOn2Digits(dtc.minute, sb); sb.append("m");                
        formatOn2Digits(dtc.second, sb); sb.append("s");                
        formatOn3Digits(dtc.millisec, sb);
        return sb.toString();
    }

    public static String toCombinedFormat(long instant) {
        TaiUtcConverter.DateTimeComponents dtc = taiUtcConverter.instantToUtc(instant);
        StringBuilder sb=new StringBuilder();
        formatOn4Digits(dtc.year, sb);sb.append("-");
        formatOn2Digits(dtc.month, sb); sb.append("-");
        formatOn2Digits(dtc.day, sb); sb.append("/");
        formatOn3Digits(dtc.doy, sb); sb.append("T");
        formatOn2Digits(dtc.hour, sb); sb.append(":");
        formatOn2Digits(dtc.minute, sb); sb.append(":");
        formatOn2Digits(dtc.second, sb); sb.append(".");
        formatOn3Digits(dtc.millisec, sb);
        return sb.toString();
    }

    /**
     * we assume coarseTime to be always positive (corresponding to uint32_t in
     * C)
     * @param coarseTime number of seconds from GPS epoch
     * @param fineTime number of 1/256 seconds
     * @return
     */
    public static long fromGpsCcsdsTime(int coarseTime, byte fineTime) {
        long c = ((long) coarseTime) & 0xFFFFFFFFL;
        return GPS_EPOCH_YAMCS_EPOCH_DELTA + c * 1000 + 1000 * (0xFF & fineTime) / 256;
    }

    /**
     * Conversion from instant to GPS time.
     * @param instant yamcs time
     * @return GPS time
     */
    public static GpsCcsdsTime toGpsTime(final long instant) {
        GpsCcsdsTime gpsTime = new GpsCcsdsTime();
        long shiftedMillis = instant - GPS_EPOCH_YAMCS_EPOCH_DELTA;
        gpsTime.coarseTime = (int) (shiftedMillis / 1000);
        gpsTime.fineTime = (byte) (((shiftedMillis % 1000) * 256 / 1000));
        return gpsTime;
    }

    
    /**
     * Conversion from current instant to GPS time.
     * Current time is the *nix time this function is called.
     * @return GPS time
     */
    public static GpsCcsdsTime getCurrentGpsTime() {
        return toGpsTime(TimeEncoding.currentInstant());
    }
    
    /**
     * Conversion from instant to GPS time (milliseconds since the GPS epoch).
     * @param instant TimeEncoding instant
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


    /**
     * parses an ISO 8860 UTC date into an instant
     * @param s
     * @return
     */
    public static long parse(String s) {
        TaiUtcConverter.DateTimeComponents dtc; 
        Matcher m = iso8860Pattern.matcher(s);
        
        
        if(m.matches()) {
            
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(4));
            int minute = Integer.parseInt(m.group(5));
            int second = Integer.parseInt(m.group(6));
            int millisec =0;
            if(m.group(7)!=null) {
                millisec = Integer.parseInt(m.group(8));
            }
            dtc = new TaiUtcConverter.DateTimeComponents(year, month, day, hour, minute, second, millisec);
        } else {
            m = doyPattern.matcher(s);
            if(m.matches()) {
                int year = Integer.parseInt(m.group(1));
                int doy = Integer.parseInt(m.group(2));
                int hour = Integer.parseInt(m.group(3));
                int minute = Integer.parseInt(m.group(4));
                int second = Integer.parseInt(m.group(5));
                int millisec = 0;
                if(m.group(6)!=null) {
                    millisec = Integer.parseInt(m.group(7));
                }
                
                dtc = new TaiUtcConverter.DateTimeComponents(year, doy, hour, minute, second, millisec);
                
            } else {
                throw new IllegalArgumentException("Cannot parse '"+s+"' with the pattern '"+iso8860Pattern+" or "+doyPattern);
            }
        }
        return taiUtcConverter.utcToInstant(dtc);
    }

    
    /**
     * Transforms UNIX time (milliseconds since 1970) to instant
     * @param milliseconds
     * @return
     */
    public static long fromUnixTime(long milliseconds) {
        return taiUtcConverter.unixToInstant(milliseconds);
    }
    
    /**
     * Transforms UNIX time expressed in seconds and microseconds since 1970 to instant
     * WARNING: this conversion will loose precision (microsecond to millisecond)
     * 
     * @param seconds
     * @param microseconds
     * @return
     */
    public static long fromUnixTime(long seconds, int microseconds) {
        long millisec = seconds*1000+microseconds/1000;
        return taiUtcConverter.unixToInstant(millisec);
    }

    /**
     * Transforms instant to UNIX time expressed in milliseconds since 1970
     * @param instant
     * @return
     */
    public static long toUnixTime(long instant) {
        return taiUtcConverter.instantToUnix(instant);
    }

    /**
     * Transforms the cal from UNIX (millisec since 1970) to instant
     * @param cal
     * @return
     */
    public static long fromCalendar(Calendar cal) {
        return fromUnixTime(cal.getTimeInMillis());
    }
    
    /**
     * transforms instant into a java cal containing milliseconds since 1970
     * @param instant
     * @return
     */
    public static Calendar toCalendar(long instant) {
    	if(instant==TimeEncoding.INVALID_INSTANT) return null;
    	long t = taiUtcConverter.instantToUnix(instant);
        Calendar cal=Calendar.getInstance();
        cal.setTimeInMillis(t);
        return cal;
    }
    
    /**
     * JavaGps is number of milliseconds since 1970 that assumes no leap seconds
     * from 1970 to GPS Epoch, and then continues with the leap seconds.
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
     * @param gpstime number of millisec from GPS epoch
     * @return
     */
    public static long fromGpsMillisec(long gpstime) {
        return gpstime + GPS_EPOCH_YAMCS_EPOCH_DELTA;
    }


}
