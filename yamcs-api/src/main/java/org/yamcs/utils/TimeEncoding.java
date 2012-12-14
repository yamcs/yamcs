package org.yamcs.utils;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import org.orekit.data.DataProvidersManager;
import org.orekit.errors.OrekitException;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateTimeComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.time.UTCScale;
import org.yamcs.utils.GpsCcsdsTime;


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
    public static final long MAX_INSTANT       = Long.MAX_VALUE;
    
    static UTCScale          utc;
    static TimeScale         hmiScale;
    
    public static final AbsoluteDate  YAMCS_EPOCH          = new AbsoluteDate(1970, 1, 1, 0, 0, 0, TimeScalesFactory.getTAI());
    static final long        GPS_YAMCS_EPOCH_DELTA = 315964819000L;
 
    public static final AbsoluteDate UTC1972     = new AbsoluteDate("1972-01-01T00:00:00", TimeScalesFactory.getTAI()).shiftedBy(10);
    
    public static void setUp() throws OrekitException {
        Properties props = System.getProperties();
        if (props.getProperty(DataProvidersManager.OREKIT_DATA_PATH) == null)
        {
            String[] cp = ((String) props.get("java.class.path")).split(File.pathSeparator);
            for (String dirname : cp)
            {
                File f = new File(dirname + "/orekit");
                if (f.exists() && f.isDirectory())
                {
                    props.setProperty("orekit.data.path", f.getAbsolutePath());
                    break;
                }
            }
        }
        utc = TimeScalesFactory.getUTC();
        hmiScale = utc;
    }

    public static long currentInstant() {
        return fromAbsoluteDate(currentAbsoluteDate());
    }

    public static AbsoluteDate currentAbsoluteDate() {
        return new AbsoluteDate(new Date(), utc);
    }

    public static AbsoluteDate getAbsoluteDate(long instant) {
        return new AbsoluteDate(YAMCS_EPOCH, instant / 1000.0d);
    }

    public static String toString(AbsoluteDate ad) {
        return ad.toString(hmiScale);
    }

   
    public static void formatOn2Digits(int x, StringBuilder sb) {
    	if(x<10) sb.append("0").append(x);
    	else sb.append(x);
    }
    
    public static void formatOn3Digits(int x, StringBuilder sb) {
    	if(x<10) sb.append("00").append(x);
    	else if(x<100) sb.append("0").append(x);
    	else sb.append(x);
    }
    
    public static void formatOn4Digits(int x, StringBuilder sb) {
    	if(x<10) sb.append("000").append(x);
    	else if(x<100) sb.append("00").append(x);
    	else if(x<1000) sb.append("0").append(x);
    	else sb.append(x);
    }
    
    public static void formatSeconds(double x, StringBuilder sb) {
    	formatOn2Digits((int)x,sb); sb.append(".");
        int millisec=((int)(Math.round(x*1000)))%1000;
        formatOn3Digits(millisec,sb);
    }
    
    public static String toOrdinalDateTime(AbsoluteDate ad)  {
    	 DateTimeComponents dtc = ad.getComponents(hmiScale);
         StringBuilder sb=new StringBuilder();
         formatOn4Digits(dtc.getDate().getYear(),sb);sb.append("-");
         formatOn3Digits(dtc.getDate().getDayOfYear(),sb); sb.append("T");
         formatOn2Digits(dtc.getTime().getHour(),sb); sb.append(":");
         formatOn2Digits(dtc.getTime().getMinute(),sb); sb.append(":");
         formatSeconds(dtc.getTime().getSecond(), sb);
         return sb.toString();
    }
        
    public static String toWinCompatibleDateTime(AbsoluteDate ad)  {
   	 DateTimeComponents dtc = ad.getComponents(hmiScale);
        StringBuilder sb = new StringBuilder();
        formatOn4Digits(dtc.getDate().getYear(),sb); sb.append("-");
        formatOn3Digits(dtc.getDate().getDayOfYear(),sb); sb.append("T");
        formatOn2Digits(dtc.getTime().getHour(),sb); sb.append("h");
        formatOn2Digits(dtc.getTime().getMinute(),sb); sb.append("m");                
        formatSeconds(dtc.getTime().getSecond(), sb);                
        return sb.toString().replace('.', 's');
   }

    public static String toCombinedFormat(AbsoluteDate ad) {
        DateTimeComponents dtc = ad.getComponents(hmiScale);
        StringBuilder sb=new StringBuilder();
        formatOn4Digits(dtc.getDate().getYear(),sb);sb.append("-");
        formatOn2Digits(dtc.getDate().getMonth(),sb); sb.append("-");
        formatOn2Digits(dtc.getDate().getDay(),sb); sb.append("/");
        formatOn3Digits(dtc.getDate().getDayOfYear(),sb); sb.append("T");
        formatOn2Digits(dtc.getTime().getHour(),sb); sb.append(":");
        formatOn2Digits(dtc.getTime().getMinute(),sb); sb.append(":");
        formatSeconds(dtc.getTime().getSecond(), sb);
        return sb.toString();
    }
    
    /**
     * Returns the instant formatted as utc
     * yyyy-DD-MMTHH:mm:ss.SSS
     * @param instant
     * @return
     */
    public static String toString(long instant) {
        return toString(getAbsoluteDate(instant));
    }

    /**
     * Returns the instant in UTC timescale formatted as utc
     * yyyy-DDDTHH:mm:ss.SSS
     * @param instant
     * @return
     */
    public static String toOrdinalDateTime(long instant) {
        return toOrdinalDateTime(getAbsoluteDate(instant));
    }
   
    /**
     * Returns the instant in UTC time scale formatted as 
     * YYYY-DDDTHHhMMmSSsSSS
     * so that is leads to an MS Windows compatible filename
     * @param instant
     * @return 
     */
    public static String toWinCompatibleDateTime(long instant) {
        return toWinCompatibleDateTime(getAbsoluteDate(instant));
    }

    public static String toCombinedFormat(long instant) {
        return toCombinedFormat(getAbsoluteDate(instant));
    }

    /**
     * we assume coarseTime to be always positive (corresponding to uint32_t in
     * C)
     * @param coarseTime number of seconds from GPS epoch
     * @param fineTime number of 1/256 seconds
     * @return
     */
    public static long fromGpsCcsds(int coarseTime, byte fineTime) {
        long c = ((long) coarseTime) & 0xFFFFFFFFL;
        return GPS_YAMCS_EPOCH_DELTA + c * 1000 + 1000 * (0xFF & fineTime) / 256;
    }

    /**
     * Conversion from instant to GPS time.
     * @param instant yamcs time
     * @return GPS time
     */
    public static GpsCcsdsTime getGpsTime(final long instant) {
        GpsCcsdsTime gpsTime = new GpsCcsdsTime();
        long shiftedMillis = instant - GPS_YAMCS_EPOCH_DELTA;
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
        return getGpsTime(TimeEncoding.currentInstant());
    }
    
    /**
     * Conversion from standard *nix time in milliseconds to GPS time.
     * @param milliseconds Unix time in milliseconds, input parameter
     * @return GPS time
     */
    public static long getGpsTimeMillisec(final long instant) {
        return instant - GPS_YAMCS_EPOCH_DELTA;
    }
    
    public static long fromGpsYearSecMillis(int year, int secOfYear, int millis) {
        AbsoluteDate ad = new AbsoluteDate(year, 1, 1, 0, 0, 0, TimeScalesFactory.getGPS());
        return fromAbsoluteDate(ad.shiftedBy(secOfYear + millis / 1000.0));
    }

    public static DateTimeComponents getComponents(long instant) {
        AbsoluteDate ad = getAbsoluteDate(instant);
        return ad.getComponents(hmiScale);
    }

    public static TimeScale getHmiTimeScale() {
        return hmiScale;
    }

    public static long fromAbsoluteDate(AbsoluteDate ad)  {
    //	System.out.println("back duration from yamcs: "+ad.durationFrom(YAMCS_EPOCH));
        return Math.round((ad.durationFrom(YAMCS_EPOCH) * 1000));
    }

    public static long parse(String s) {
        return fromAbsoluteDate(new AbsoluteDate(s, hmiScale));
    }

    

    public static long getInstantFromUnix(long milliseconds) {
        double apparentOffset = milliseconds/1000.0d-2*365*24*3600;
        AbsoluteDate ad = new AbsoluteDate(UTC1972, apparentOffset, utc);
        return fromAbsoluteDate(ad);
    }
    
    public static long getInstantFromUnix(long seconds, int microseconds) {
        double apparentOffset = seconds-2*365*24*3600 + microseconds * 1.0e-6;
        AbsoluteDate ad = new AbsoluteDate(UTC1972, apparentOffset, utc);
        return fromAbsoluteDate(ad);
    }

    /**
     * returns unix time in milliseconds
     * @param instant
     * @return
     */
    public static long getUnixFromInstant(long instant) {
        AbsoluteDate ad=TimeEncoding.getAbsoluteDate(instant);
        double apparentOffset=ad.offsetFrom(UTC1972, utc)+2*365*24*3600;
        return Math.round(apparentOffset*1000);
    }

    public static long getInstantFromCal(Calendar cal) {
    	if(cal==null) return TimeEncoding.INVALID_INSTANT;
    	AbsoluteDate ad = new AbsoluteDate(cal.get(Calendar.YEAR), 1 + cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND)+cal.get(Calendar.MILLISECOND)/1000d, TimeEncoding.getHmiTimeScale());
        return TimeEncoding.fromAbsoluteDate(ad);
    }
    public static Calendar getCalFromInstant(long instant) {
    	if(instant==TimeEncoding.INVALID_INSTANT) return null;
    	
        AbsoluteDate ad=TimeEncoding.getAbsoluteDate(instant);
        Calendar cal=Calendar.getInstance();
        DateTimeComponents dtc=ad.getComponents(utc);
        double seconds=dtc.getTime().getSecond();
        cal.set(dtc.getDate().getYear(), dtc.getDate().getMonth()-1, dtc.getDate().getDay(), dtc.getTime().getHour(), dtc.getTime().getMinute(), (int)Math.round(seconds));
        
        cal.set(Calendar.MILLISECOND, Math.round(((int)(Math.round(seconds*1000)))%1000));
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

    public static UTCScale getUtcScale() {
        return utc;
    }

    /**
     * 
     * @param gpstime number of millisec from GPS epoch
     * @return
     */
    public static long fromGpsMillisec(long gpstime) {
        return gpstime + GPS_YAMCS_EPOCH_DELTA;
    }

}
