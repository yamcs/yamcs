package org.yamcs.yarch.rocksdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.PartitioningInfo;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.PartitioningInfo.PartitioningType;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ProtoTableDefinition;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndex;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TableColumnInfo;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TableColumnInfo.EnumValue;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * 
 * protobuf serializer and deserializer for table definitions
 * 
 */
class TableDefinitionSerializer {

    static ProtoTableDefinition toProtobuf(TableDefinition def, List<TableColumnDefinition> keyDef,
            List<TableColumnDefinition> valueDef) {
        ProtoTableDefinition.Builder infob = ProtoTableDefinition.newBuilder();

        infob.setCompressed(def.isCompressed());
        infob.setFormatVersion(def.getFormatVersion());
        infob.setStorageEngine(def.getStorageEngineName());
        if (def.hasHistogram()) {
            infob.addAllHistogramColumn(def.getHistogramColumns());
        }
        if (def.hasPartitioning()) {
            infob.setPartitioningInfo(toProtobuf(def.getPartitioningSpec()));
        }

        for (TableColumnDefinition cdef : keyDef) {
            infob.addKeyColumn(toProtobuf(cdef));
        }
        for (TableColumnDefinition cdef : valueDef) {
            infob.addValueColumn(toProtobuf(cdef));
        }

        List<String> scndIdx = def.getSecondaryIndex();
        if (scndIdx != null) {
            infob.addSecondaryIndex(SecondaryIndex.newBuilder().addAllColumnName(scndIdx).build());
        }
        if (def.getCfName() != null) {
            infob.setCfName(def.getCfName());
        }
        return infob.build();
    }

    private static PartitioningInfo toProtobuf(PartitioningSpec spec) {
        PartitioningInfo.Builder partb = PartitioningInfo.newBuilder();

        switch (spec.type) {
        case TIME:
            partb.setType(PartitioningType.TIME);
            break;
        case VALUE:
            partb.setType(PartitioningType.VALUE);
            break;
        case TIME_AND_VALUE:
            partb.setType(PartitioningType.TIME_AND_VALUE);
            break;
        case NONE:
            break;
        default:
            throw new IllegalStateException("Unexpected partitioning type " + spec.type);
        }
        if (spec.type == PartitioningSpec._type.TIME || spec.type == PartitioningSpec._type.TIME_AND_VALUE) {
            if (spec.timeColumn != null) {
                partb.setTimeColumn(spec.timeColumn);
                partb.setTimePartitionSchema(spec.getTimePartitioningSchema().getName());
            }
        }
        if (spec.type == PartitioningSpec._type.VALUE || spec.type == PartitioningSpec._type.TIME_AND_VALUE) {
            if (spec.valueColumn != null) {
                partb.setValueColumn(spec.valueColumn);
                partb.setValueColumnType(spec.getValueColumnType().toString());
            }
        }
        return partb.build();

    }

    private static TableColumnInfo toProtobuf(TableColumnDefinition cdef) {
        TableColumnInfo.Builder infob = TableColumnInfo.newBuilder();
        infob.setName(cdef.getName());
        infob.setType(cdef.getType().name());
        if (cdef.getType().hasEnums()) {
            BiMap<String, Short> enumValues = cdef.getEnumValues();
            if (enumValues != null) {
                List<EnumValue> enumValueList = new ArrayList<>();
                for (Entry<String, Short> entry : enumValues.entrySet()) {
                    EnumValue val = EnumValue.newBuilder().setValue(entry.getValue()).setLabel(entry.getKey()).build();
                    enumValueList.add(val);
                }
                Collections.sort(enumValueList, (v1, v2) -> Integer.compare(v1.getValue(), v2.getValue()));
                infob.addAllEnumValue(enumValueList);
            }
        }
        if (cdef.isAutoIncrement()) {
            infob.setAutoincrement(true);
        }
        return infob.build();
    }

    static TableColumnDefinition fromProtobuf(TableColumnInfo tci) {
        String name = tci.getName();
        DataType type = DataType.byName(tci.getType());
        TableColumnDefinition tcd = new TableColumnDefinition(name, type);

        if (type.hasEnums()) {
            BiMap<String, Short> m = HashBiMap.create();
            for (EnumValue val : tci.getEnumValueList()) {
                m.put(val.getLabel(), (short) val.getValue());
            }
            tcd.setEnumValues(m);
        }
        if (tci.hasAutoincrement()) {
            tcd.setAutoIncrement(tci.getAutoincrement());
        }
        return tcd;
    }

    public static TableDefinition fromProtobuf(ProtoTableDefinition protodef) {
        List<TableColumnDefinition> keyDef = new ArrayList<>();
        List<TableColumnDefinition> valueDef = new ArrayList<>();

        for (TableColumnInfo tci : protodef.getKeyColumnList()) {
            TableColumnDefinition cdef = fromProtobuf(tci);
            keyDef.add(cdef);
        }

        for (TableColumnInfo tci : protodef.getValueColumnList()) {
            TableColumnDefinition cdef = fromProtobuf(tci);
            valueDef.add(cdef);
        }
        TableDefinition tdef = new TableDefinition(protodef.getFormatVersion(), keyDef, valueDef);

        try {
            if (protodef.getHistogramColumnCount() > 0) {
                tdef.setHistogramColumns(new ArrayList<String>(protodef.getHistogramColumnList()));
            }
            if (protodef.hasPartitioningInfo()) {
                tdef.setPartitioningSpec(fromProtobuf(protodef.getPartitioningInfo()));
            } else {
                tdef.setPartitioningSpec(PartitioningSpec.noneSpec());
            }

            if (protodef.getSecondaryIndexCount() > 0) {
                SecondaryIndex sidx = protodef.getSecondaryIndex(0);
                tdef.setSecondaryIndex(new ArrayList<String>(sidx.getColumnNameList()));
            }
        } catch (StreamSqlException e) {
            throw new DatabaseCorruptionException(e);
        }
        tdef.setCompressed(protodef.getCompressed());
        tdef.setStorageEngineName(protodef.getStorageEngine());

        if (protodef.hasCfName()) {
            tdef.setCfName(protodef.getCfName());
        }

        return tdef;

    }

    private static PartitioningSpec fromProtobuf(PartitioningInfo pinfo) {
        switch (pinfo.getType()) {
        case TIME:
            return PartitioningSpec.timeSpec(pinfo.getTimeColumn(), pinfo.getTimePartitionSchema());
        case VALUE:
            return PartitioningSpec.valueSpec(pinfo.getValueColumn());
        case TIME_AND_VALUE:
            return PartitioningSpec.timeAndValueSpec(pinfo.getTimeColumn(), pinfo.getValueColumn(),
                    pinfo.getTimePartitionSchema());
        default:
            throw new IllegalStateException("Unexpected partitioning type " + pinfo.getType());
        }
    }

}
