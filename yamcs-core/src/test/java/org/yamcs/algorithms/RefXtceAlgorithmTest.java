package org.yamcs.algorithms;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterListener;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ContainerProcessingResult;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmExtractor;

import com.google.common.util.concurrent.AbstractService;

public class RefXtceAlgorithmTest {
    static String instance = "refxtce";
    private static XtceDb db;
    private static Processor proc;
    private static ParameterRequestManager prm;

    private static MyProcService mpp = new MyProcService();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest(instance);
        EventProducerFactory.setMockup(false);
        XtceDbFactory.reset();
        AlgorithmManager am = new AlgorithmManager();
        proc = ProcessorFactory.create(instance, "XtceAlgorithmTest", mpp, am);
        prm = proc.getParameterRequestManager();
        db = proc.getXtceDb();
    }

    @Test
    public void test1() {
        Parameter param3 = db.getParameter("/RefXtce/param3");
        Parameter param4 = db.getParameter("/RefXtce/param4");

        final ArrayList<ParameterValue> params = new ArrayList<>();

        prm.addRequest(param3, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        prm.addRequest(param4, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        ByteBuffer buf = ByteBuffer.allocate(6);
        buf.putFloat(0.2f);
        buf.putShort((short) 10);
        mpp.injectPacket(buf.array(), "/RefXtce/packet2");

        assertEquals(2, params.size());
        assertEquals(5.1, params.get(0).getEngValue().getFloatValue(), 1e-5);
        assertEquals(5.1, params.get(1).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void test2() {
        Parameter param6 = db.getParameter("/RefXtce/param6");
        final ArrayList<ParameterValue> params = new ArrayList<>();

        prm.addRequest(param6, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        mpp.injectPacket(new byte[6], "/RefXtce/packet2");

        assertEquals(1, params.size());
        assertEquals(3.14, params.get(0).getEngValue().getFloatValue(), 1e-5);
    }

    static class MyProcService extends AbstractService implements ParameterProvider {
        ParameterRequestManager prm;
        XtceTmExtractor extractor;
        XtceDb db;

        @Override
        public void init(Processor processor, YConfiguration config, Object spec) {
            this.prm = processor.getParameterRequestManager();
            prm.addParameterProvider(this);
            db = processor.getXtceDb();
            extractor = new XtceTmExtractor(db);
            extractor.provideAll();
        }

        public void injectPacket(byte[] array, String name) {
            ContainerProcessingResult cpr = extractor.processPacket(array, 0, 0, db.getSequenceContainer(name));
            prm.update(cpr.getParameterResult());
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
            notifyStopped();
        }
    }
}
