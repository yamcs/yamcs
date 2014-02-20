package org.yamcs.algorithms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.Channel;
import org.yamcs.ChannelException;
import org.yamcs.ChannelFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterConsumer;
import org.yamcs.ParameterProvider;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValueWithId;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.DummyPpProvider;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TcUplinker;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * Just a small sanity check to verify python/jython still works.
 */
public class AlgorithmManagerPyTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb-py");
        ManagementService.setup(false,false);
        XtceDbFactory.reset();
    }
    
    private XtceDb db;
    private Channel c;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;
    
    @Before
    public void beforeEachTest() throws ConfigurationException, ChannelException {
        EventProducerFactory.setMockup(true);
        
        db=XtceDbFactory.getInstance("refmdb-py");
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara11_2"));

        tmGenerator=new RefMdbPacketGenerator();
        System.out.println(System.currentTimeMillis()+":"+Thread.currentThread()+"----------- before creating chanel: ");
        try {
            c=ChannelFactory.create("refmdb-py", "AlgorithmManagerPyTest", "refmdb-py", "refmdb-py", new MyTcTmService(tmGenerator), "refmdb-py", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(System.currentTimeMillis()+":"+Thread.currentThread()+"----------- after creating chanel c:"+c);
        prm=c.getParameterRequestManager();
    }
    
    @After
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        System.out.println(System.currentTimeMillis()+":"+Thread.currentThread()+"----------- after eachtest c:"+c);
        c.quit();
    }

    @Test
    public void testExternalLibrary() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatDivision").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getDoubleValue()*3, 0.001);
    }
    
    static class MyTcTmService extends AbstractService implements TcTmService {
        TmPacketProvider tm;
        ParameterProvider pp;

        public MyTcTmService(RefMdbPacketGenerator tmGenerator) throws ConfigurationException {
            this.tm=tmGenerator;
            pp=new DummyPpProvider("refmdb-py", "refmdb-py");
        }

        @Override
        public TmPacketProvider getTmPacketProvider() {
            return tm;
        }

        @Override
        public TcUplinker getTcUplinker() {
            return null;
        }

        @Override
        public ParameterProvider getParameterProvider() {
            return pp;
        }

        @Override
        protected void doStart() {
            tm.start();
            notifyStarted();
        }

        @Override
        protected void doStop() {
            tm.stop();
            notifyStopped();
        }
    }
}
