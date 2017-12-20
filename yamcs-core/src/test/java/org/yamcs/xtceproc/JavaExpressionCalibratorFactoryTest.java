package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import org.junit.Test;
import org.yamcs.xtce.JavaExpressionCalibrator;

public class JavaExpressionCalibratorFactoryTest {
    @Test
    public void test1() {
        JavaExpressionCalibrator jec = new JavaExpressionCalibrator("v+3");
        CalibratorProc c = JavaExpressionCalibratorFactory.compile(jec);
        assertEquals(7, c.calibrate(4), 1e-5);
    }
    
    
    @Test(expected=IllegalArgumentException.class)
    public void test2() {
        JavaExpressionCalibrator jec = new JavaExpressionCalibrator("\"blabala\"");
        CalibratorProc c = JavaExpressionCalibratorFactory.compile(jec);
        assertEquals(7, c.calibrate(4), 1e-5);
    }
    
    @Test
    public void test3() {
        JavaExpressionCalibrator jec = new JavaExpressionCalibrator("v>0?v+5:v-5");
        CalibratorProc c = JavaExpressionCalibratorFactory.compile(jec);
        assertEquals(9, c.calibrate(4), 1e-5);
        assertEquals(-7, c.calibrate(-2), 1e-5);
    }
}
