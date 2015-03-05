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
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValueWithId;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.RefMdbTmService;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.TcTmService;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Just a small sanity check to verify python/jython still works.
 * Uses algorithms in the spreadsheet that are interpreted the same in javascript and python
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
            c=ChannelFactory.create("refmdb-py", "AlgorithmManagerPyTest", "refmdb-py", new RefMdbTmService(tmGenerator), "refmdb-py");
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
    public void testFloats() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAddition").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(2.1672918, params.get(0).getParameterValue().getEngValue().getFloatValue(), 0.001);
    }
    
    @Test
    public void testSignedIntegers() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoNegativeOutcome1").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoNegativeOutcome2").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoNegativeOutcome3").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoNegativeOutcome4").build()
        ), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT18(2,-2);
        assertEquals(4, params.size());
        assertEquals(2, params.get(0).getParameterValue().getEngValue().getSint32Value());
        assertEquals(-2, params.get(1).getParameterValue().getEngValue().getSint32Value());
        assertEquals(-2, params.get(2).getParameterValue().getEngValue().getSint32Value());
        assertEquals(2, params.get(3).getParameterValue().getEngValue().getSint32Value());
    }

    @Test
    public void testExternalLibrary() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatDivision").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getFloatValue()*3, 0.001);
    }
}
