package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    public void openDb() throws Exception {
        Path dbroot = Path.of(YarchDatabase.getDataDir(), instance);
        FileUtils.deleteRecursivelyIfExists(dbroot);
        FileUtils.deleteRecursivelyIfExists(Path.of(dbroot + ".rdb"));
        FileUtils.deleteRecursivelyIfExists(Path.of(dbroot + ".tbs"));
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        if (rse.getTablespace(instance) != null) {
            rse.dropTablespace(instance);
        }
        rse.createTablespace(instance);

        parchive = new ParameterArchive();

        YConfiguration config = parchive.getSpec().validate(ParameterArchiveTest.backFillerDisabledConfig());
        parchive.init(instance, "test", config);
        pidDb = parchive.getParameterIdDb();
        ParameterGroupIdDb pgidMap = parchive.getParameterGroupIdDb();
        assertNotNull(pidDb);
        assertNotNull(pgidMap);
        filler = new MyFiller(parchive);
    }

    @BeforeAll
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

    @AfterEach
    public void closeDb() throws Exception {
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        rse.dropTablespace(instance);
    }

    @Test
    public void testArray() throws Exception {
        ParameterValue pv1 = getArrayPv(p1, 3, 1000);
        filler.addParameter(pv1).flush();
        ParameterId[] pids = pidDb.get("/test/p1[0]");
        assertEquals(1, pids.length);
        assertEquals(Type.FLOAT, pids[0].getEngType());
    }

    @Test
    public void testEmptyArray() throws Exception {
        ParameterValue pv1 = getArrayPv(p1, 0, 1000);
        filler.addParameter(pv1).flush();
        ParameterId[] pids = pidDb.get("/test/p1[0]");
        assertNull(pids);
    }

    private ParameterValue getArrayPv(Parameter p, int n, long t) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(t);
        pv.setAcquisitionTime(t);

        ArrayValue av = new ArrayValue(new int[] { n }, Type.FLOAT);
        for (int i = 0; i < n; i++) {
            av.setElementValue(i, ValueUtility.getFloatValue(i));
        }
        pv.setEngValue(av);
        pv.setRawValue(av);

        return pv;
    }

    static class MyFiller extends BackFillerTask {
        List<ParameterValue> pvlist = new ArrayList<>();

        public MyFiller(ParameterArchive parameterArchive) {
            super(parameterArchive);
        }

        public MyFiller addParameter(ParameterValue pv) {
            pvlist.add(pv);
            return this;
        }

        @Override
        public void flush() {
            processParameters(pvlist);
            super.flush();
        }
    }
}
