package org.yamcs.utils;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.BeforeClass;
import org.junit.Test;
import org.orekit.errors.OrekitException;
import org.orekit.time.AbsoluteDate;
import org.yamcs.utils.GpsCcsdsTime;
import org.yamcs.utils.TimeEncoding;



public class TimeEncodingTest {
    static final long j1972=(2L*365*24*3600+10)*1000+123;
    @BeforeClass
    public static void setUpBeforeClass() throws OrekitException {
        TimeEncoding.setUp();
    }

    @Test
    public void testCurrentInstant() {
       
        TimeEncoding.currentInstant();
        long time=System.currentTimeMillis();
        long instant=TimeEncoding.currentInstant();
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
    public void testCurrentAbsoluteDate() {
        AbsoluteDate ad=TimeEncoding.currentAbsoluteDate();
        long instant1=TimeEncoding.currentInstant();
        long instant2=TimeEncoding.fromAbsoluteDate(ad);
        System.out.println("instant1="+instant1+" instant2="+instant2);
        assertTrue(Math.abs(instant1-instant2)<10);
    }

    @Test
    public void testGetAbsoluteDate() {
        AbsoluteDate ad=TimeEncoding.getAbsoluteDate(j1972);
        assertEquals("1972-01-01T00:00:00.123",ad.toString());
    }
    
    @Test
    public void testToCombinedFormatAbsoluteDate() {
        String s="2010-04-21T16:59:02.300";
        String sc="2010-04-21/111T16:59:02.300";
        AbsoluteDate ad=new AbsoluteDate(s, TimeEncoding.getUtcScale());
        assertEquals(sc, TimeEncoding.toCombinedFormat(ad));
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
        long instant=TimeEncoding.fromGpsCcsds(0, (byte)128);
        assertEquals("1980-01-06T00:00:00.500", TimeEncoding.toString(instant));
    }
    
    @Test
    public void testFromGpsYearSecMillis() {
        long instant=TimeEncoding.fromGpsYearSecMillis(2010, 15, 200);
        assertEquals("2010-01-01T00:00:00.200", TimeEncoding.toString(instant));
    }

    @Test
    public void getInstantFromUnix2() {
        long instant=TimeEncoding.getInstantFromUnix(123);
        assertEquals("1970-01-01T00:00:00.123", TimeEncoding.toString(instant));
    } 
    
    @Test
    public void testGetInstantFromUnix() {
        long instant=TimeEncoding.getInstantFromUnix(1266539888, 20000);
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
        long instant=TimeEncoding.getInstantFromCal(cal);
        String s=TimeEncoding.toString(instant);
       
        assertEquals(utc,s);
        
        utc="2010-12-31T23:59:59.000";
        d=sdf.parse(utc);
        cal.setTime(d);
        System.out.println("sdf,format:"+sdf.format(d)+"\nd="+d+"\ncal="+cal.getTime()+"\nhod="+cal.get(Calendar.HOUR_OF_DAY));
        instant=TimeEncoding.getInstantFromCal(cal);
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
        String utc="2009-12-24T09:21:00";
        long instant=TimeEncoding.parse(utc);
        assertEquals(1261646494000L, instant);
    }
    
    @Test
    public void testGetGpsTime1() {
        final int startCoarseTime = 981456898;
        
        for (int coarseTime = startCoarseTime; coarseTime < startCoarseTime + 5; ++coarseTime) {
            for (short fineTime=0; fineTime < 256; ++fineTime)
            {
                GpsCcsdsTime time = TimeEncoding.getGpsTime( TimeEncoding.fromGpsCcsds(coarseTime, (byte)fineTime) );
                
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
        GpsCcsdsTime time = TimeEncoding.getGpsTime(instant);
        assertEquals(977876415, time.coarseTime);
        assertEquals(1, time.fineTime);
    }


}
