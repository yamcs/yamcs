package org.yamcs.parameter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;
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
	SoftwareParameterManager spm = new SoftwareParameterManager("test");
	XtceDb xtceDb = XtceDbFactory.createInstance("refmdb");
	MyParamConsumer consumer = new MyParamConsumer();
	
	spm.init(xtceDb);
	spm.setParameterListener(consumer);
	
	assertFalse(spm.canProvide(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara11_2").build()));
	
	Parameter p1 = spm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1").build());
	Parameter p2 = spm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara2").build());
	assertNotNull(p1);
	assertNotNull(p2);
	
	spm.startProviding(p1);
	NamedObjectId p1id = NamedObjectId.newBuilder().setName(p1.getQualifiedName()).build();
	NamedObjectId p2id = NamedObjectId.newBuilder().setName(p2.getQualifiedName()).build();
	
	Value p1v = ValueHelper.newValue(3);
	Value p2v = ValueHelper.newValue(2.72);
	
	org.yamcs.protobuf.Pvalue.ParameterValue pv1 = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder().setId(p1id).setEngValue(p1v).build();
	org.yamcs.protobuf.Pvalue.ParameterValue pv2 = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder().setId(p2id).setEngValue(p2v).build();
	
	List<org.yamcs.protobuf.Pvalue.ParameterValue> pvList = new ArrayList<org.yamcs.protobuf.Pvalue.ParameterValue>();
	pvList.add(pv1);
	pvList.add(pv2);
	
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

    class MyParamConsumer implements ParameterRequestManager {
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
