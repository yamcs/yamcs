package org.yamcs.xtceproc;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.XtceDb;

public class ProcessorDataTest {

    @BeforeClass
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
    }

    @Test
    public void testInitialValue() {
        XtceDb xtcedb = XtceDbFactory.createInstanceByConfig("refmdb");

        ProcessorData pdata = new ProcessorData("test", "test", xtcedb, false);
        LastValueCache lvc = pdata.getLastValueCache();

        ParameterValue pv = lvc.getValue(xtcedb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue1"));
        assertEquals(3.14, pv.getEngValue().getFloatValue(), 1e-5);

        pv = lvc.getValue(xtcedb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue2"));
        assertEquals(42, pv.getEngValue().getUint32Value());

        pv = lvc.getValue(xtcedb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue3"));
        assertEquals("string4 initial value", pv.getEngValue().getStringValue());

        pv = lvc.getValue(xtcedb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue4"));
        AggregateValue av = (AggregateValue) pv.getEngValue();
        assertEquals(42, av.getMemberValue("member1").getUint32Value());
        assertEquals(2.72, av.getMemberValue("member2").getFloatValue(), 1e-5);

        pv = lvc.getValue(xtcedb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue5"));
        ArrayValue arrv = (ArrayValue) pv.getEngValue();
        assertEquals(4, arrv.flatLength());
        assertEquals(3.3, arrv.getElementValue(2).getFloatValue(), 1e-5);

        pv = lvc.getValue(xtcedb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue6"));
        av = (AggregateValue) pv.getEngValue();
        assertEquals(1, av.getMemberValue("member1").getUint32Value());

        pv = lvc.getValue(xtcedb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue7"));
        arrv = (ArrayValue) pv.getEngValue();
        assertEquals(1, arrv.flatLength());
        assertEquals(-10.12, arrv.getElementValue(0).getFloatValue(), 1e-5);

    }
}
