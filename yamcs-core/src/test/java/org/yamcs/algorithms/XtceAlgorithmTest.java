package org.yamcs.algorithms;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterListener;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

public class XtceAlgorithmTest {
    static String instance = "BogusSAT";
    private static XtceDb db;
    private static Processor c;
    private static ParameterRequestManager prm;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup(instance);
        XtceDbFactory.reset();
        AlgorithmManager am = new AlgorithmManager(instance);
        c = ProcessorFactory.create(instance, "XtceAlgorithmTest", new MyParaProvider(), am);
        prm = c.getParameterRequestManager();
        db = c.getXtceDb();
    }

    @Test
    public void test1() {
        Parameter bv = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_Voltage");
        Parameter bsoc = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_State_Of_Charge");
        final ArrayList<ParameterValue> params = new ArrayList<ParameterValue>();

        prm.addRequest(bsoc, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);

            }
        });
        ParameterValue pv = new ParameterValue(bv);
        pv.setEngineeringValue(ValueUtility.getFloatValue(12.6f));
        prm.update(Arrays.asList(pv));
        assertEquals(1, params.size());

        pv = params.get(0);
        assertEquals(1.0d, pv.getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void test2() {
        Parameter bv = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_Voltage");
        Parameter bscc = db.getParameter("/BogusSAT/SC001/BusElectronics/Battery_State_Of_Charge_Custom");
        final ArrayList<ParameterValue> params = new ArrayList<ParameterValue>();

        prm.addRequest(bscc, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);

            }
        });
        ParameterValue pv = new ParameterValue(bv);
        pv.setEngineeringValue(ValueUtility.getFloatValue(12.6f));
        prm.update(Arrays.asList(pv));
        assertEquals(1, params.size());

        pv = params.get(0);
        assertEquals(0.6d, pv.getEngValue().getFloatValue(), 1e-5);
    }

    static class MyParaProvider extends AbstractService implements ParameterProvider {

        @Override
        public void init(Processor processor) {
            processor.getParameterRequestManager().addParameterProvider(this);
        }

        @Override
        public void setParameterListener(ParameterListener parameterListener) {
        }

        @Override
        public void startProviding(Parameter paramDef) {
        }

        @Override
        public void startProvidingAll() {

        }

        @Override
        public void stopProviding(Parameter paramDef) {
        }

        @Override
        public boolean canProvide(NamedObjectId paraId) {
            return true;
        }

        @Override
        public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
            return db.getParameter(paraId.getName());
        }

        @Override
        public boolean canProvide(Parameter param) {
            return true;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStarted();
        }

    }

}
