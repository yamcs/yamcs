package org.yamcs.tctm;


import static org.junit.Assert.assertEquals;

import java.time.Instant;

import org.junit.Test;
import org.yamcs.tctm.ccsdstime.CucTimeDecoder;
import org.yamcs.utils.StringConverter;

public class CucTimeDecoderTest {
    
    @Test
    public void test1() {
        CucTimeDecoder ctd = new CucTimeDecoder(46);
        //byte[] b = StringConverter.hexStringToArray("5B3F555E48B4");
        byte[] b = StringConverter.hexStringToArray("5B3F555E48B4");
        long t = ctd.decode(b, 0);
        assertEquals("2018-07-06T11:41:18.283Z", Instant.ofEpochMilli(t).toString());
    }
}
