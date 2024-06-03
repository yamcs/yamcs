package org.yamcs.yarch.rocksdb;

import static org.yamcs.utils.ValueUtility.getUint64Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.UnitType;

/**
 * Makes system parameters under /yamcs/<instance-id>/rocksdb/
 * <p>
 * One parameter for each open RocksDB database, containing statistics about the database
 * 
 */
public class RocksdbSysParamProducer implements SystemParametersProducer {

    private AggregateParameterType rocksdbMemUsageAggrType;
    private Parameter rocksdbMemUsageParam;

    final Tablespace tablespace;

    public RocksdbSysParamProducer(Tablespace tablespace, SystemParametersService sysParamsService) {
        this.tablespace = tablespace;

        UnitType kbunit = new UnitType("KB");


        Member blockCacheMemoryUsageMember = new Member("blockCache",
                sysParamsService.getBasicType(Type.UINT64, kbunit));
        blockCacheMemoryUsageMember.setShortDescription("The amount of memory used by the block cache");

        Member indexMemoryUsageeMember = new Member("index",
                sysParamsService.getBasicType(Type.UINT64, kbunit));
        indexMemoryUsageeMember.setShortDescription("The amount of memory used by the indexes and bloom filters");

        Member memtableMemoryUsageMember = new Member("memtable",
                sysParamsService.getBasicType(Type.UINT64, kbunit));
        memtableMemoryUsageMember.setShortDescription("The amount of memory used by the memtables");

        Member pinnedBlocksMemoryUsageMember = new Member("pinnedBlocks",
                sysParamsService.getBasicType(Type.UINT64, kbunit));
        pinnedBlocksMemoryUsageMember.setShortDescription("The amount of memory used by the pinned iterators");

        rocksdbMemUsageAggrType = new AggregateParameterType.Builder().setName("MemoryUsage")
                .addMember(blockCacheMemoryUsageMember)
                .addMember(indexMemoryUsageeMember)
                .addMember(memtableMemoryUsageMember)
                .addMember(pinnedBlocksMemoryUsageMember)
                .build();

        rocksdbMemUsageParam = sysParamsService.createSystemParameter("rocksdb/memoryUsage",
                rocksdbMemUsageAggrType,
                "Memory usage for RocksDB databases associated to tablespace " + tablespace.getName());

    }

    @Override
    public Collection<ParameterValue> getSystemParameters(long gentime) {
        List<ParameterValue> pvlist = new ArrayList<>();
        var m = tablespace.getApproximateMemoryUsage();

        AggregateValue v = new AggregateValue(rocksdbMemUsageAggrType.getMemberNames());

        v.setMemberValue("blockCache", getUint64Value(m.blockCacheMemoryUsage / 1024));
        v.setMemberValue("index", getUint64Value(m.indexMemoryUsage / 1024));
        v.setMemberValue("memtable", getUint64Value(m.memtableMemoryUsage / 1024));
        v.setMemberValue("pinnedBlocks", getUint64Value(m.pinnedBlocksMemoryUsage / 1024));

        ParameterValue pv = new ParameterValue(rocksdbMemUsageParam);
        pv.setGenerationTime(gentime);
        pv.setAcquisitionTime(gentime);
        pv.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
        pv.setEngValue(v);

        pv.setExpireMillis((long) (1.9 * getFrequency() * 1000));
        pvlist.add(pv);
        return pvlist;
    }

    @Override
    public int getFrequency() {
        return 5;
    }
}
