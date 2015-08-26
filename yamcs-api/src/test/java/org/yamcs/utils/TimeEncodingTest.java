package org.yamcs.utils;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.utils.GpsCcsdsTime;
import org.yamcs.utils.TimeEncoding;



public class TimeEncodingTest {
    static final long j1972=(2L*365*24*3600+10)*1000+123;
    @BeforeClass
    public static void setUpBeforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    public void testCurrentInstant() {
       
        TimeEncoding.getWallclockTime();
        long time=System.currentTimeMillis();
        long instant=TimeEncoding.getWallclockTime();
        long correction=Math.abs(instant-time)%1000;
        
        assertTrue(correction<3);
        time+=correction;
        
        String sinstant=TimeEncoding.toString(instant);
        
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String stime=sdf.format(new Date(time));
        assertEquals(sinstant, stime);
       
    }
    
    @Test
    public void testToStringLong() {
        assertEquals("1972-01-01T00:00:00.123", TimeEncoding.toString(j1972));
    }
    
    @Test
    public void testToOrdinalDateTimeLong() {
        assertEquals("1972-001T00:00:00.123", TimeEncoding.toOrdinalDateTime(j1972));
    }
    
    
    @Test
    public void testToCombinedFormatLong() {
        assertEquals("1972-01-01/001T00:00:00.123", TimeEncoding.toCombinedFormat(j1972));
    }
    
    @Test
    public void testFromGpsCcsds() {
        long instant=TimeEncoding.fromGpsCcsdsTime(0, (byte)128);
        assertEquals("1980-01-06T00:00:00.500", TimeEncoding.toString(instant));
    }
    
    @Test
    public void testFromGpsYearSecMillis() {
        long instant=TimeEncoding.fromGpsYearSecMillis(2010, 15, 200);
        assertEquals("2010-01-01T00:00:00.200", TimeEncoding.toString(instant));
    }

    @Test
    public void getInstantFromUnix2() throws ParseException {
        long instant=TimeEncoding.fromUnixTime(123);
        assertEquals("1970-01-01T00:00:00.123", TimeEncoding.toString(instant));
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        long inst1 = TimeEncoding.fromUnixTime(sdf.parse("2008-12-31T23:59:59.125").getTime());
        long inst2 = TimeEncoding.fromUnixTime(sdf.parse("2009-01-01T00:00:00.126").getTime());
        assertEquals("2008-12-31T23:59:59.125", TimeEncoding.toString(inst1));
        assertEquals("2009-01-01T00:00:00.126", TimeEncoding.toString(inst2));
        assertEquals(2001, (inst2-inst1));
    } 
    
    @Test
    public void getUnixFromInstant() throws ParseException {
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        long unix1 = sdf.parse("2008-12-31T23:59:59.125").getTime();
        long unix2 = sdf.parse("2009-01-01T00:00:00.126").getTime();

        long inst1 = TimeEncoding.fromUnixTime(unix1);
        long inst2 = TimeEncoding.fromUnixTime(unix2);

        assertEquals("2008-12-31T23:59:59.125", TimeEncoding.toString(inst1));
        assertEquals("2009-01-01T00:00:00.126", TimeEncoding.toString(inst2));
        assertEquals(1001, (unix2-unix1));
        assertEquals(2001, (inst2-inst1));
    } 
    
    
    
    
    @Test
    public void testGetInstantFromUnix() {
        long instant=TimeEncoding.fromUnixTime(1266539888, 20000);
        assertEquals("2010-02-19T00:38:08.020", TimeEncoding.toString(instant));
    }

   
    
    @Test
    public void testGetInstantFromCal() throws ParseException {
        Calendar cal=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        String utc="2010-01-01T00:00:00.000";
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d=sdf.parse(utc);
        cal.setTime(d);
        long instant=TimeEncoding.fromCalendar(cal);
        String s=TimeEncoding.toString(instant);
       
        assertEquals(utc,s);
        
        utc="2010-12-31T23:59:59.000";
        d=sdf.parse(utc);
        cal.setTime(d);
        System.out.println("sdf,format:"+sdf.format(d)+"\nd="+d+"\ncal="+cal.getTime()+"\nhod="+cal.get(Calendar.HOUR_OF_DAY));
        instant=TimeEncoding.fromCalendar(cal);
        s=TimeEncoding.toString(instant);
        System.out.println("s="+s);
        assertEquals(utc,s);
        
    }

    @Test
    public void testGetJavaGpsFromInstant() {
        String utc="2010-01-01T00:00:00";
        String javagps="2010-01-01T00:00:15.000";
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        long instant=TimeEncoding.parse(utc);
        long jt=TimeEncoding.getJavaGpsFromInstant(instant);
        String s=sdf.format(new Date(jt));
        assertEquals(javagps, s);
    }
    
    @Test
    public void testParse() {
        assertEquals(1230768032001L, TimeEncoding.parse("2008-12-31T23:59:59.001"));
        assertEquals(1230768033000L, TimeEncoding.parse("2008-12-31T23:59:60"));
        assertEquals(1230768034000L, TimeEncoding.parse("2009-01-01T00:00:00"));
        
        assertEquals(1230768034000L, TimeEncoding.parse("2009/001T00:00:00"));
        assertEquals(1230768033000L, TimeEncoding.parse("2008/366T23:59:60"));
    }
    
    @Test
    public void testToString() {
        assertEquals("2008-12-31T23:59:59.000", TimeEncoding.toString(1230768032000L));
        assertEquals("2008-12-31T23:59:60.000", TimeEncoding.toString(1230768033000L));
        assertEquals("2009-01-01T00:00:00.000", TimeEncoding.toString(1230768034000L));
    }
    
    @Test
    public void testGetGpsTime1() {
        final int startCoarseTime = 981456898;
        
        for (int coarseTime = startCoarseTime; coarseTime < startCoarseTime + 5; ++coarseTime) {
            for (short fineTime=0; fineTime < 256; ++fineTime)
            {
                GpsCcsdsTime time = TimeEncoding.toGpsTime( TimeEncoding.fromGpsCcsdsTime(coarseTime, (byte)fineTime) );
                
               // System.out.println("in coarse: " + coarseTime + "\tin fine: " + (fineTime&0xFF));
                //System.out.println("out coarse: " + time.coarseTime + "\tout fine: " + (time.fineTime&0xFF));
                
                assertEquals(time.coarseTime,  coarseTime);
                assertTrue( Math.abs( (time.fineTime&0xFF) - fineTime ) <= 1 );
            }
        }
    }
    
    @Test
    public void testGetGpsTime2() {
        long instant=1293841234004L;
        GpsCcsdsTime time = TimeEncoding.toGpsTime(instant);
        assertEquals(977876415, time.coarseTime);
        assertEquals(1, time.fineTime);
    }


}
