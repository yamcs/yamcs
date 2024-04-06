package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.rocksdb.RocksDBException;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

public abstract class BaseParchiveTest {
    String instance;

    static MockupTimeService timeService;
    ParameterArchive parchive;
    ParameterIdDb pidMap;
    ParameterGroupIdDb pgidMap;

    public void openDb(String partitioningSchema) throws Exception {
        openDb(partitioningSchema, false, 0);
    }

    public void openDb(String partitioningSchema, boolean sparseGroups, double minOverlap) throws Exception {
        Path dbroot = Path.of(YarchDatabase.getDataDir(), instance);
        FileUtils.deleteRecursivelyIfExists(dbroot);
        FileUtils.deleteRecursivelyIfExists(Path.of(dbroot + ".rdb"));
        FileUtils.deleteRecursivelyIfExists(Path.of(dbroot + ".tbs"));

        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        if (rse.getTablespace(instance) != null) {
            rse.dropTablespace(instance);
        }
        rse.createTablespace(instance);
        Map<String, Object> conf = new HashMap<>();

        if (partitioningSchema != null) {
            conf.put("partitioningSchema", partitioningSchema);
        }
        Map<String, Object> bfc = new HashMap<>();
        bfc.put("enabled", Boolean.FALSE);
        conf.put("backFiller", bfc);
        conf.put("sparseGroups", sparseGroups);
        conf.put("minimumGroupOverlap", minOverlap);

        parchive = new ParameterArchive();
        YConfiguration config = parchive.getSpec().validate(YConfiguration.wrap(conf));
        parchive.init(instance, "test", config);
        pidMap = parchive.getParameterIdDb();
        pgidMap = parchive.getParameterGroupIdDb();
        assertNotNull(pidMap);
        assertNotNull(pgidMap);
    }

    @AfterEach
    public void closeDb() throws Exception {
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        rse.dropTablespace(instance);
    }

    List<ParameterValueArray> retrieveSingleParamSingleGroup(long start, long stop, int parameterId,
            int parameterGroupId, boolean ascending) throws Exception {
        return retrieveSingleParamSingleGroup(start, stop, parameterId, parameterGroupId, ascending, true, true, true);
    }

    List<ParameterValueArray> retrieveSingleParamSingleGroup(long start, long stop, int parameterId,
            int parameterGroupId, boolean ascending, boolean retrieveEngValues, boolean retrieveRawValues,
            boolean retriveParamStatus) throws Exception {
        // ascending request on empty data
        SingleValueConsumer c = new SingleValueConsumer();
        ParameterRequest spvr = new ParameterRequest(start, stop, ascending, retrieveEngValues, retrieveRawValues,
                retriveParamStatus);
        SingleParameterRetrieval spdr = new SingleParameterRetrieval(parchive, parameterId,
                new int[] { parameterGroupId }, spvr);
        spdr.retrieve(c);
        return c.list;
    }

    List<ParameterValueArray> retrieveSingleValueMultigroup(long start, long stop, int parameterId,
            int[] parameterGroupIds, boolean ascending, boolean retrieveEng, boolean retrieveRaw,
            boolean retrieveStatus) throws RocksDBException, DecodingException, IOException {
        ParameterRequest spvr = new ParameterRequest(start, stop, ascending, retrieveEng, retrieveRaw, retrieveStatus);

        SingleParameterRetrieval spdr = new SingleParameterRetrieval(parchive, parameterId,
                parameterGroupIds, spvr);
        SingleValueConsumer svc = new SingleValueConsumer();
        spdr.retrieve(svc);
        return svc.list;
    }

    List<ParameterValueArray> retrieveSingleValueMultigroup(long start, long stop, int parameterId,
            int[] parameterGroupIds, boolean ascending) throws RocksDBException, DecodingException, IOException {
        return retrieveSingleValueMultigroup(start, stop, parameterId, parameterGroupIds, ascending, true, true, true);
    }

    ParameterValue getParameterValue(Parameter p, long instant, String sv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngValue(v);
        return pv;
    }

    ParameterValue getArrayValue(Parameter p, long instant, String... values) {
        ParameterValue pv = new ParameterValue(p);
        ArrayValue engValue = new ArrayValue(new int[] { values.length }, Type.STRING);
        for (int i = 0; i < values.length; i++) {
            engValue.setElementValue(i, ValueUtility.getStringValue(values[i]));
        }
        pv.setGenerationTime(instant);
        pv.setEngValue(engValue);
        return pv;
    }

    ParameterValue getParameterValue(Parameter p, long instant, String sv, int rv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngValue(v);
        pv.setRawValue(ValueUtility.getUint32Value(rv));
        return pv;
    }

    ParameterValue getParameterValue(Parameter p, long instant, String sv, String rv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngValue(v);
        pv.setRawValue(ValueUtility.getStringValue(rv));
        return pv;
    }

    List<ParameterIdValueList> retrieveMultipleParameters(long start, long stop, int[] parameterIds,
            int[] parameterGroupIds, boolean ascending) throws Exception {
        return retrieveMultipleParameters(start, stop, parameterIds, parameterGroupIds, ascending, -1);
    }

    List<ParameterIdValueList> retrieveMultipleParameters(long start, long stop, int[] parameterIds,
            int[] parameterGroupIds, boolean ascending, int limit) throws Exception {
        ParameterId[] pids = Arrays.stream(parameterIds).mapToObj(pid -> pidMap.getParameterId(pid))
                .toArray(ParameterId[]::new);
        MultipleParameterRequest mpvr = new MultipleParameterRequest(start, stop,
                pids, parameterGroupIds, ascending, true, true, true);
        mpvr.setLimit(limit);

        MultiParameterRetrieval mpdr = new MultiParameterRetrieval(parchive, mpvr);
        MultiValueConsumer c = new MultiValueConsumer();
        mpdr.retrieve(c);
        return c.list;
    }

    class SingleValueConsumer implements Consumer<ParameterValueArray> {
        List<ParameterValueArray> list = new ArrayList<>();

        @Override
        public void accept(ParameterValueArray x) {
            // System.out.println("received: engValues: "+x.engValues+" rawValues: "+x.rawValues+" paramStatus:
            // "+Arrays.toString(x.paramStatus));
            list.add(x);
        }
    }

    class MultiValueConsumer implements Consumer<ParameterIdValueList> {
        List<ParameterIdValueList> list = new ArrayList<>();

        @Override
        public void accept(ParameterIdValueList x) {
            list.add(x);
        }
    }
}
