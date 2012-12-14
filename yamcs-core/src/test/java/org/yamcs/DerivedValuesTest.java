package org.yamcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.Channel;
import org.yamcs.ChannelFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.DerivedValue;
import org.yamcs.DerivedValuesProvider;
import org.yamcs.ParameterConsumer;
import org.yamcs.ParameterProvider;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValue;
import org.yamcs.ParameterValueWithId;
import org.yamcs.management.ManagementService;
import org.yamcs.tctm.DummyPpProvider;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TcUplinker;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

import static org.junit.Assert.*;

public class DerivedValuesTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("dvtest");
        ManagementService.setup(false,false);
        XtceDbFactory.reset();
    }
    
    @Test
    public void testFloatAdd() throws Exception {
        XtceDb db=XtceDbFactory.getInstance("dvtest");
        Parameter fp=db.getParameter("/REFMDB/SUBSYS1/FloatPara11_2");
        assertNotNull(fp);
        
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        Channel c=ChannelFactory.create("dvtest", "dvtest", "dvtest", "dvtest", new MyTcTmService(tmGenerator), "dvtest", null);
        ParameterRequestManager prm=c.getParameterRequestManager();
        List<NamedObjectId> paraList=new ArrayList<NamedObjectId>();
        paraList.add(NamedObjectId.newBuilder().setName("test_float_add").build());
        paraList.add(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara11_2").build());
        final Semaphore s=new Semaphore(0);
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        
        
        prm.addRequest(paraList, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId,   ArrayList<ParameterValueWithId> items) {
            	params.addAll(items);
            	s.release();
            }
        });
        
        c.start();
        tmGenerator.generate_PKT11();
        //   s.tryAcquire(5, TimeUnit.SECONDS);

        assertEquals(2, params.size());
        ParameterValue p=params.get(0).getParameterValue();
        assertEquals(0.1672918, p.getEngValue().getFloatValue(), 0.001);
        
        p=params.get(1).getParameterValue();
        assertEquals(2.1672918, p.getEngValue().getFloatValue(), 0.001);

        c.quit();
    }

    
    static class MyTcTmService extends AbstractService implements TcTmService {
        TmPacketProvider tm;
        ParameterProvider pp;
        
        public MyTcTmService(RefMdbPacketGenerator tmGenerator) throws ConfigurationException {
            this.tm=tmGenerator;
            pp=new DummyPpProvider("dvtest", "dvtest");
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
    
    static public class MyDerivedValuesProvider implements DerivedValuesProvider {
        Collection<DerivedValue> dvalues=new ArrayList<DerivedValue>(1);
        
        public MyDerivedValuesProvider() {
            FloatAddDv dv1=new FloatAddDv("test_float_add", new String[]{"/REFMDB/SUBSYS1/FloatPara11_2", "/REFMDB/SUBSYS1/FloatPara11_3"});
            dvalues.add(dv1);
        }
        @Override
        public Collection<DerivedValue> getDerivedValues() {
            return dvalues;
        }
    }
    
    static class FloatAddDv extends DerivedValue {
        public FloatAddDv(String name, String[] argnames) {
            super(name, argnames);
        }

        @Override
        public void updateValue() {
        	setFloatValue(args[0].getEngValue().getFloatValue()+args[1].getEngValue().getFloatValue());
        }
        
    }
}
