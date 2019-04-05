package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

public class ArrayAndAggregatesTest {
    String instance = "ParameterArchiveAggrTest";
    ParameterArchive parchive;
    ParameterIdDb pidDb;
    static MockupTimeService timeService;
    static Parameter p1, p2, p3, p4, p5;
    MyFiller filler;
    
    @Before
    public void openDb() throws Exception {
        String dbroot = YarchDatabase.getInstance(instance).getRoot();
        FileUtils.deleteRecursively(dbroot);
        FileUtils.deleteRecursively(dbroot + ".rdb");
        FileUtils.deleteRecursively(dbroot + ".tbs");
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        if (rse.getTablespace(instance) != null) {
            rse.dropTablespace(instance);
        }
        rse.createTablespace(instance);

      
        parchive = new ParameterArchive(instance, YConfiguration.emptyConfig());
        pidDb = parchive.getParameterIdDb();
        ParameterGroupIdDb pgidMap = parchive.getParameterGroupIdDb();
        assertNotNull(pidDb);
        assertNotNull(pgidMap);
        filler = new MyFiller(parchive);
    }
    
    
    @BeforeClass
    public static void beforeClass() {
        p1 = new Parameter("p1");
        p2 = new Parameter("p2");
        p3 = new Parameter("p3");
        p4 = new Parameter("p4");
        p5 = new Parameter("p5");
        p1.setQualifiedName("/test/p1");
        p2.setQualifiedName("/test/p2");
        p3.setQualifiedName("/test/p3");
        p4.setQualifiedName("/test/p4");
        p5.setQualifiedName("/test/p5");
        TimeEncoding.setUp();

        timeService = new MockupTimeService();
        YamcsServer.setMockupTimeService(timeService);
        // org.yamcs.LoggingUtils.enableLogging();
    }
    
    @After
    public void closeDb() throws Exception {
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        rse.dropTablespace(instance);
    }
    

    @Test
    public void testArray() throws Exception {
        ParameterValue pv1 = getArrayPv(p1, 1000);
        filler.addParameter(pv1).flush();
        ParameterId[] pids = pidDb.get("/test/p1[0]");
        assertEquals(1, pids.length);
        assertEquals(Type.FLOAT, pids[0].engType);
    }
    
    
    
    private ParameterValue getArrayPv(Parameter p, long t) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(t);
        pv.setAcquisitionTime(t);
        
        ArrayValue av = new ArrayValue(new int[] {3}, Type.FLOAT);
        av.setElementValue(0, ValueUtility.getFloatValue(0));
        av.setElementValue(1, ValueUtility.getFloatValue(1));
        av.setElementValue(2, ValueUtility.getFloatValue(2));
        
        pv.setEngValue(av);
        pv.setRawValue(av);
        
        return pv;
    }



    static class MyFiller extends  ArchiveFillerTask {
        List<ParameterValue> pvlist = new ArrayList<>();
        public MyFiller(ParameterArchive parameterArchive) {
            super(parameterArchive, ArchiveFillerTask.DEFAULT_MAX_SEGMENT_SIZE);
        }

        public MyFiller addParameter(ParameterValue pv) {
            pvlist.add(pv);
            return this;
        }
        
        public void flush() {
            processParameters(pvlist);
            super.flush();
        }
        
    }
}
