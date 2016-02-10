package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import org.junit.Test;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class StringValueSegmentTest {
  
    @Test
    public void testStringVsEnum() throws DecodingException {
        StringValueSegment svs = new StringValueSegment(true);
        for(int i=0;i<1000; i++) {
            svs.addValue(ValueUtility.getStringValue("random "+i+" value"));
        }
        svs = svs.consolidate();
        assertTrue(svs.rawSize < svs.enumRawSize);
        
        
        svs = new StringValueSegment(true);
        for(int i=0;i<1000; i++) {
            svs.addValue(ValueUtility.getStringValue("not so random "+(i%10)+" value"));
        }
        svs = svs.consolidate();
        assertTrue(svs.rawSize > svs.enumRawSize);
    }    
    
}
