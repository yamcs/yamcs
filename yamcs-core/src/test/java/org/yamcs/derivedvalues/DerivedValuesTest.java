package org.yamcs.derivedvalues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.Channel;
import org.yamcs.ChannelException;
import org.yamcs.ChannelFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ParameterValue;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.derivedvalues.DerivedValue;
import org.yamcs.derivedvalues.DerivedValuesManager;
import org.yamcs.derivedvalues.DerivedValuesProvider;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;


public class DerivedValuesTest {

    static XtceDb xtceDb;
    static String instance = "dvtest";
    RefMdbPacketGenerator tmGenerator;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
	YConfiguration.setup(instance);
	ManagementService.setup(false,false);
	XtceDbFactory.reset();
	EventProducerFactory.setMockup(false);
	xtceDb = XtceDbFactory.getInstance(instance);
    }

    Channel createChannel(String channelName) throws ChannelException, ConfigurationException {
	tmGenerator=new RefMdbPacketGenerator();
	DerivedValuesManager dvm = new DerivedValuesManager("dvtest");
	List<ParameterProvider> provList = new ArrayList<ParameterProvider>();
	provList.add(dvm);
	Channel c=ChannelFactory.create(instance, channelName, "dvtest", 
		new SimpleTcTmService(tmGenerator, provList, null), "dvtest");

	return c;
    }
    @Test
    public void testFloatAdd() throws Exception {

	Parameter fp=xtceDb.getParameter("/REFMDB/SUBSYS1/FloatPara11_2");
	assertNotNull(fp);

	Channel c=  createChannel("testFloatAdd");
	ParameterRequestManager prm=c.getParameterRequestManager();
	List<Parameter> paraList=new ArrayList<Parameter>();
	paraList.add(prm.getParameter("/DV/test_float_add"));
	paraList.add(prm.getParameter("/REFMDB/SUBSYS1/FloatPara11_2"));
	final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();


	prm.addRequest(paraList, new ParameterConsumer() {
	    @Override
	    public void updateItems(int subscriptionId,   ArrayList<ParameterValue> items) {
		params.addAll(items);
	    }
	});

	c.start();

	long t0 = System.currentTimeMillis();
	int n = 10;
	for(int i = 0 ; i<n; i++)  {
	    tmGenerator.generate_PKT11();
	}
	long t1 = System.currentTimeMillis();
	//   s.tryAcquire(5, TimeUnit.SECONDS);

	assertEquals(2*n, params.size());
	ParameterValue p=params.get(0);
	assertEquals(0.1672918, p.getEngValue().getFloatValue(), 0.001);

	p=params.get(1);
	assertEquals(2.1672918, p.getEngValue().getFloatValue(), 0.001);

	c.quit();
    }

    @Test
    public void testJavascriptFloatAdd() throws Exception {
	XtceDb db=XtceDbFactory.getInstance(instance);
	Parameter fp=db.getParameter("/REFMDB/SUBSYS1/FloatPara11_2");
	assertNotNull(fp);

	Channel c=  createChannel("testJavascriptFloatAdd");
	ParameterRequestManager prm=c.getParameterRequestManager();
	List<Parameter> paraList=new ArrayList<Parameter>();
	paraList.add(prm.getParameter("/DV/test_float_add_js"));
	paraList.add(prm.getParameter("/REFMDB/SUBSYS1/FloatPara11_2"));

	final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();


	prm.addRequest(paraList, new ParameterConsumer() {
	    @Override
	    public void updateItems(int subscriptionId,   ArrayList<ParameterValue> items) {
		params.addAll(items);
	    }
	});

	c.start();
	tmGenerator.generate_PKT11();
	//   s.tryAcquire(5, TimeUnit.SECONDS);
	assertEquals(2, params.size());
	ParameterValue p=params.get(0);
	assertEquals(0.1672918, p.getEngValue().getFloatValue(), 0.001);

	p=params.get(1);
	assertEquals(2.1672918, p.getEngValue().getDoubleValue(), 0.001);

	c.quit();
    }

    @Ignore
    @Test
    //this can be used to see that the performance of javascript is much worse in some later versions of Java 6
    //OpenJDK 7 is very fast.
    public void testJavascriptPerformanceFloatAdd() throws Exception {
	XtceDb db=XtceDbFactory.getInstance(instance);
	Parameter fp=db.getParameter("/REFMDB/SUBSYS1/FloatPara11_2");
	assertNotNull(fp);
	Channel c=  createChannel("testJavascriptPerformanceFloatAdd");

	ParameterRequestManager prm=c.getParameterRequestManager();
	List<Parameter> paraList=new ArrayList<Parameter>();
	paraList.add(prm.getParameter("test_float_ypr_js"));
	paraList.add(prm.getParameter("/REFMDB/SUBSYS1/FloatPara11_2"));

	final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();


	prm.addRequest(paraList, new ParameterConsumer() {
	    @Override
	    public void updateItems(int subscriptionId,   ArrayList<ParameterValue> items) {
		params.addAll(items);
	    }
	});

	c.start();
	long t0 = System.currentTimeMillis();
	int n = 100000;
	for(int i = 0 ; i<n; i++)  {
	    tmGenerator.generate_PKT11();
	}
	long t1 = System.currentTimeMillis();
	//   s.tryAcquire(5, TimeUnit.SECONDS);
	assertEquals(2*n, params.size());
	ParameterValue p = params.get(0);
	assertEquals(0.1672918, p.getEngValue().getFloatValue(), 0.001);

	p=params.get(1);
	assertEquals(2.1672918, p.getEngValue().getDoubleValue(), 0.001);

	c.quit();
    }

    static public class MyDerivedValuesProvider implements DerivedValuesProvider {
	Collection<DerivedValue> dvalues=new ArrayList<DerivedValue>(1);

	public MyDerivedValuesProvider() {
	    FloatAddDv dv1=new FloatAddDv("test_float_add", new String[]{"/REFMDB/SUBSYS1/FloatPara11_2", "/REFMDB/SUBSYS1/FloatPara11_3"});
	    dv1.def.setQualifiedName("/DV/test_float_add");
	    dvalues.add(dv1);
	}
	@Override
	public Collection<DerivedValue> getDerivedValues() {
	    return dvalues;
	}
    }

    static Parameter[] getParams(String[] argnames) {
	Parameter[] params = new Parameter[argnames.length];
	for(int i =0; i<argnames.length; i++) {
	    params[i] = xtceDb.getParameter(argnames[i]);
	}
	return params;
    }


    static class FloatAddDv extends DerivedValue {
	public FloatAddDv(String name, String[] argnames) {
	    super(name, getParams(argnames));
	}


	@Override
	public void updateValue() {
	    setFloatValue(args[0].getEngValue().getFloatValue()+args[1].getEngValue().getFloatValue());
	}

    }
}
