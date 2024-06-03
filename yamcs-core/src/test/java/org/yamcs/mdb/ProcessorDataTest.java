package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;

public class ProcessorDataTest {

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
    }

    @Test
    public void testInitialValue() {
        Mdb mdb = MdbFactory.createInstanceByConfig("refmdb");

        ProcessorData pdata = new ProcessorData("test", mdb, new ProcessorConfig());
        LastValueCache lvc = pdata.getLastValueCache();

        ParameterValue pv = lvc.getValue(mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue1"));
        assertEquals(3.14, pv.getEngValue().getFloatValue(), 1e-5);

        pv = lvc.getValue(mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue2"));
        assertEquals(42, pv.getEngValue().getUint32Value());

        pv = lvc.getValue(mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue3"));
        assertEquals("string4 initial value", pv.getEngValue().getStringValue());

        pv = lvc.getValue(mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue4"));
        AggregateValue av = (AggregateValue) pv.getEngValue();
        assertEquals(42, av.getMemberValue("member1").getUint32Value());
        assertEquals(2.72, av.getMemberValue("member2").getFloatValue(), 1e-5);

        pv = lvc.getValue(mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue5"));
        ArrayValue arrv = (ArrayValue) pv.getEngValue();
        assertEquals(4, arrv.flatLength());
        assertEquals(3.3, arrv.getElementValue(2).getFloatValue(), 1e-5);

        pv = lvc.getValue(mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue6"));
        av = (AggregateValue) pv.getEngValue();
        assertEquals(1, av.getMemberValue("member1").getUint32Value());

        pv = lvc.getValue(mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue7"));
        arrv = (ArrayValue) pv.getEngValue();
        assertEquals(1, arrv.flatLength());
        assertEquals(-10.12, arrv.getElementValue(0).getFloatValue(), 1e-5);
    }
}
