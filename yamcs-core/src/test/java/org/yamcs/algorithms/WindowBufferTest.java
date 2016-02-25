package org.yamcs.algorithms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Parameter;

public class WindowBufferTest {
    
    @Test
    public void test() {
        WindowBuffer window = new WindowBuffer(5);
        assertBufferEquals(window, null, null, null, null, null);
        
        window.update(toPval(50, "a1"));
        window.update(toPval(60, "a2"));
        assertBufferEquals(window, null, null, null, "a1", "a2");
        
        window.update(toPval(55, "a11"));
        assertBufferEquals(window, null, null, "a1", "a11", "a2");
        
        window.update(toPval(10, "n"));
        assertBufferEquals(window, null, "n", "a1", "a11", "a2");
        
        window.update(toPval(5, "n"));
        assertBufferEquals(window, "n", "n", "a1", "a11", "a2");
        
        window.update(toPval(15, "n")); // older than a1
        assertBufferEquals(window, "n", "n", "a1", "a11", "a2");
        
        window.update(toPval(60, "n")); // ignored, already have a param at 60
        assertBufferEquals(window, "n", "n", "a1", "a11", "a2");
    }
    
    private static boolean assertBufferEquals(WindowBuffer window, String... elements) {
        for(int i=0;i<window.getSize();i++) {
            ParameterValue pval=window.getHistoricValue(-window.getSize()+1+i);
            assertEquals("Incorrect element at position "+i, elements[i], 
                    (pval!=null)?pval.getEngValue().getStringValue():null);
        }
        return true;
    }
    
    private static ParameterValue toPval(long generationTime, String value) {
        Parameter def=new Parameter("something");
        ParameterValue pval = new ParameterValue(def);
        pval.setGenerationTime(generationTime);
        pval.setStringValue(value);
        return pval;
    }
}
