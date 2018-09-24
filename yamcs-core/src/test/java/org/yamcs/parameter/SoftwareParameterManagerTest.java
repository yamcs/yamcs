package org.yamcs.parameter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class SoftwareParameterManagerTest {
    @BeforeClass
    static public void setupTime() {
	TimeEncoding.setUp();
	YConfiguration.setup();
	XtceDbFactory.reset();
    }

    
    @Test
    public void test() throws Exception {
        LocalParameterManager spm = new LocalParameterManager("test");
	XtceDb xtceDb = XtceDbFactory.createInstanceByConfig("refmdb");
	MyParamConsumer consumer = new MyParamConsumer();
	
	spm.init(xtceDb);
	spm.setParameterListener(consumer);
	
	assertFalse(spm.canProvide(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara11_2").build()));
	
	Parameter p1 = spm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1").build());
	Parameter p2 = spm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara2").build());
	assertNotNull(p1);
	assertNotNull(p2);
	
	spm.startProviding(p1);
	
	Value p1v = ValueUtility.getUint32Value(3);
	Value p2v = ValueUtility.getDoubleValue(2.72);
	
	ParameterValue pv1 = new ParameterValue(p1);   
	pv1.setEngineeringValue(p1v);
	ParameterValue pv2 = new ParameterValue(p2);
	pv2.setEngineeringValue(p2v);
	
	List<ParameterValue> pvList = Arrays.asList(pv1, pv2);
	
	spm.updateParameters(pvList);
	Collection<ParameterValue> pvs = consumer.received.poll(5,  TimeUnit.SECONDS);
	assertNotNull(pvs);
	
	assertEquals(1, pvs.size());
	ParameterValue pv = pvs.iterator().next();
	assertEquals(p1, pv.getParameter());
	
	spm.startProvidingAll();
	
	spm.updateParameters(pvList);
	pvs = consumer.received.poll(5,  TimeUnit.SECONDS);
	assertEquals(2, pvs.size());
	
	
	spm.stopProviding(p1);
	spm.updateParameters(pvList);
	pvs = consumer.received.poll(5,  TimeUnit.SECONDS);
	assertEquals(1, pvs.size());
	pv = pvs.iterator().next();
	assertEquals(p2, pv.getParameter());
	
	spm.stopProviding(p2);
	spm.updateParameters(pvList);
	pvs = consumer.received.poll(2,  TimeUnit.SECONDS);
	assertNull(pvs);
    }

    class MyParamConsumer implements ParameterListener {
	BlockingQueue<Collection<ParameterValue>> received = new LinkedBlockingQueue<Collection<ParameterValue>>();

	@Override
	public void update(Collection<ParameterValue> params) {
	    try {
		received.put(params);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }

	}
    }    
}
