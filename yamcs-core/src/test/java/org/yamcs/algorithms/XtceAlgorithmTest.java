package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

public class XtceAlgorithmTest {
    static String instance = "BogusSAT";
    static XtceDb db;
    private static Processor proc;
    private static ParameterRequestManager prm;
    private static ParameterProcessorManager ppm;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest(instance);
        EventProducerFactory.setMockup(false);
        MdbFactory.reset();
        AlgorithmManager am = new AlgorithmManager();
        proc = ProcessorFactory.create(instance, "XtceAlgorithmTest", new MyParaProvider(), am);
        ppm = proc.getParameterProcessorManager();
        prm = ppm.getParameterRequestManager();

        db = proc.getXtceDb();
    }

    @Test
    public void test1() throws Exception {
        Parameter bv = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_Voltage");
        Parameter bsoc = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_State_Of_Charge");
        final ArrayList<ParameterValue> params = new ArrayList<>();

        prm.addRequest(bsoc, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        ParameterValue pv = new ParameterValue(bv);
        pv.setEngValue(ValueUtility.getFloatValue(12.6f));
        ppm.process(ProcessingData.createForTestTm(pv));
        assertEquals(1, params.size());
        pv = params.get(0);
        assertEquals(1.0d, pv.getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void test2() {
        Parameter bv = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_Voltage");
        Parameter bscc = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_State_Of_Charge_Custom");
        final ArrayList<ParameterValue> params = new ArrayList<>();

        prm.addRequest(bscc, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        ParameterValue pv = new ParameterValue(bv);
        pv.setEngValue(ValueUtility.getFloatValue(12.6f));
        ppm.process(ProcessingData.createForTestTm(pv));
        assertEquals(1, params.size());

        pv = params.get(0);
        assertEquals(0.6d, pv.getEngValue().getFloatValue(), 1e-5);
    }
}
