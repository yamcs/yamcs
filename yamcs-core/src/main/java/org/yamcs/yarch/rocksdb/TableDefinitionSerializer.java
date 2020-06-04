package org.yamcs.yarch.rocksdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.PartitioningInfo;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.PartitioningInfo.PartitioningType;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.RdbTableDefinition;
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

    static RdbTableDefinition toProtobuf(TableDefinition def) {
        RdbTableDefinition.Builder infob = RdbTableDefinition.newBuilder();

        infob.setCompressed(def.isCompressed());
        infob.setFormatVersion(def.getFormatVersion());
        infob.setStorageEngine(def.getStorageEngineName());
        if (def.hasHistogram()) {
            infob.addAllHistogramColumn(def.getHistogramColumns());
        }
        if (def.hasPartitioning()) {
            infob.setPartitioningInfo(toProtobuf(def.getPartitioningSpec()));
        }

        for (ColumnDefinition cdef : def.getKeyDefinition().getColumnDefinitions()) {
            infob.addKeyColumn(toProtobuf(cdef, def));
        }
        for (ColumnDefinition cdef : def.getValueDefinition().getColumnDefinitions()) {
            infob.addValueColumn(toProtobuf(cdef, def));
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

    private static TableColumnInfo toProtobuf(ColumnDefinition cdef, TableDefinition tableDefinition) {
        TableColumnInfo.Builder infob = TableColumnInfo.newBuilder();
        infob.setName(cdef.getName());
        infob.setType(cdef.getType().name());
        if (tableDefinition != null && cdef.getType() == DataType.ENUM) {
            BiMap<String, Short> enumValues = tableDefinition.getEnumValues(cdef.getName());
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
        return infob.build();
    }

    static ColumnDefinition fromProtobuf(TableColumnInfo tci, Map<String, BiMap<String, Short>> enumValues) {
        String name = tci.getName();
        DataType type = DataType.byName(tci.getType());
        if (type == DataType.ENUM) {
            BiMap<String, Short> m = HashBiMap.create();
            for (EnumValue val : tci.getEnumValueList()) {
                m.put(val.getLabel(), (short) val.getValue());
            }
            enumValues.put(name, m);
        }

        return new ColumnDefinition(name, type);
    }

    public static TableDefinition fromProtobuf(RdbTableDefinition protodef) {
        TupleDefinition keyDef = new TupleDefinition();
        TupleDefinition valueDef = new TupleDefinition();

        Map<String, BiMap<String, Short>> enumValues = new HashMap<>();
        for (TableColumnInfo tci : protodef.getKeyColumnList()) {
            ColumnDefinition cdef = fromProtobuf(tci, enumValues);
            keyDef.addColumn(cdef);
        }

        for (TableColumnInfo tci : protodef.getValueColumnList()) {
            ColumnDefinition cdef = fromProtobuf(tci, enumValues);
            valueDef.addColumn(cdef);
        }

        TableDefinition tdef = new TableDefinition(keyDef, valueDef, enumValues);

        try {
            if (protodef.getHistogramColumnCount() > 0) {
                tdef.setHistogramColumns(new ArrayList<String>(protodef.getHistogramColumnList()));
            }
            if (protodef.hasPartitioningInfo()) {
                tdef.setPartitioningSpec(fromProtobuf(protodef.getPartitioningInfo()));
            } else {
                tdef.setPartitioningSpec(PartitioningSpec.noneSpec());
            }
        } catch (StreamSqlException e) {
            throw new DatabaseCorruptionException(e);
        }
        tdef.setCompressed(protodef.getCompressed());
        tdef.setCompressed(protodef.getCompressed());
        tdef.setStorageEngineName(protodef.getStorageEngine());

        return tdef;

    }

    private static PartitioningSpec fromProtobuf(PartitioningInfo pinfo) {
        switch (pinfo.getType()) {
        case TIME:
            return PartitioningSpec.timeSpec(pinfo.getTimeColumn());
        case VALUE:
            return PartitioningSpec.valueSpec(pinfo.getValueColumn());
        case TIME_AND_VALUE:
            return PartitioningSpec.timeAndValueSpec(pinfo.getTimeColumn(), pinfo.getValueColumn());
        default:
            throw new IllegalStateException("Unexpected partitioning type " + pinfo.getType());
        }
    }

}
